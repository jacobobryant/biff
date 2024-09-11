(ns com.biffweb.impl.index-test
  (:require [clojure.test :refer [deftest is]]
            [clojure.java.io :as io]
            [com.biffweb :as biff]
            [com.biffweb.impl.index :as biff.index]
            [xtdb.api :as xt])
  (:import (org.rocksdb RocksDB ColumnFamilyHandle)
           (java.nio.file Files Paths)
           (java.nio.file.attribute FileAttribute)))

(defn rocks-get [{:biff.index/keys [rocksdb indexes]} index-id _key]
  (biff.index/rocks-get rocksdb (get-in indexes [index-id :biff.index/handle]) _key))

(defn rocks-put [{:biff.index/keys [rocksdb indexes]} index-id _key value]
  (biff.index/rocks-put rocksdb (get-in indexes [index-id :biff.index/handle]) _key value))

(defn modules [foo-version bar-version]
  (delay [{:indexes [{:id :foo
                      :indexer (fn [{:biff.index/keys [doc op]}]
                                 {"test" "foo"})
                      :version foo-version}
                     {:id :bar
                      :indexer (fn [{:biff.index/keys [doc op]}]
                                 {"test" "bar"})
                      :version bar-version}]}]))

(defn create-temp-dir! []
  (let [tmp-dir (str (Files/createTempDirectory "biff-test" (into-array FileAttribute [])))]
    [tmp-dir #(run! io/delete-file (reverse (file-seq (io/file tmp-dir))))]))

(deftest use-indexes
  (let [[tmp-dir close-tmp-dir!] (create-temp-dir!)]
    (try
      (let [{:biff.index/keys [rocksdb
                               indexes
                               snapshot-data]
             :as ctx}
            (biff/use-indexes {:biff/modules (modules 0 0)
                               :biff.index/dir tmp-dir})]
        (try
          (is (= #{:foo :bar} (set (keys indexes))))
          (is (every? #(instance? ColumnFamilyHandle (:biff.index/handle %)) (vals indexes)))
          (is (instance? RocksDB rocksdb))
          (is (instance? clojure.lang.Atom snapshot-data))
          (rocks-put ctx :foo "hello" :there)
          (is (= :there (rocks-get ctx :foo "hello")))
          (is (= nil (rocks-get ctx :bar "hello")))
          (rocks-put ctx :foo :biff.index/metadata {:biff.index/version 0})
          (rocks-put ctx :bar :biff.index/metadata {:biff.index/version 0})
          (rocks-put ctx :bar "abc" 123)
          (is (= 123 (rocks-get ctx :bar "abc")))
          (finally
            (run! #(%) (reverse (:biff/stop ctx))))))
      (let [{:biff.index/keys [rocksdb
                               indexes
                               snapshot-data]
             :as ctx}
            (biff/use-indexes {:biff/modules (modules 0 1)
                               :biff.index/dir tmp-dir})]
        (try
          (is (= :there (rocks-get ctx :foo "hello")))
          (is (= nil (rocks-get ctx :bar "hello")))
          (finally
            (run! #(%) (reverse (:biff/stop ctx))))))
      (finally
        (close-tmp-dir!)))))
