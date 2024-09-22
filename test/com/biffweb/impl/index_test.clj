(ns com.biffweb.impl.index-test
  (:require [clojure.test :refer [deftest is]]
            [clojure.java.io :as io]
            [com.biffweb :as biff]
            [com.biffweb.impl.index :as biff.index]
            [clojure.tools.logging :as log]
            [xtdb.api :as xt])
  (:import (org.rocksdb RocksDB ColumnFamilyHandle)
           (java.nio.file Files Paths)
           (java.nio.file.attribute FileAttribute)))

(defn rocks-get [{:biff.index/keys [rocksdb indexes]} index-id _key]
  (biff.index/rocks-get rocksdb (get-in indexes [index-id :biff.index/handle]) _key))

(defn rocks-put [{:biff.index/keys [rocksdb indexes]} index-id _key value]
  (biff.index/rocks-put rocksdb (get-in indexes [index-id :biff.index/handle]) _key value))

(defn indexer [{:biff.index/keys [doc op index-get]}]
  {(:xt/id doc) (when (= ::xt/put op)
                  doc)
   :n-docs ((if (= ::xt/put op)
              inc
              dec)
            (or (index-get :n-docs) 0))})

(defn modules [foo-version bar-version & {:keys [prepare]}]
  (delay [{:indexes [{:id :foo
                      :indexer indexer
                      :version foo-version
                      :prepare prepare}
                     {:id :bar
                      :indexer indexer
                      :version bar-version
                      :prepare prepare}]}]))

(defrecord TempDir [path]
  java.io.Closeable
  (close [_]
    (run! io/delete-file (reverse (file-seq (io/file path))))))

;; make this inherit from File or something?
(defn make-temp-dir []
  (let [tmp-dir (str (Files/createTempDirectory "biff-test" (into-array FileAttribute [])))]
    (TempDir. tmp-dir)))

;; todo remove
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

(defrecord BiffSystem []
  java.io.Closeable
  (close [this]
    (doseq [f (:biff/stop this)]
      (log/info "stopping:" (str f))
      (f))))

(defn start! [system components]
  (map->BiffSystem
   (reduce (fn [system component]
             (log/info "starting:" (str component))
             (component system))
           system
           components)))

(defn submit-await [node tx]
  (xt/await-tx node (xt/submit-tx node tx)))

(deftest ->xtdb-index
  (with-open [system (start! {:biff.xtdb/topology :memory
                              :biff.index/dir :tmp
                              :biff/modules (modules 0 0)}
                             [biff/use-xtdb])]
    (let [{:keys [biff.xtdb/node biff.index/snapshot-data]} system]
      (submit-await node [[::xt/put {:xt/id :a}]])
      (is (= 1 (rocks-get system :foo :n-docs)))
      (is (= 1 (rocks-get system :bar :n-docs)))
      (is (= {:biff.index/version 0
              ::xt/tx-id 0}
             (rocks-get system :foo :biff.index/metadata)))
      (is (= 0 (:latest-snapshotted-tx-id @snapshot-data)))
      (is (= 0 (:latest-tx-id @snapshot-data)))
      (is (= #{:snapshot :read-options :n-clients}
             (set (keys (get @snapshot-data 0)))))
      (submit-await node [[::xt/put {:xt/id :b}]])
      (is (= 1 (:latest-snapshotted-tx-id @snapshot-data)))
      (is (= 1 (:latest-tx-id @snapshot-data)))
      (is (= #{:latest-snapshotted-tx-id
               :latest-tx-id
               1}
             (set (keys @snapshot-data))))
      (is (= {:xt/id :a} (rocks-get system :foo :a)))
      (submit-await node [[::xt/delete :a]])
      (is (= nil (rocks-get system :foo :a))))))

(defn snapshotted-tx-ids [snapshot-data]
  (set (keys (dissoc snapshot-data :latest-tx-id :latest-snapshotted-tx-id))))

