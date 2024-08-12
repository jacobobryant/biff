(ns com.biffweb.impl.xtdb
  (:require [better-cond.core :as b]
            [com.biffweb.impl.util :as util :refer [<<-]]
            [com.biffweb.impl.util.ns :as ns]
            [clojure.java.io :as io]
            [clojure.set :as set]
            [clojure.stacktrace :as st]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [clojure.walk :as walk]
            [xtdb.api :as xt]
            [xtdb.tx :as tx]
            [malli.error :as male]
            [malli.core :as malc])
  (:import [java.io Closeable]))

(defn verbose-sync [node-id node]
  (let [done (atom false)]
    (future
      (loop []
        (Thread/sleep 2000)
        (when-not @done
          (log/info node-id "indexed" (xt/latest-completed-tx node))
          (recur))))
    (xt/sync node)
    (reset! done true)))

(defn- tx-id-for [node t]
  (get-in (xt/db-basis (xt/db node {::xt/tx-time t}))
          [::xt/tx ::xt/tx-id]))

(defn indexer-args [node {::xt/keys [tx-id tx-ops]}]
  (let [prev-db (delay (xt/db node {::xt/tx {::xt/tx-id (dec tx-id)}}))]
    (vec ((fn step [tx-ops]
            (mapcat (fn [[op arg1 arg2]]
                      (case op
                        ::xt/put [#:biff.index{:op ::xt/put
                                               :doc arg1}]
                        ::xt/fn (step (::xt/tx-ops arg2))
                        ::xt/delete [#:biff.index{:op ::xt/delete
                                                  :doc (xt/entity @prev-db arg1)}]
                        nil))
                    tx-ops))
          tx-ops))))

