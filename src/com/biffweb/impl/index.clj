(ns com.biffweb.impl.index
  (:require [clojure.java.io :as io]
            [com.biffweb.impl.util :as biff.util :refer [<<-]]
            [com.biffweb.impl.util.ns :as biff.util.ns]
            [taoensso.nippy :as nippy])
  (:import java.util.Map
           java.util.HashMap
           java.util.function.Function
           (java.io Closeable File)
           (java.nio.file Files Path)
           java.nio.file.attribute.FileAttribute
           (org.rocksdb BlockBasedTableConfig Checkpoint CompressionType FlushOptions LRUCache
                        DBOptions Options ReadOptions RocksDB RocksIterator
                        WriteBatchWithIndex WriteBatch WriteOptions Statistics StatsLevel
                        ColumnFamilyOptions ColumnFamilyDescriptor ColumnFamilyHandle BloomFilter)))

(defn rocks-get [rocksdb handle _key]
  (some-> (.get rocksdb handle (.getBytes (str _key))) nippy/thaw))

(defn rocks-put [rocksdb handle _key value]
  (.put rocksdb handle (.getBytes (str _key)) (nippy/freeze value)))

(defn use-indexes [{index-dir :biff.index/dir
                    :or {index-dir "storage/biff-index"}
                    :as ctx}]
  (RocksDB/loadLibrary)
  (let [modules         (biff.util/ctx->modules ctx)
        indexes         (into {}
                              (map (fn [{:keys [id] :as index}]
                                     [id (biff.util.ns/select-ns-as index nil 'biff.index)]))
                              (mapcat :indexes modules))
        cf-opts         (.optimizeUniversalStyleCompaction (ColumnFamilyOptions.))
        cf-descriptors  (java.util.ArrayList. (into [(ColumnFamilyDescriptor. RocksDB/DEFAULT_COLUMN_FAMILY cf-opts)]
                                                    (map #(ColumnFamilyDescriptor. (.getBytes (str %)) cf-opts))
                                                    (keys indexes)))
        handles         (java.util.ArrayList.)
        db-options      (.. (DBOptions.)
                            (setCreateIfMissing true)
                            (setCreateMissingColumnFamilies true))
        rocksdb         (RocksDB/open db-options (doto "storage/biff-index" io/make-parents) cf-descriptors handles)
        id-str->handle  (into {}
                              (map (fn [handle]
                                     [(String. (.getName handle)) handle]))
                              handles)
        indexes         (update-vals indexes
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
                                         (merge index
                                                _meta
                                                {:biff.index/handle handle}))))
        close-fn        (fn []
                          (doseq [{:biff.index/keys [id handle]} (vals indexes)]
                            (.close handle))
                          (.close rocksdb)
                          (.close db-options)
                          (.close cf-opts))]
    (-> ctx
        (update :biff/stop conj close-fn)
        (assoc :biff.index/rocksdb rocksdb
               :biff.index/indexes indexes
               :biff.index/tx-id->snapshot-data (atom {})))))