(deftest open-db-with-index
  (with-open [system (start! {:biff.xtdb/topology :memory
                              :biff.index/dir :tmp
                              :biff/modules (modules 0 0)}
                             [biff/use-xtdb])]
    (let [{:keys [biff.xtdb/node biff.index/snapshot-data]} system]
      (submit-await node [[::xt/put {:xt/id :a :value 1}]])
      (with-open [db (biff/open-db-with-index system)]
        (is (= 1 (get-in @snapshot-data [0 :n-clients])))
        (with-open [db (biff/open-db-with-index system)]
          (is (= 2 (get-in @snapshot-data [0 :n-clients]))))
        (is (= 1 (get-in @snapshot-data [0 :n-clients])))

        (is (= {:xt/id :a :value 1} (xt/entity db :a)))
        (is (= 1 (biff/index-get db :foo :n-docs)))
        (submit-await node [[::xt/put {:xt/id :a :value 2}]])
        (is (= {:xt/id :a :value 1} (xt/entity db :a)))
        (is (= 1 (biff/index-get db :foo :n-docs)))
        (is (= #{0 1} (snapshotted-tx-ids @snapshot-data)))

        (with-open [db (biff/open-db-with-index system)]
          (is (= {:xt/id :a :value 2} (xt/entity db :a)))
          (is (= 2 (biff/index-get db :foo :n-docs))))
        (is (= #{0 1} (snapshotted-tx-ids @snapshot-data))))
      (is (= #{1} (snapshotted-tx-ids @snapshot-data)))
      (submit-await node [[::xt/put {:xt/id :a :value 3}]])
      (is (= #{2} (snapshotted-tx-ids @snapshot-data))))))

(deftest index-get-many
  (with-open [system (start! {:biff.xtdb/topology :memory
                              :biff.index/dir :tmp
                              :biff/modules (modules 0 0)}
                             [biff/use-xtdb])]
    (let [{:keys [biff.xtdb/node]} system]
      (submit-await node [[::xt/put {:xt/id :a :value 1}]])
      (submit-await node [[::xt/put {:xt/id :b :value 2}]])
      (with-open [db (biff/open-db-with-index system)]
        (is (= [{:xt/id :a :value 1}
                {:xt/id :b :value 2}
                2]
               (biff/index-get-many db :foo [:a :b :n-docs])))
        (is (= [{:xt/id :a :value 1}
                {:xt/id :a :value 1}]
               (biff/index-get-many db [[:foo :a] [:bar :a]])))))))

(deftest prepare-indexes!
  (with-open [dir (make-temp-dir)]
    (with-open [system (start! {:biff.xtdb/topology :standalone
                                :biff.xtdb/dir (:path dir)}
                               [biff/use-xtdb])]

      (let [{:keys [biff.xtdb/node]} system]
        (submit-await node [[::xt/put {:xt/id :a :value 1}]])
        (submit-await node [[::xt/put {:xt/id :b :value 2}]])))
    (with-open [system (start! {:biff.xtdb/topology :standalone
                                :biff.xtdb/dir (:path dir)
                                :biff.index/dir :tmp
                                :biff.index/sync-prepare true
                                :biff/modules (modules 0 0 {:prepare true})}
                               [biff/use-xtdb])
                db (biff/open-db-with-index system)]
      (is (= [{:xt/id :a :value 1}
              {:xt/id :b :value 2}
              2]
             (biff/index-get-many db :foo [:a :b :n-docs])))
      (is (= [{:xt/id :a :value 1}
              {:xt/id :b :value 2}
              2]
             (biff/index-get-many db :bar [:a :b :n-docs]))))))

(deftest indexer-input
  (with-open [node (xt/start-node {})]
    (xt/submit-tx node [[::xt/put {:xt/id :a}]
                        [::xt/put {:xt/id :b}]])
    (xt/submit-tx node [[::xt/delete :a]
                        [::xt/delete :non-existent-key]])
    (xt/sync node)
    (is (= [#:biff.index{:op :xtdb.api/put, :doc #:xt{:id :a}}
            #:biff.index{:op :xtdb.api/put, :doc #:xt{:id :b}}
            #:biff.index{:op :xtdb.api/delete, :doc #:xt{:id :a}}]
           (mapcat :biff.index/args (biff/indexer-input node nil nil))))))

(deftest indexer-results
  (with-open [node (xt/start-node {})]
    (xt/submit-tx node [[::xt/put {:xt/id :a}]
                        [::xt/put {:xt/id :b}]])
    (xt/submit-tx node [[::xt/delete :a]
                        [::xt/delete :non-existent-key]])
    (xt/sync node)
    (let [{:keys [results changes txes-processed]}
          (biff/indexer-results indexer (biff/indexer-input node nil nil))]
      (is (= 2 txes-processed))
      (is (= {:a nil, :n-docs 1, :b #:xt{:id :b}} changes))
      (is (= 2 (count results))))))
