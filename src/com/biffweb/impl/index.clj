(ns com.biffweb.impl.index
  (:require [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [com.biffweb.impl.util :as biff.util :refer [<<-]]
            [com.biffweb.impl.util.ns :as biff.util.ns]
            [taoensso.nippy :as nippy]
            [xtdb.api :as xt]
            [xtdb.tx :as xt.tx]
            [clojure.walk :as walk])
  (:import java.util.Map
           java.util.HashMap
           java.util.function.Function
           (java.io Closeable File)
           (java.nio.file Files Path)
           (java.nio.file.attribute FileAttribute)
           (java.nio ByteBuffer)
           [java.time Instant]
           java.nio.file.attribute.FileAttribute
           (org.rocksdb BlockBasedTableConfig Checkpoint CompressionType FlushOptions LRUCache
                        DBOptions Options ReadOptions RocksDB RocksIterator
                        WriteBatchWithIndex WriteBatch WriteOptions Statistics StatsLevel
                        ColumnFamilyOptions ColumnFamilyDescriptor ColumnFamilyHandle BloomFilter)))

(defn key->bytes
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
                                               (throw (ex-info "Vectors elements index keys can't have an encoded size of greater than 255 bytes."
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

(defn xtdb-indexer-args [db tx]
  nil)

(defn run-indexer [indexer args get-doc]
  nil)

#_(log/error e
                                              (str "Exception while indexing! You can reproduce the exception by calling "
                                                   "(biff/run-indexer (get-context) " (pr-str indexer-args) " "
                                                   (pr-str @accessed-docs)
                                                   main-tx-id ") from the REPL. "
                                                   "After fixing the problem, you should re-index by incrementing the index version and "
                                                   "refreshing/restarting your app.")
                                              (pr-str {:biff.index/id index-id
                                                       :biff.index/version version
                                                       :biff.index/get-doc-values @accessed-docs
                                                       ;::xt/tx-id main-tx-id
                                                       }))

(defn- with-cache [f m]
  (fn [k]
    (if (contains? m k)
      (get m k)
      (f k))))

#_(defn ->xtdb-index
  {:xtdb.system/deps {:xtdb/secondary-indices :xtdb/secondary-indices
                      :xtdb/query-engine :xtdb/query-engine}
   :xtdb.system/before #{[:xtdb/tx-ingester]}}
  [{:xtdb/keys [secondary-indices query-engine]
    :biff.index/keys [rocksdb indexes snapshot-data]}]
  (doseq [_ [nil]
          :let [all-changes* (atom {})
                
                ]
          [_ {indexed-tx-id ::xt/tx-id
              index-id :biff.index/id
              :biff.index/keys [indexer version abort-on-error handle]
              :keys [node indexer version abort-on-error]
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
                         (let [get-doc      (memoize #(rocks-get rocksdb handle %))
                               indexer-args (xtdb-indexer-args query-engine tx)]
                           (try
                             (run-indexer indexer indexer-args get-doc)
                             (catch Exception e
                               (log/error e)
                               (when abort-on-error
                                 (reset! aborted true))
                               nil))))
               now (Instant/now)
               changes (when (or (not-empty changes)
                                 (< (inst-ms (.plusSeconds @committed-at 30))
                                    (inst-ms now)))
                         (merge changes
                                {:biff.index/metadata {:biff.index/version version
                                                       ::xt/tx-id current-tx-id}}))
               all-changes (swap! all-changes* assoc index-id changes)
               n-indexes (count (keep (fn [[id {:keys [::xt/tx-id]}]]
                                        (when (< (or tx-id -1) current-tx-id)
                                          id))
                                      indexes))
               
               ]
           (when (= n-indexes (count all-changes))
             ;; TODO write to rocksdb
             )

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

  )

;; TODO write more functions:
;; - test-tx-log
;; - indexer-results
;; - prepare-index!
;; - ->xtdb-module-thing
;; - `snapshots` fn for returning `get-doc` function or something + current XT snapshot
;; - tooling to replace replay-indexer (serialize all the args maybe?)

(comment

  ;; need to close
  (def cf-opts (.optimizeUniversalStyleCompaction (ColumnFamilyOptions.)))

  (def cf-descriptors (java.util.ArrayList. [(ColumnFamilyDescriptor. RocksDB/DEFAULT_COLUMN_FAMILY cf-opts)
                                             (ColumnFamilyDescriptor. (.getBytes "cf-1") cf-opts)
                                             (ColumnFamilyDescriptor. (.getBytes "cf-2") cf-opts)]))

  ;; need to close (before the db)
  (def cf-handles (java.util.ArrayList.))

  ;; need to close
  (def db-options (.. (DBOptions.)
                      (setCreateIfMissing true)
                      (setCreateMissingColumnFamilies true)))


  ;; need to close
  (def db (RocksDB/open db-options "rocksdb-index-test" cf-descriptors cf-handles))

  (def id->handle (into {}
                        (map (fn [cf-handle]
                               [(String. (.getName cf-handle)) cf-handle]))
                        cf-handles))


  (.put db (id->handle "cf-1") (.getBytes "foo") (.getBytes "bar"))
  (String. (.get db (id->handle "cf-1") (.getBytes "foo")))
  (.put db (id->handle "cf-2") (.getBytes "foo") (.getBytes "quux"))
  (String. (.get db (id->handle "cf-2") (.getBytes "foo")))

  (.dropColumnFamily db (id->handle "cf-1"))
  (.close (id->handle "cf-1"))
  (def new-handle (.createColumnFamily db (ColumnFamilyDescriptor. (.getBytes "cf-1") cf-opts)))
  (def id->handle (assoc id->handle "cf-1" new-handle))


  (with-open [batch (WriteBatch.)
              write-options (WriteOptions.)]
    (.put batch (id->handle "cf-1") (.getBytes "a") (.getBytes "b"))
    (.put batch (id->handle "cf-1") (.getBytes "c") (.getBytes "d"))
    (.write db write-options batch)
    )
  (String. (.get db (id->handle "cf-1") (.getBytes "a")))
  (String. (.get db (id->handle "cf-1") (.getBytes "c")))

  (mapv #(String. %) (.multiGetAsList db
                                      (java.util.ArrayList. [(id->handle "cf-1") (id->handle "cf-1")])
                                      (java.util.ArrayList. [(.getBytes "a") (.getBytes "c")])))

  ;; need to close
  (def snapshot (.getSnapshot db))
  ;; need to close
  (def read-options (doto (ReadOptions.) (.setSnapshot snapshot)))

  (String. (.get db (id->handle "cf-1") (.getBytes "foo")))
  (String. (.get db (id->handle "cf-1") read-options (.getBytes "foo")))
  (.put db (id->handle "cf-1") (.getBytes "foo") (.getBytes "abc"))

  (.close read-options)

  (.releaseSnapshot db snapshot)

  (.close snapshot)


  )

