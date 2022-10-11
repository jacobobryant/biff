(ns com.biffweb.impl.xtdb-test
  (:require [clojure.test :refer [deftest is]]
            [xtdb.api :as xt]
            [com.biffweb :as biff]
            [com.biffweb.impl.xtdb :as bxt]))

(defn test-node [& docs]
  (let [node (xt/start-node {})]
    (when (not-empty docs)
      (xt/await-tx
       node
       (xt/submit-tx node
         (vec
          (concat
           (for [d docs]
             [::xt/put (merge {:xt/id (random-uuid)}
                              d)])
           (for [[k f] bxt/tx-fns]
             [::xt/put {:xt/id k :xt/fn f}]))))))
    node))

(deftest ensure-unique
  (with-open [node (test-node {:foo "bar"})]
    (let [db (xt/db node)]
      (is (nil? (xt/with-tx
                  db
                  [[::xt/put {:xt/id (random-uuid)
                              :foo "bar"}]
                   [::xt/fn :biff/ensure-unique {:foo "bar"}]])))
      (is (some? (xt/with-tx
                   db
                   [[::xt/put {:xt/id (random-uuid)
                               :foo "baz"}]
                    [::xt/fn :biff/ensure-unique {:foo "bar"}]]))))))