(defn test-tx-log [node from to]
  (let [from-tx-id (tx-id-for node from)]
    (with-open [log (xt/open-tx-log node
                                    (dec from-tx-id)
                                    true)]
      (->> (iterator-seq log)
           (take-while #(< (compare (::xt/tx-time %) to) 0))
           (mapv (fn [tx]
                   (assoc tx :biff.index/args (indexer-args node tx))))))))

(defn run-indexer [indexer index-args get-doc]
  (reduce (fn [[index-tx id->doc] args]
            (let [get-doc* (fn [id]
                             (cond
                               (contains? id->doc id) (get id->doc id)
                               get-doc (get-doc id)))
                  output-tx (indexer (assoc args :biff.index/get-doc get-doc*))]
              [(into index-tx output-tx)
               (into id->doc
                     (map (fn [[op doc-or-id]]
                            (case op
                              ::xt/put [(:xt/id doc-or-id) doc-or-id]
                              ::xt/delete [doc-or-id nil])))
                     output-tx)]))
          [[] {}]
          index-args))

(defn indexer-results [indexer tx-log-with-args & {:keys [limit] :or {limit 10}}]
  (let [[results id->doc txes-processed]
        (loop [results []
               id->doc {}
               txes-processed 0
               [main-tx & remaining] tx-log-with-args]
          (if (or (nil? main-tx) (<= limit (count results)))
            [results id->doc txes-processed]
            (let [[index-tx id->doc] (run-indexer indexer (:biff.index/args main-tx) id->doc)]
              (recur (cond-> results
                       (not-empty index-tx) (conj {:main-tx main-tx
                                                   :index-tx index-tx}))
                     id->doc
                     (inc txes-processed)
                     remaining))))]
    {:results results
     :all-docs (filterv some? (vals id->doc))
     :txes-processed txes-processed}))

(defn start-index-node [{:biff.xtdb/keys [dir kv-store]} index-id]
  (let [db-dir (-> (str index-id)
                   (str/replace #"/" "_")
                   (str/replace #"[^a-zA-Z0-9-]+" ""))
        kv-store-fn (fn [basename]
                      {:kv-store {:xtdb/module (if (= kv-store :lmdb)
                                                 'xtdb.lmdb/->kv-store
                                                 'xtdb.rocksdb/->kv-store)
                                  :db-dir (io/file dir (str basename (when (= kv-store :lmdb)
                                                                       "-lmdb")))}})]
    (xt/start-node
     {:xtdb/index-store    (kv-store-fn (str "biff-indexes/" db-dir "/index"))
      :xtdb/document-store (kv-store-fn (str "biff-indexes/" db-dir "/docs"))
      :xtdb/tx-log         (kv-store-fn (str "biff-indexes/" db-dir "/tx-log"))})))

(defn prepare-index! [{:biff.xtdb/keys [node] :as ctx}
                      {:keys [id indexer version]}]
  (<<- (with-open [index-node (start-index-node ctx id)])
       (let [_ (com.biffweb.impl.xtdb/verbose-sync id index-node)
             index-db (xt/db index-node)
             index-meta (xt/entity index-db :biff.index/metadata)])
       (if (and (some? index-meta)
                (not= (:biff.index/version index-meta) version))
         ::bad-version)
       (with-open [log (xt/open-tx-log node (::xt/tx-id index-meta) true)])
       (reduce (fn [[index-tx id->doc get-doc committed-at-ms latest-tx]
                    input-tx]
                 (<<- (let [get-doc* (fn [id]
                                       (if (contains? id->doc id)
                                         (get id->doc id)
                                         (get-doc id)))
                            [tx new-id->doc] (run-indexer indexer
                                                          (indexer-args node input-tx)
                                                          get-doc*)
                            index-tx (into index-tx tx)
                            now-ms (inst-ms (java.util.Date.))
                            commit (or (<= 1000 (count index-tx))
                                       (< (* 1000 10) (- now-ms committed-at-ms))
                                       (= (::xt/tx-id input-tx) (::xt/tx-id latest-tx)))])
                      (if-not commit
                        [index-tx
                         (merge id->doc new-id->doc)
                         get-doc
                         committed-at-ms
                         latest-tx])
                      (let [index-tx (conj index-tx [::xt/put {:xt/id :biff.index/metadata
                                                               :biff.index/version version
                                                               ::xt/tx-id (::xt/tx-id input-tx)}])
                            _ (xt/await-tx index-node (xt/submit-tx index-node index-tx))
                            _ (log/info "Indexed" id "up to" (select-keys input-tx [::xt/tx-id ::xt/tx-time]))
                            index-db (xt/db index-node)])
                                   [[]
                                    {}
                                    (memoize #(xt/entity index-db %))
                                    now-ms
                                    (xt/latest-submitted-tx node)]))
                            [[]
                             {}
                             (memoize #(xt/entity index-db %))
                             (inst-ms (java.util.Date.))
                             (xt/latest-submitted-tx node)]
                            (iterator-seq log)))
  (log/info "Finished indexing" id))

(defn rollback [node tx-id]
  (let [txes (with-open [log (xt/open-tx-log node tx-id true)]
               (doall (iterator-seq log)))
        doc-ids (->> txes
                     (mapcat #(indexer-args node %))
                     (mapv (comp :xt/id :biff.index/doc))
                     distinct)
        db (xt/db node {::xt/tx {::xt/tx-id tx-id}})
        tx (mapv (fn [id]
                   (if-some [doc (xt/entity db id)]
                     [::xt/put doc]
                     [::xt/delete id]))
                 doc-ids)]
    (->> tx
         (partition-all 1000)
         (run! #(xt/submit-tx node %)))))

(defn ->index
  {:xtdb.system/deps {:xtdb/secondary-indices :xtdb/secondary-indices}
   :xtdb.system/before #{[:xtdb/tx-ingester]}}
  [{:keys [xtdb/secondary-indices
           biff/indexes
           biff.index/tx-metadata]
    main-node :biff.xtdb/node}]
  (doseq [[_ {indexed-main-tx-id ::xt/tx-id
              index-id :id
              :keys [node indexer version abort-on-error]}] indexes
          :let [aborted (atom false)]]
    (tx/register-index!
     secondary-indices
     (or indexed-main-tx-id -1)
     {:with-tx-ops? true}
     (fn [{main-tx-id ::xt/tx-id
           :keys [::xt/tx-ops
                  committing?]
           :as new-main-tx}]
       (when-not (and abort-on-error @aborted)
         (let [db (delay (do
                           (when-some [tx (xt/latest-submitted-tx node)]
                             (xt/await-tx node tx))
                           (xt/db node)))
               tx (when committing?
                    (try
                      (let [get-doc (memoize #(xt/entity @db %))
                            [tx _] (run-indexer indexer
                                                (indexer-args main-node new-main-tx)
                                                get-doc)]
                        ;; TODO get rid of this to speed things up?
                        (when (not-empty tx)
                          (xt/with-tx @db tx))
                        tx)
                      (catch Exception e
                        ;; TODO include message + ex-data on the exception?
                        (log/error e
                                   (str "Exception while indexing! You can debug by calling "
                                        "(biff/replay-indexer (get-context) " index-id " " main-tx-id ") from the REPL. "
                                        "After fixing the problem, you should re-index by incrementing the index version and "
                                        "refreshing/restarting your app.")
                                   (pr-str {:biff.index/id index-id
                                            :biff.index/version version
                                            ::xt/tx-id main-tx-id}))
                        (when abort-on-error
                          (reset! aborted true))
                        nil)))
               tx (cond->> tx
                    ;; Even if we don't have any new index data to store, still record ::xt/tx-id every once in a while
                    ;; so we don't have to iterate through a potentially large part of the transaction log on every
                    ;; startup.
                    (or (not-empty tx) (zero? (mod main-tx-id 1000)))
                    (concat [[::xt/put {:xt/id :biff.index/metadata
                                        :biff.index/version version
                                        ::xt/tx-id main-tx-id}]]))
               {index-tx-id ::xt/tx-id} (if (not-empty tx)
                                          (xt/submit-tx node tx)
                                          (xt/latest-submitted-tx node))]
           (swap! tx-metadata
                  (fn [txm]
                    (<<- (if (< main-tx-id (get txm :max-indexed-tx-at-startup -1))
                           txm)
                         (let [txm (assoc-in txm [:main-tx-id->index-id->index-tx-id main-tx-id index-id] index-tx-id)
                               next-tx-ids (get-in txm [:main-tx-id->index-id->index-tx-id main-tx-id])])
                         (if (< (count next-tx-ids) (count indexes))
                           txm)
                         (-> txm
                             (assoc :latest-consistent-tx-ids (assoc next-tx-ids :biff.xtdb/node main-tx-id))
                             (update :main-tx-id->index-id->index-tx-id dissoc main-tx-id))))))))))
  (reify Closeable
    (close [this]
      (doseq [[_ {:keys [node]}] indexes]
        (.close node)))))

(defn empty-db [node]
  (if (xt/latest-completed-tx node)
    (xt/db node {::xt/tx {::xt/tx-id -1}})
    (xt/db node)))

(defn index-snapshots [{:keys [biff.xtdb/node biff/indexes biff.index/tx-metadata]}]
  (if-some [node-id->tx-id (:latest-consistent-tx-ids @tx-metadata)]
    (let [main-tx {::xt/tx-id (node-id->tx-id :biff.xtdb/node)}]
      (xt/await-tx node main-tx)
      (-> indexes
          (update-vals (fn [{:keys [id node]}]
                         (xt/db node {::xt/tx {::xt/tx-id (node-id->tx-id id)}})))
          (assoc :biff/db (xt/db node {::xt/tx main-tx}))))
    (-> indexes
        (update-vals (comp empty-db :node))
        (assoc :biff/db (empty-db node)))))

(defn replay-indexer [{:keys [biff.xtdb/node biff/indexes]} index-id tx-id]
  (let [{:keys [indexer] index-node :node} (get indexes index-id)
        tx (first (iterator-seq (xt/open-tx-log node (dec tx-id) true)))
        ;; TODO page through entity-history (or do binary search?) if getting the whole thing eagerly turns out to be a
        ;; problem with large DBs.
        index-tx-id (->> (xt/entity-history (xt/db index-node) :biff.index/metadata :desc {:with-docs? true})
                         (drop-while #(<= tx-id (get-in % [::xt/doc ::xt/tx-id])))
                         first
                         ::xt/tx-id)
        index-db (if (some? index-tx-id)
                   (xt/db index-node {::xt/tx {::xt/tx-id index-tx-id}})
                   (empty-db index-node))
        ret (try
              (let [[tx _] (run-indexer indexer
                                        (indexer-args index-node tx)
                                        (memoize #(xt/entity index-db %)))]
                (try
                  (xt/with-tx index-db tx)
                  {:output-tx tx}
                  (catch Exception e
                    {:output-tx tx :indexer-exception e})))
              (catch Exception e
                {:with-tx-exception e}))]
    (merge {:index-basis (xt/db-basis index-db) :input-tx tx} ret)))

(defn save-tx-fns! [node tx-fns]
  (let [db (xt/db node)]
    (when-some [tx (not-empty
                    (vec
                     (for [[k f] tx-fns
                           :let [new-doc {:xt/id k
                                          :xt/fn f}
                                 old-doc (xt/entity db k)]
                           :when (not= new-doc old-doc)]
                       [::xt/put new-doc])))]
      (xt/submit-tx node tx))))

(defn use-xt
  [{:keys [biff/secret
           biff/modules
           biff/plugins
           biff/features]
    :biff.xtdb/keys [topology dir kv-store opts tx-fns]
    index-topology :biff.index/topology
    :or {kv-store :rocksdb
         index-topology :standalone}
    :as ctx}]
  (let [kv-store-fn (fn [basename]
                      {:kv-store {:xtdb/module (if (= kv-store :lmdb)
                                                 'xtdb.lmdb/->kv-store
                                                 'xtdb.rocksdb/->kv-store)
                                  :db-dir (io/file dir (str basename (when (= kv-store :lmdb)
                                                                       "-lmdb")))}})
        jdbc-spec (into (ns/select-ns-as ctx 'biff.xtdb.jdbc nil)
                        (keep (fn [k]
                                (when-let [value (and secret (secret (keyword "biff.xtdb.jdbc"
                                                                              (name k))))]
                                  [k value])))
                        [:password :jdbcUrl])
        pool-opts (ns/select-ns-as ctx 'biff.xtdb.jdbc-pool nil)
        modules (util/ctx->modules ctx)
        indexes (for [{:keys [id indexer] :as index} (mapcat :indexes modules)]
                  (assoc index
                         :db-dir (-> (str id)
                                     (str/replace #"/" "_")
                                     (str/replace #"[^a-zA-Z0-9-]+" ""))))
        _ (doseq [[db-dir indexes] (group-by :db-dir indexes)
                  :when (< 1 (count indexes))]
            (throw (ex-info "Invalid indexes: multiple index IDs map to the same db-dir"
                            {:ids (mapv :id indexes)
                             :db-dir db-dir})))
        start-index-node (fn [{:keys [db-dir]}]
                           (xt/start-node
                            (case index-topology
                              :memory  {}
                              :standalone {:xtdb/index-store    (kv-store-fn (str "biff-indexes/" db-dir "/index"))
                                           :xtdb/document-store (kv-store-fn (str "biff-indexes/" db-dir "/docs"))
                                           :xtdb/tx-log         (kv-store-fn (str "biff-indexes/" db-dir "/tx-log"))})))
        indexes (into {} (for [{:keys [id] :as index} indexes]
                           [id (assoc index :node (start-index-node index))]))
        indexes (update-vals indexes
                             (fn [{:keys [id node version db-dir] :as index}]
                               (verbose-sync id node)
                               (if-not (and (= index-topology :standalone)
                                            (some-> (xt/entity (xt/db node) :biff.index/metadata)
                                                    :biff.index/version
                                                    (not= version)))
                                 index
                                 (do
                                   (.close node)
                                   (run! io/delete-file (reverse (file-seq (io/file (str dir "/biff-indexes/" db-dir)))))
                                   (assoc index :node (start-index-node index))))))
        indexes (update-vals indexes (fn [{:keys [node] :as index}]
                                       (merge index (xt/entity (xt/db node) :biff.index/metadata))))
        max-indexed-tx-id (some->> (keep ::xt/tx-id indexes)
                                   not-empty
                                   (apply max))
        tx-metadata (atom {:max-indexed-tx-id-at-startup
                           max-indexed-tx-id

                           :latest-consistent-tx-ids
                           (when (= 1 (count (set (mapv ::xt/id (vals indexes)))))
                             (assoc (update-vals indexes (comp ::xt/id xt/latest-completed-tx :node))
                                    :biff.xtdb/node (::xt/id (first (vals indexes)))))

                           :main-tx-id->index-id->index-tx-id
                           (some->> (vals indexes)
                                    (filterv ::xt/tx-id)
                                    (sort-by ::xt/tx-id >)
                                    (partition-by ::xt/tx-id)
                                    first
                                    (mapv (juxt :id (comp ::xt/tx-id xt/latest-completed-tx :node)))
                                    (into {})
                                    (hash-map max-indexed-tx-id))})
        ctx (assoc ctx :biff/indexes indexes :biff.index/tx-metadata tx-metadata)
        node (xt/start-node
              (merge (case topology
                       :memory
                       {}

                       :standalone
                       {:xtdb/index-store    (kv-store-fn "index")
                        :xtdb/document-store (kv-store-fn "docs")
                        :xtdb/tx-log         (kv-store-fn "tx-log")}

                       :jdbc
                       {:xtdb/index-store (kv-store-fn "index")
                        :xtdb.jdbc/connection-pool {:dialect {:xtdb/module
                                                              'xtdb.jdbc.psql/->dialect}
                                                    :pool-opts pool-opts
                                                    :db-spec jdbc-spec}
                        :xtdb/tx-log {:xtdb/module 'xtdb.jdbc/->tx-log
                                      :connection-pool :xtdb.jdbc/connection-pool}
                        :xtdb/document-store {:xtdb/module 'xtdb.jdbc/->document-store
                                              :connection-pool :xtdb.jdbc/connection-pool}})
                     {::index ctx}
                     opts))]
    (verbose-sync :biff.xtdb/node node)
    (when (not-empty tx-fns)
      (save-tx-fns! node tx-fns))
    (-> ctx
        (assoc :biff.xtdb/node node)
        (update :biff/stop conj #(.close node)))))

(defn assoc-db [{:keys [biff.xtdb/node] :as ctx}]
  (cond-> ctx
    node (assoc :biff/db (xt/db node))))

(defn merge-context [{:keys [biff/merge-context-fn]
                      :or {merge-context-fn assoc-db}
                      :as ctx}]
  (merge-context-fn ctx))

(defn use-tx-listener [{:keys [biff/features
                               biff/plugins
                               biff/modules
                               biff.xtdb/on-tx
                               biff.xtdb/node]
                        :as ctx}]
  (if-not (or on-tx modules plugins features)
    ctx
    (let [on-tx (or on-tx
                    (fn [ctx tx]
                      (doseq [{:keys [on-tx]} @(or modules plugins features)
                              :when on-tx]
                        (util/catchall-verbose
                         (on-tx ctx tx)))))
          lock (Object.)
          listener (xt/listen
                    node
                    {::xt/event-type ::xt/indexed-tx}
                    (fn [{:keys [::xt/tx-id committed?]}]
                      (when committed?
                        (locking lock
                          (with-open [log (xt/open-tx-log node
                                                          (dec tx-id)
                                                          true)]
                            (let [tx (first (iterator-seq log))]
                              (try
                                (on-tx (merge-context ctx) tx)
                                (catch Exception e
                                  (log/error e "Exception during on-tx")))))))))]
      (update ctx :biff/stop conj #(.close listener)))))

(defn q [db query & args]
  (when-not (= (count (:in query))
               (count args))
    (throw (ex-info (str "Incorrect number of query arguments. Expected "
                         (count (:in query))
                         " but got "
                         (count args)
                         ".")
                    {})))
  (let [return-tuples (vector? (:find query))
        query (cond-> query
                (not return-tuples) (update :find vector))
        results (apply xt/q db query args)]
    (cond->> results
      (not return-tuples) (map first))))

(defn lazy-q
  [db query & args]
  (when-not (= (count (:in query))
               (dec (count args)))
    (throw (ex-info (str "Incorrect number of query arguments. Expected "
                         (count (:in query))
                         " but got "
                         (count args)
                         ".")
                    {})))
  (let [f (last args)
        query-args (butlast args)
        return-tuples (vector? (:find query))
        query (cond-> query
                (not return-tuples) (update :find vector))]
    (with-open [results (apply xt/open-q db query query-args)]
      (f (cond->> (iterator-seq results)
           (not return-tuples) (map first))))))

(defn parse-lookup-args [args]
  (if (vector? (first args))
    args
    (conj args '[*])))

(defn lookup* [db opts & kvs]
  (let [kvs (partition 2 kvs)
        symbols (vec
                 (for [i (range (count kvs))]
                   (symbol (str "v" i))))]
    (apply q
           db
           (merge
            opts
            {:in symbols
             :where (vec
                     (for [[[k _] sym] (map vector kvs symbols)]
                       ['doc k sym]))})
           (map second kvs))))

(defn lookup [db & args]
  (let [[pull-expr & kvs] (parse-lookup-args args)
        opts {:find (list 'pull 'doc pull-expr)
              :limit 1}]
    (first (apply lookup* db opts kvs))))

(defn lookup-all [db & args]
  (let [[pull-expr & kvs] (parse-lookup-args args)
        opts {:find (list 'pull 'doc pull-expr)}]
    (apply lookup* db opts kvs)))

(defn lookup-id [db & kvs]
  (first (apply lookup* db '{:find doc :limit 1} kvs)))

(defn lookup-id-all [db & kvs]
  (apply lookup* db '{:find doc} kvs))

(defn- special-val? [x]
  (or (= x :db/dissoc)
      (and (coll? x)
           (<= 2 (count x))
           (#{:db/lookup
              :db/union
              :db/difference
              :db/add
              :db/default} (first x)))))

(defn- apply-special-vals [doc-before doc-after]
  (->> (merge doc-before doc-after)
       (keep (fn [[k v]]
               (b/cond
                 (not (special-val? v)) [k v]
                 (= v :db/dissoc) nil
                 :let [[op & xs] v
                       v-before (get doc-before k)]
                 (= op :db/union) [k (set/union (set v-before) (set xs))]
                 (= op :db/difference) [k (set/difference (set v-before) (set xs))]
                 (= op :db/add) [k (apply + (or v-before 0) xs)]
                 :let [[default-value] xs]
                 (= op :db/default) (if (contains? doc-before k)
                                      [k v-before]
                                      [k default-value]))))
       (into {})))

(b/defnc lookup-info [db doc-id]
  :let [[lookup-id default-id] (when (and (special-val? doc-id)
                                          (= :db/lookup (first doc-id)))
                                 (rest doc-id))]
  :when lookup-id
  :let [lookup-doc-before (xt/entity db lookup-id)
        lookup-doc-after (or lookup-doc-before
                             {:xt/id lookup-id
                              :db/owned-by (or default-id (java.util.UUID/randomUUID))})]
  [lookup-id lookup-doc-before lookup-doc-after])

;; TODO Refactor this into smaller tx-xform-* functions
(b/defnc biff-op->xt
  [{:keys [biff/now biff/db biff/malli-opts]}
   {:keys [xt/id db/doc-type db/op] :or {op :put} :as tx-doc}]
  ;; possible ops: delete, put, create, merge, update
  :let [valid? (fn [doc] (malc/validate doc-type doc @malli-opts))
        explain (fn [doc] (male/humanize (malc/explain doc-type doc @malli-opts)))
        [lookup-id
         lookup-doc-before
         lookup-doc-after] (lookup-info db id)
        id (if lookup-id
             (:db/owned-by lookup-doc-after)
             (or id (java.util.UUID/randomUUID)))]
  (= op :delete) (concat [[::xt/delete id]]
                         (when lookup-id
                           [[::xt/match lookup-id lookup-doc-before]
                            [::xt/delete lookup-id]]))

  ;; possible ops: put, create, merge, update
  (nil? doc-type) (throw (ex-info "Missing :db/doc-type."
                                  {:tx-doc tx-doc}))
  :let [doc-after (cond-> tx-doc
                    (map? lookup-id) (merge lookup-id)
                    true (dissoc :db/op :db/doc-type)
                    true (assoc :xt/id id))
        doc-after (walk/postwalk #(if (= % :db/now) now %) doc-after)
        lookup-ops (when lookup-id
                     [[::xt/match lookup-id lookup-doc-before]
                      [::xt/put lookup-doc-after]])]
  :do (cond
        (not= op :put) nil,

        (some special-val? (vals doc-after))
        (throw (ex-info "Attempted to use a special value on a :put operation"
                        {:tx-doc tx-doc})),

        (not (valid? doc-after))
        (throw (ex-info (str "Doc wouldn't be a valid " doc-type " after transaction.")
                        {:tx-doc tx-doc
                         :explain (explain doc-after)})))
  (= op :put) (concat [[::xt/put doc-after]] lookup-ops)

  ;; possible ops: create, merge, update
  :let [doc-before (xt/entity db id)]
  :do (cond
        (not= op :create) nil,

        (some? doc-before) (throw (ex-info "Attempted to create over an existing doc."
                                           {:tx-doc tx-doc})),

        (some special-val? (vals doc-after))
        (throw (ex-info "Attempted to use a special value on a :create operation"
                        {:tx-doc tx-doc})),

        (not (valid? doc-after))
        (throw (ex-info (str "Doc wouldn't be a valid " doc-type " after transaction.")
                        {:tx-doc tx-doc
                         :explain (explain doc-after)})))
  (= op :create) (concat [[::xt/match id nil]
                          [::xt/put doc-after]]
                         lookup-ops)

  ;; possible ops: merge, update
  (and (= op :update)
       (nil? doc-before)) (throw (ex-info "Attempted to update on a new doc."
                                          {:tx-doc tx-doc}))
  :let [doc-after (apply-special-vals doc-before doc-after)]
  (not (valid? doc-after)) (throw (ex-info (str "Doc wouldn't be a valid " doc-type " after transaction.")
                                           {:tx-doc tx-doc
                                            :explain (explain doc-after)}))
  :else (concat [[::xt/match id doc-before]
                 [::xt/put doc-after]]
                lookup-ops))

(defn tx-xform-tmp-ids [_ tx]
  (let [tmp-ids (->> tx
                     (tree-seq (some-fn list?
                                        #(instance? clojure.lang.IMapEntry %)
                                        seq?
                                        #(instance? clojure.lang.IRecord %)
                                        coll?)
                               identity)
                     (filter (fn [x]
                               (and (keyword? x) (= "db.id" (namespace x)))))
                     distinct
                     (map (fn [x]
                            [x (java.util.UUID/randomUUID)]))
                     (into {}))]
    (cond->> tx
      (not-empty tmp-ids) (walk/postwalk #(get tmp-ids % %)))))

(defn tx-xform-upsert [{:keys [biff/db]} tx]
  (mapcat
   (fn [op]
     (if-some [m (:db.op/upsert op)]
       (let [kvs (apply concat m)
             id (apply lookup-id db kvs)
             doc (-> (apply assoc op kvs)
                     (assoc :db/op :merge)
                     (dissoc :db.op/upsert))]
         (if (nil? id)
           [doc [::xt/fn :biff/ensure-unique m]]
           [(assoc doc :xt/id id)]))
       [op]))
   tx))

(defn tx-xform-unique [_ tx]
  (mapcat
   (fn [op]
     (if-let [entries (and (map? op)
                           (->> op
                                (keep (fn [[k v]]
                                        (when (and (vector? v)
                                                   (= (first v) :db/unique))
                                          [k (second v)])))
                                not-empty))]
       (concat
        [(into op entries)]
        (for [[k v] entries]
          [::xt/fn :biff/ensure-unique {k v}]))
       [op]))
   tx))

(defn tx-xform-main [ctx tx]
  (mapcat
   (fn [op]
     (if (map? op)
       (biff-op->xt ctx op)
       [op]))
   tx))

(def default-tx-transformers
  [tx-xform-tmp-ids
   tx-xform-upsert
   tx-xform-unique
   tx-xform-main])

(defn biff-tx->xt [{:keys [biff.xtdb/transformers]
                    :or {transformers default-tx-transformers}
                    :as ctx}
                   biff-tx]
  (reduce (fn [tx xform]
            (xform ctx tx))
          (if (fn? biff-tx)
            (biff-tx ctx)
            biff-tx)
          transformers))

(defn submit-with-retries [ctx make-tx]
  (let [{:keys [biff.xtdb/node
                biff/db
                ::n-tried]
         :or {n-tried 0}
         :as ctx} (-> (assoc-db ctx)
                      (assoc :biff/now (java.util.Date.)))
        tx (make-tx ctx)
        _ (when (and (some (fn [[op]]
                             (= op ::xt/fn))
                           tx)
                     (nil? (xt/with-tx db tx)))
            (throw (ex-info "Transaction violated a constraint" {:tx tx})))
        submitted-tx (when (not-empty tx)
                       (xt/submit-tx node tx))
        ms (int (rand (* 1000 (Math/pow 2 n-tried))))]
    (when submitted-tx
      (xt/await-tx node submitted-tx))
    (cond
      (or (nil? submitted-tx)
          (xt/tx-committed? node submitted-tx)) submitted-tx
      (<= 4 n-tried) (throw (ex-info "TX failed, too much contention." {:tx tx}))
      :else (do
              (log/warnf "TX failed due to contention, trying again in %d ms...\n"
                         ms)
              (flush)
              (Thread/sleep ms)
              (recur (update ctx ::n-tried (fnil inc 0)) make-tx)))))

(defn submit-tx [{:keys [biff.xtdb/retry biff.xtdb/node]
                  :or {retry true}
                  :as ctx} biff-tx]
  (if retry
    (submit-with-retries ctx #(biff-tx->xt % biff-tx))
    (xt/submit-tx node (-> (assoc-db ctx)
                           (assoc :biff/now (java.util.Date.))
                           (biff-tx->xt biff-tx)))))

(def tx-fns
  {:biff/ensure-unique
   '(fn [ctx kvs]
      (let [kvs (for [[i [k v]] (map-indexed vector kvs)
                      :let [sym (symbol (str "v" i))]]
                  {:k k
                   :v v
                   :sym sym})
            query {:find '[doc],
                   :limit 2,
                   :in (mapv :sym kvs)
                   :where (vec
                           (for [{:keys [k sym]} kvs]
                             ['doc k sym]))}]
        (when (< 1 (count (apply xtdb.api/q
                                 (xtdb.api/db ctx)
                                 query
                                 (map :v kvs))))
          false)))})

(defn test-node [docs]
  (let [node (xt/start-node {})]
    (xt/await-tx
     node
     (xt/submit-tx node
       (vec
        (concat
         (for [d docs]
           [::xt/put (merge {:xt/id (random-uuid)}
                            d)])
         (for [[k f] tx-fns]
           [::xt/put {:xt/id k :xt/fn f}])))))
    node))

;;;; Deprecated ================================================================

(defn start-node
  [{:keys [topology dir opts jdbc-spec pool-opts kv-store tx-fns indexes ctx]
    :or {kv-store :rocksdb}}]
  (let [kv-store-fn (fn [basename]
                      {:kv-store {:xtdb/module (if (= kv-store :lmdb)
                                                 'xtdb.lmdb/->kv-store
                                                 'xtdb.rocksdb/->kv-store)
                                  :db-dir (io/file dir (str basename (when (= kv-store :lmdb)
                                                                       "-lmdb")))}})
        node (xt/start-node
              (merge (case topology
                       :memory
                       {}

                       :standalone
                       {:xtdb/index-store    (kv-store-fn "index")
                        :xtdb/document-store (kv-store-fn "docs")
                        :xtdb/tx-log         (kv-store-fn "tx-log")}

                       :jdbc
                       {:xtdb/index-store (kv-store-fn "index")
                        :xtdb.jdbc/connection-pool {:dialect {:xtdb/module
                                                              'xtdb.jdbc.psql/->dialect}
                                                    :pool-opts pool-opts
                                                    :db-spec jdbc-spec}
                        :xtdb/tx-log {:xtdb/module 'xtdb.jdbc/->tx-log
                                      :connection-pool :xtdb.jdbc/connection-pool}
                        :xtdb/document-store {:xtdb/module 'xtdb.jdbc/->document-store
                                              :connection-pool :xtdb.jdbc/connection-pool}})
                     opts))
        f (future (xt/sync node))]
    (Thread/sleep 5)
    (while (not (realized? f))
      (Thread/sleep 1000)
      (when-some [indexed (xt/latest-completed-tx node)]
        (log/info "Indexed" (pr-str indexed))))
    (when (not-empty tx-fns)
      (save-tx-fns! node tx-fns))
    node))
