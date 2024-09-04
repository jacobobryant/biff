(ns com.biffweb.impl.index-test
  (:require [clojure.test :refer [deftest is]]
            [com.biffweb :as biff]
            [com.biffweb.impl.index :as biff.index]
            [xtdb.api :as xt])
  (:import (org.rocksdb RocksDB ColumnFamilyHandle)))

(defn rocks-get [{:biff.index/keys [rocksdb indexes]} index-id _key]
  (biff.index/rocks-get rocksdb (get-in indexes [index-id :biff.index/handle]) _key))

(defn rocks-put [{:biff.index/keys [rocksdb indexes]} index-id _key value]
  (biff.index/rocks-put rocksdb (get-in indexes [index-id :biff.index/handle]) _key value))

(deftest use-indexes
  (let [modules (fn [foo-version bar-version]
                  (delay [{:indexes [{:id :foo
                                      :indexer (fn [{:biff.index/keys [doc op]}]
                                                 {"test" "foo"})
                                      :version foo-version}
                                     {:id :bar
                                      :indexer (fn [{:biff.index/keys [doc op]}]
                                                 {"test" "bar"})
                                      :version bar-version}]}]))]
    (let [{:biff.index/keys [rocksdb
                             indexes
                             tx-id->snapshot-data]
           :as ctx}
          (biff/use-indexes {:biff/modules (modules 0 0)})]
      (try
        (is (= #{:foo :bar} (set (keys indexes))))
        (is (every? #(instance? ColumnFamilyHandle (:biff.index/handle %)) (vals indexes)))
        (is (instance? RocksDB rocksdb))
        (is (instance? clojure.lang.Atom tx-id->snapshot-data))
        (rocks-put ctx :foo "hello" :there)
        (is (= :there (rocks-get ctx :foo "hello")))
        (is (= nil (rocks-get ctx :bar "hello")))
        (rocks-put ctx :foo :biff.index/metadata {:biff.index/version 0})
        (rocks-put ctx :bar :biff.index/metadata {:biff.index/version 0})
        (rocks-put ctx :bar "abc" 123)
        (finally
          (run! #(%) (reverse (:biff/stop ctx))))))
    (let [{:biff.index/keys [rocksdb
                             indexes
                             tx-id->snapshot-data]
           :as ctx}
          (biff/use-indexes {:biff/modules (modules 1 0)})]
      (try
        (is (= nil (rocks-get ctx :foo "hello")))
        (is (= 123 (rocks-get ctx :bar "abc")))
        (finally
          (run! #(%) (reverse (:biff/stop ctx))))))))
