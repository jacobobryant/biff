(ns com.biffweb.impl.index
  (:require [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [com.biffweb.impl.util :as biff.util :refer [<<-]]
            [com.biffweb.impl.util.ns :as biff.util.ns]
            [com.biffweb.protocols :as biff.proto]
            [taoensso.nippy :as nippy]
            [xtdb.api :as xt]
            [xtdb.tx :as xt.tx]
            [clojure.walk :as walk])
  (:import java.util.Map
           java.util.HashMap
           java.util.function.Function
           java.util.ArrayList
           (java.io Closeable File)
           (java.nio.file Files Path)
           (java.nio.file.attribute FileAttribute)
           (java.nio ByteBuffer)
           [java.time Instant]
           java.nio.file.attribute.FileAttribute
           (org.rocksdb BlockBasedTableConfig Checkpoint CompressionType FlushOptions LRUCache
                        DBOptions Options ReadOptions RocksDB RocksIterator
                        WriteBatchWithIndex WriteBatch WriteOptions Statistics StatsLevel
                        ColumnFamilyOptions ColumnFamilyDescriptor ColumnFamilyHandle BloomFilter)
           (xtdb.api IXtdbDatasource)))

(defn- key->bytes
  "Converts k to a stable byte representation.

   Supports the most common scalar types. Vectors are the only supported collection type. Instead of
   (key->bytes {:foo \"bar\"}), do (key->bytes [:foo \"bar\"]).

   java.util.Date and java.time.Instant can be used interchangeably. They're both encoded as
   milliseconds-since-the-epoch."
  [k]
  (let [[prefix _bytes]
        (cond
          (string? k)         [0 (.getBytes k)]
          (keyword? k)        [1 (.getBytes (subs (str k) 1))]
          (symbol? k)         [2 (.getBytes (str k))]
          (inst? k)           [3 (.. (ByteBuffer/allocate 8)
                                     (putLong (inst-ms k))
                                     (array))]
          (uuid? k)           [4 (.. (ByteBuffer/allocate 16)
                                     (putLong (.getMostSignificantBits k))
                                     (putLong (.getLeastSignificantBits k))
                                     array)]
          (= Long (type k))   [5 (.. (ByteBuffer/allocate 8)
                                     (putLong k)
                                     array)]
          (= Double (type k)) [6 (.. (ByteBuffer/allocate 8)
                                     (putDouble k)
                                     array)]
          (vector? k)         [7 (mapcat (fn [x]
                                           (let [_bytes (key->bytes x)
                                                 cnt (count _bytes)]
                                             (when (< 255 cnt)
                                               (throw (ex-info (str "Vectors elements index keys can't have an encoded"
                                                                    " size of greater than 255 bytes.")
                                                               {:element x
                                                                :n-bytes cnt})))
                                             (cons cnt _bytes)))
                                         k)]
          :else (throw (ex-info (str "Type not supported for index keys: " (type k))
                                {:key k})))]
    (byte-array (cons prefix _bytes))))

(defn rocks-get [rocksdb handle _key]
  (some-> (.get rocksdb handle (key->bytes _key)) nippy/thaw))

(defn rocks-put [rocksdb handle _key value]
  (.put rocksdb handle (key->bytes _key) (nippy/freeze value)))

(defn use-indexes [{index-dir :biff.index/dir
                    :or {index-dir "storage/biff-index"}
                    :as ctx}]
  (<<- (let [modules (biff.util/ctx->modules ctx)
             indexes (into {}
                           (map (fn [{:keys [id] :as index}]
                                  [id (biff.util.ns/select-ns-as index nil 'biff.index)]))
                           (mapcat :indexes modules))])
       (if (empty? indexes)
         ctx)
       (do (RocksDB/loadLibrary))
       (let [cf-opts        (.optimizeUniversalStyleCompaction (ColumnFamilyOptions.))
             cf-descriptors (java.util.ArrayList. (into [(ColumnFamilyDescriptor. RocksDB/DEFAULT_COLUMN_FAMILY cf-opts)]
                                                        (map #(ColumnFamilyDescriptor. (.getBytes (str %)) cf-opts))
                                                        (keys indexes)))
             handles        (java.util.ArrayList.)
             db-options     (.. (DBOptions.)
                                (setCreateIfMissing true)
                                (setCreateMissingColumnFamilies true))
             tmp-dir        (when (= index-dir :tmp)
                              (str (Files/createTempDirectory "biff-test" (into-array FileAttribute []))))
             rocksdb        (RocksDB/open db-options (or tmp-dir (doto index-dir io/make-parents)) cf-descriptors handles)
             id-str->handle (into {}
                                  (map (fn [handle]
                                         [(String. (.getName handle)) handle]))
                                  handles)
             indexes        (update-vals indexes
                                         (fn [{:biff.index/keys [id version] :as index}]
                                           (let [handle         (get id-str->handle (str id))
                                                 _meta          (rocks-get rocksdb handle :biff.index/metadata)
                                                 [handle _meta] (if (= version (:biff.index/version _meta version))
                                                                  [handle _meta]
                                                                  (do
                                                                    (.dropColumnFamily rocksdb handle)
                                                                    (.close handle)
                                                                    [(.createColumnFamily
                                                                      rocksdb
                                                                      (ColumnFamilyDescriptor. (.getBytes (str id)) cf-opts))
                                                                     {}]))]
                                             (merge index _meta {:biff.index/handle handle}))))
             close-fn       (fn []
                              (doseq [{:biff.index/keys [id handle]} (vals indexes)]
                                (.close handle))
                              (.close rocksdb)
                              (.close db-options)
                              (.close cf-opts)
                              (when tmp-dir
                                (run! io/delete-file (reverse (file-seq (io/file tmp-dir))))))])
       (-> ctx
           (update :biff/stop conj close-fn)
           (assoc :biff.index/rocksdb rocksdb
                  :biff.index/indexes indexes
                  :biff.index/snapshot-data (atom {})))))

(defn- with-cache [f m]
  (fn [k]
    (if (contains? m k)
      (get m k)
      (f k))))

(defn- xtdb-indexer-args [db-provider {::xt/keys [tx-id tx-ops]}]
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

(defn- run-indexer [index-id indexer index-args index-get]
  (second
    (reduce (fn [[changes serialized-changes] args]
              (try
                (let [index-get* (fn [id]
                                   (if (contains? changes id)
                                     (get changes id)
                                     (index-get id)))
                      new-changes (indexer (assoc args :biff.index/index-get index-get*))
                      _ (when-not (or (nil? new-changes) (map? new-changes))
                          (throw (ex-info "Indexer return value must be either a map or nil"
                                          {:biff.index/id index-id
                                           :return-value new-changes})))]
                  [(merge changes new-changes)
                   (into serialized-changes
                         (map (fn [[k v]]
                                [(try
                                   (key->bytes k)
                                   (catch Exception e
                                     (throw (ex-info "Couldn't serialize key from indexer"
                                                     {:biff.index/id index-id
                                                      :key k}
                                                     e))))
                                 (try
                                   (nippy/freeze v)
                                   (catch Exception e
                                     (throw (ex-info "Couldn't serialize value from indexer"
                                                     {:biff.index/id index-id
                                                      :key k
                                                      :value v}
                                                     e))))]))
                         new-changes)])
                (catch Exception e
                  (throw (ex-info (str "Exception while indexing. After fixing the problem, "
                                       "you should re-index by incrementing the index version and "
                                       "refreshing/restarting your app.")
                                  (merge {:biff.index/id index-id} args)
                                  e)))))
            [{} {}]
            index-args)))

(defn ->xtdb-index
  {:xtdb.system/deps {:xtdb/secondary-indices :xtdb/secondary-indices
                      :xtdb/query-engine :xtdb/query-engine}
   :xtdb.system/before #{[:xtdb/tx-ingester]}}
  [{:xtdb/keys [secondary-indices query-engine]
    :biff.index/keys [rocksdb indexes snapshot-data]}]
  (doseq [_ [nil]
          :let [tx->index->changes (atom {})]
          [_ {indexed-tx-id ::xt/tx-id
              index-id :biff.index/id
              :biff.index/keys [indexer version abort-on-error handle]
              :or {indexed-tx-id -1}}] indexes
          :let [aborted      (atom false)
                committed-at (atom (Instant/now))]]
    (xt.tx/register-index!
     secondary-indices
     indexed-tx-id
     {:with-tx-ops? true}
     (fn [{:keys [::xt/tx-ops committing?]
           current-tx-id ::xt/tx-id
           :as tx}]
       (when-not (and abort-on-error @aborted)
         (let [changes (when committing?
                         (let [index-get      (memoize #(rocks-get rocksdb handle %))
                               indexer-args (xtdb-indexer-args query-engine tx)]
                           (try
                             (run-indexer index-id indexer indexer-args index-get)
                             (catch Exception e
                               (log/error e)
                               (when abort-on-error
                                 (reset! aborted true))
                               nil))))
               changes (when (or (not-empty changes)
                                 (< (inst-ms (.plusSeconds @committed-at 30))
                                    (inst-ms (Instant/now))))
                         (assoc changes
                                (key->bytes :biff.index/metadata)
                                (nippy/freeze {:biff.index/version version
                                               ::xt/tx-id current-tx-id})))
               index->changes (get (swap! tx->index->changes assoc-in [current-tx-id index-id] changes)
                                   current-tx-id)
               current-tx-complete (= (count index->changes)
                                      (count (filterv (fn [[_ {:keys [::xt/tx-id]}]]
                                                        (< (or tx-id -1) current-tx-id))
                                                      indexes)))
               have-changes (boolean (some not-empty (vals index->changes)))]
           (when current-tx-complete
             (swap! tx->index->changes dissoc current-tx-id)

             (when-not have-changes
               (swap! snapshot-data assoc :latest-tx-id current-tx-id))

             (when have-changes
               (with-open [batch         (WriteBatch.)
                           write-options (WriteOptions.)]
                 (doseq [[index-id changes] index->changes
                         :let [handle (get-in indexes [index-id :biff.index/handle])]
                         [k v] changes]
                   (.put batch handle k v))
                 (.write rocksdb write-options batch))
               (let [snapshot          (.getSnapshot rocksdb)
                     read-options      (doto (ReadOptions.) (.setSnapshot snapshot))
                     old-snapshot-data @snapshot-data
                     new-snapshot-data (swap! snapshot-data
                                              (fn [snapshot-data]
                                                (assoc snapshot-data
                                                       current-tx-id {:snapshot snapshot
                                                                      :read-options read-options
                                                                      :n-clients 0}
                                                       :latest-snapshotted-tx-id current-tx-id
                                                       :latest-tx-id current-tx-id)))
                     {:keys [n-clients snapshot read-options]} (get new-snapshot-data
                                                                    (:latest-snapshotted-tx-id old-snapshot-data))]
                 (when (= 0 n-clients)
                   (.close read-options)
                   (.releaseSnapshot rocksdb snapshot)
                   (.close snapshot)
                   (swap! snapshot-data dissoc (:latest-snapshotted-tx-id old-snapshot-data))))))))))))

(defrecord Snapshots [xtdb rocksdb index-id->handle snapshot-data index-tx-id read-options]
  biff.proto/IndexSnapshot
  (index-get [_ index-id k]
    (some-> (.get rocksdb
                  (index-id->handle index-id)
                  read-options
                  (key->bytes k))
            nippy/thaw))
  (index-get-many [this index-id ks]
    (biff.proto/index-get-many this (mapv vector (repeat index-id) ks)))
  (index-get-many [_ index-id-key-pairs]
    (mapv #(some-> % nippy/thaw)
          (.multiGetAsList rocksdb
                           read-options
                           (ArrayList. (mapv (comp index-id->handle first) index-id-key-pairs))
                           (ArrayList. (mapv (comp key->bytes second) index-id-key-pairs)))))




  xt/PXtdbDatasource
  (entity [_ eid]
    (xt/entity xtdb eid))
  (entity-tx [_ eid]
    (xt/entity-tx xtdb eid))
  (q* [_ query args]
    (xt/q* xtdb query args))
  (open-q* [_ query args]
    (xt/open-q* xtdb query args))
  (pull [_ query eid]
    (xt/pull xtdb query eid))
  (pull-many [_ query eids]
    (xt/pull-many xtdb query eids))
  (entity-history [_ eid sort-order]
    (xt/entity-history xtdb eid sort-order))
  (entity-history [_ eid sort-order opts]
    (xt/entity-history xtdb eid sort-order opts))
  (open-entity-history [_ eid sort-order]
    (xt/open-entity-history xtdb eid sort-order))
  (open-entity-history [_ eid sort-order opts]
    (xt/open-entity-history xtdb eid sort-order opts))
  (valid-time [_]
    (xt/valid-time xtdb))
  (transaction-time [_]
    (xt/transaction-time xtdb))
  (db-basis [_]
    (xt/db-basis xtdb))
  (with-tx [_ tx-ops]
    (xt/with-tx xtdb tx-ops))

  java.io.Closeable
  (close [_]
    (let [{:keys [latest-snapshotted-tx-id]
           {:keys [n-clients snapshot read-options]} index-tx-id}
          (swap! snapshot-data update-in [index-tx-id :n-clients] dec)]
      (when (and (= 0 n-clients) (not= latest-snapshotted-tx-id index-tx-id))
        (.close read-options)
        (.releaseSnapshot rocksdb snapshot)
        (.close snapshot)
        (swap! snapshot-data dissoc index-tx-id)))
    (.close xtdb)))

(defn read-snapshots [{:keys [biff.index/rocksdb
                              biff.index/indexes
                              biff.index/snapshot-data
                              biff.xtdb/node]}]
  (let [{:keys [latest-tx-id
                latest-snapshotted-tx-id]
         :as snapshot-data'} (swap! snapshot-data
                                    (fn [snapshot-data]
                                      (update-in snapshot-data
                                                 [(:latest-snapshotted-tx-id snapshot-data)
                                                  :n-clients]
                                                 inc)))
        tx {::xt/tx-id latest-tx-id}]
    (xt/await-tx node tx)
    (Snapshots. (xt/open-db node {::xt/tx tx})
                rocksdb
                (update-vals indexes :biff.index/handle)
                snapshot-data
                latest-snapshotted-tx-id
                (get-in snapshot-data' [latest-snapshotted-tx-id :read-options]))))


;; TODO write more functions:
;; - test-tx-log
;; - prepare-index!
