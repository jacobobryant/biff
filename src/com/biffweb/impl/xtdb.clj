(ns com.biffweb.impl.xtdb
  (:require [better-cond.core :as b]
            [com.biffweb.impl.util :as util]
            [com.biffweb.impl.util.ns :as ns]
            [clojure.java.io :as io]
            [clojure.set :as set]
            [clojure.tools.logging :as log]
            [clojure.walk :as walk]
            [xtdb.api :as xt]
            [malli.error :as male]
            [malli.core :as malc]))

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

(defn start-node
  [{:keys [topology dir opts jdbc-spec pool-opts kv-store tx-fns]
    :or {kv-store :rocksdb}}]
  (let [kv-store-fn (fn [basename]
                      {:kv-store {:xtdb/module (if (= kv-store :lmdb)
                                                 'xtdb.lmdb/->kv-store
                                                 'xtdb.rocksdb/->kv-store)
                                  :db-dir (io/file dir (str basename (when (= kv-store :lmdb)
                                                                       "-lmdb")))}})
        node (xt/start-node
              (merge (case topology
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
    (while (not (realized? f))
      (Thread/sleep 2000)
      (when-some [indexed (xt/latest-completed-tx node)]
        (log/info "Indexed" (pr-str indexed))))
    (when (not-empty tx-fns)
      (save-tx-fns! node tx-fns))
    node))

(defn use-xt
  [{:keys [biff/secret]
    :biff.xtdb/keys [topology dir kv-store opts tx-fns]
    :or {kv-store :rocksdb}
    :as ctx}]
  (let [node (start-node
              {:topology topology
               :dir dir
               :kv-store kv-store
               :opts opts
               :jdbc-spec (cond-> (ns/select-ns-as ctx 'biff.xtdb.jdbc nil)
                            secret (assoc :password (secret :biff.xtdb.jdbc/password)))
               :pool-opts (ns/select-ns-as ctx 'biff.xtdb.jdbc-pool nil)
               :tx-fns tx-fns})]
    (-> ctx
        (assoc :biff.xtdb/node node)
        (update :biff/stop conj #(.close node)))))

(defn assoc-db [{:keys [biff.xtdb/node] :as ctx}]
  (assoc ctx :biff/db (xt/db node)))

(defn merge-context [{:keys [biff/merge-context-fn]
                      :or {merge-context-fn assoc-db}
                      :as ctx}]
  (merge-context-fn ctx))

(defn use-tx-listener [{:keys [biff/features
                               biff/plugins
                               biff.xtdb/on-tx
                               biff.xtdb/node]
                        :as ctx}]
  (if-not (or on-tx plugins features)
    ctx
    (let [on-tx (or on-tx
                    (fn [ctx tx]
                      (doseq [{:keys [on-tx]} @(or plugins features)
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
             id (apply lookup-id db kvs)]
         (cond-> [(-> (apply assoc op kvs)
                      (assoc :db/op :merge
                             :xt/id id)
                      (dissoc :db.op/upsert))]
           (nil? id) (conj [::xt/fn :biff/ensure-unique m])))
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
