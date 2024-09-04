(ns com.biffweb.impl.xtdb.index
  (:require [com.biffweb.impl.util :as biff.util :refer [<<-]]
            [com.biffweb.impl.xtdb.util :as biff.xt.util]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [xtdb.api :as xt]
            [xtdb.tx :as xt.tx])
  (:import [java.io Closeable]))

(defn- tx-id-for [node t]
  (get-in (xt/db-basis (xt/db node {::xt/tx-time t}))
          [::xt/tx ::xt/tx-id]))

(defn- empty-db [node]
  (if (xt/latest-completed-tx node)
    (xt/db node {::xt/tx {::xt/tx-id -1}})
    (xt/db node)))

(defn- await-db [node tx-id]
  (let [basis {::xt/tx-id tx-id}]
    (xt/await-tx node basis)
    (xt/db node {::xt/tx basis})))

(defn- index-id->db-dir [id]
  (-> (str id)
      (str/replace #"/" "_")
      (str/replace #"[^a-zA-Z0-9-]+" "")))

(defn indexer-args [db-provider {::xt/keys [tx-id tx-ops]}]
  (let [prev-db (delay (xt/db db-provider {::xt/tx {::xt/tx-id (dec tx-id)}}))
        [args _] (->> tx-ops
                      ((fn step [tx-ops]
                         (mapcat (fn [[op arg1 arg2]]
                                   (case op
                                     ::xt/put [#:biff.index{:op ::xt/put
                                                            :doc arg1}]
                                     ::xt/fn (step (::xt/tx-ops arg2))
                                     ::xt/delete [#:biff.index{:op ::xt/delete
                                                               :id arg1}]
                                     nil))
                                 tx-ops)))
                      (reduce (fn [[args id->doc] {:biff.index/keys [op doc id] :as arg}]
                                (case op
                                  ::xt/put [(conj args arg) (assoc id->doc (:xt/id doc) doc)]
                                  ::xt/delete [(conj args
                                                     {:biff.index/op ::xt/delete
                                                      :biff.index/doc (if (contains? id->doc id)
                                                                        (id->doc id)
                                                                        (xt/entity @prev-db id))})
                                               (assoc id->doc id nil)]))
                              [[] {}]))]
    (vec args)))

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
            (let [[index-tx new-id->doc] (run-indexer indexer (:biff.index/args main-tx) id->doc)]
              (recur (cond-> results
                       (not-empty index-tx) (conj {:main-tx main-tx :index-tx index-tx}))
                     (merge id->doc new-id->doc)
                     (inc txes-processed)
                     remaining))))]
    {:results results
     :all-docs (filterv some? (vals id->doc))
     :txes-processed txes-processed}))

(defn start-index-node [{index-topology :biff.index/topology
                         :or {index-topology :standalone}
                         :as ctx} index-id]
  (let [db-dir (index-id->db-dir index-id)]
    (xt/start-node
     (case index-topology
       :memory {}
       :standalone {:xtdb/index-store    (biff.xt.util/kv-store ctx (str "biff-indexes/" db-dir "/index"))
                    :xtdb/document-store (biff.xt.util/kv-store ctx (str "biff-indexes/" db-dir "/docs"))
                    :xtdb/tx-log         (biff.xt.util/kv-store ctx (str "biff-indexes/" db-dir "/tx-log"))}))))

(defn prepare-index! [{:biff.xtdb/keys [node] :as ctx}
                      {:keys [id indexer version]}]
  (<<- (with-open [index-node (start-index-node ctx id)])
       (let [_ (biff.xt.util/verbose-sync id index-node)
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
  {:xtdb.system/deps {:xtdb/secondary-indices :xtdb/secondary-indices
                      :xtdb/query-engine :xtdb/query-engine}
   :xtdb.system/before #{[:xtdb/tx-ingester]}}
  [{:keys [xtdb/secondary-indices
           xtdb/query-engine
           biff/indexes
           biff.index/tx-metadata]}]
  (doseq [[_ {indexed-main-tx-id ::xt/tx-id
              index-id :id
              :keys [node indexer version abort-on-error]}] indexes
          :let [aborted (atom false)]]
    (xt.tx/register-index!
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
                                                (indexer-args query-engine new-main-tx)
                                                get-doc)]
                        ;; TODO get rid of this to speed things up?
                        (when (not-empty tx)
                          (xt/with-tx @db tx))
                        tx)
                      (catch Exception e
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

(defn index-snapshots [{:keys [biff.xtdb/node biff/indexes biff.index/tx-metadata]}]
  (if-some [node-id->tx-id (:latest-consistent-tx-ids @tx-metadata)]
    (-> indexes
        (update-vals (fn [{:keys [id node]}]
                       (await-db node (node-id->tx-id id))))
        (assoc :biff/db (await-db node (node-id->tx-id :biff.xtdb/node))))
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

(defn start-indexes [{:keys [biff/secret
                             biff/modules
                             biff/plugins
                             biff/features]
                      :biff.xtdb/keys [topology dir kv-store opts tx-fns]
                      index-topology :biff.index/topology
                      :or {kv-store :rocksdb
                           index-topology :standalone}
                      :as ctx}]
  (let [modules (biff.util/ctx->modules ctx)
        indexes (mapcat :indexes modules)
        _ (doseq [[db-dir indexes] (group-by (comp index-id->db-dir :id) indexes)
                  :when (< 1 (count indexes))]
            (throw (ex-info "Invalid indexes: multiple index IDs map to the same db-dir"
                            {:ids (mapv :id indexes)
                             :db-dir db-dir})))
        indexes (into {} (for [{:keys [id] :as index} indexes]
                           [id (assoc index :node (start-index-node ctx id))]))
        indexes (update-vals indexes
                             (fn [{:keys [id node version] :as index}]
                               (biff.xt.util/verbose-sync id node)
                               (let [{:keys [node]
                                      :as index} (if-not (and (= index-topology :standalone)
                                                              (some-> (xt/entity (xt/db node) :biff.index/metadata)
                                                                      :biff.index/version
                                                                      (not= version)))
                                                   index
                                                   (do
                                                     (.close node)
                                                     (->> (file-seq (io/file (str dir "/biff-indexes/" (index-id->db-dir id))))
                                                          reverse
                                                          (run! io/delete-file))
                                                     (assoc index :node (start-index-node index))))]
                                 (merge index (xt/entity (xt/db node) :biff.index/metadata)))))
        ;; TODO call prepare-index! here
        max-indexed-tx-id (some->> (vals indexes)
                                   (keep ::xt/tx-id)
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
                                    (hash-map max-indexed-tx-id))})]
    (assoc ctx :biff/indexes indexes :biff.index/tx-metadata tx-metadata)))
