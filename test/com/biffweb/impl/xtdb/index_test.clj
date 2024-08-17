(ns com.biffweb.impl.xtdb.index-test
  (:require [clojure.test :refer [deftest is]]
            [com.biffweb :as biff :refer [<<-]]
            [xtdb.api :as xt]))

(deftest indexer-args
  (is (= (<<- (with-open [node (xt/start-node {})])
              (let [txes (mapv #(xt/submit-tx node %)
                               ;; TODO add an ::xt/fn op
                               [[[::xt/put {:xt/id :foo :a "b"}]]
                                [[::xt/delete :foo]
                                 [::xt/put {:xt/id :bar :c "d"}]]])])
              (do (xt/sync node))
              (with-open [log (xt/open-tx-log node nil true)])
              (->> (iterator-seq log)
                   (mapv #(biff/indexer-args node %))))
         [[#:biff.index{:op :xtdb.api/put, :doc {:a "b", :xt/id :foo}}]
          [#:biff.index{:op :xtdb.api/delete, :doc {:a "b", :xt/id :foo}}
           #:biff.index{:op :xtdb.api/put, :doc {:c "d", :xt/id :bar}}]])))

(defn indexer [{:biff.index/keys [op doc get-doc]}]
  (concat [[::xt/put {:xt/id :n-docs
                      :value ((case op
                                ::xt/put inc
                                ::xt/delete dec)
                              (:value (get-doc :n-docs) 0))}]]
          (when (= op ::xt/delete)
            [[::xt/put {:xt/id :deleted-doc
                        :doc doc}]])))

(deftest index-deleted-doc
  (let [{:keys [biff.xtdb/node
                biff/indexes]
         [stop-fn] :biff/stop
         :as ctx} (biff/use-xt {:biff.xtdb/topology :memory
                                :biff.index/topology :memory
                                :biff/modules (delay
                                               [{:indexes [{:id :my-index
                                                            :indexer indexer
                                                            :version 0}]}])})]
    (try
      (xt/await-tx node (xt/submit-tx node [[::xt/put {:xt/id :foo :message "hello"}]]))
      (xt/await-tx node (xt/submit-tx node [[::xt/delete :foo]]))
      (let [db (:my-index (biff/index-snapshots ctx))]
        (is (= 0 (:value (xt/entity db :n-docs))))
        (is (= {:xt/id :foo :message "hello"} (:doc (xt/entity db :deleted-doc)))))

      (xt/await-tx node (xt/submit-tx node [[::xt/put {:xt/id :bar :message "hello"}]
                                            [::xt/delete :bar]]))
      (let [db (:my-index (biff/index-snapshots ctx))]
        (is (= 0 (:value (xt/entity db :n-docs))))
        (is (= {:xt/id :bar :message "hello"} (:doc (xt/entity db :deleted-doc)))))

      (finally
        (stop-fn)))))
