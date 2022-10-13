(ns com.biffweb.impl.xtdb-test
  (:require [clojure.test :refer [deftest is]]
            [xtdb.api :as xt]
            [com.biffweb :as biff]
            [com.biffweb.impl.xtdb :as bxt]
            [malli.core :as malc]
            [malli.registry :as malr]))

(def schema
  {:user/id :keyword
   :user/email :string
   :user/foo :string
   :user/bar :string
   :user [:map {:closed true}
          [:xt/id :user/id]
          :user/email
          [:user/foo {:optional true}]
          [:user/bar {:optional true}]]

   :msg/id :keyword
   :msg/user :user/id
   :msg/text :string
   :msg/sent-at inst?
   :msg [:map {:closed true}
         [:xt/id :msg/id]
         :msg/user
         :msg/text
         :msg/sent-at]})

(def malli-opts {:registry (malr/composite-registry malc/default-registry schema)})

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

(deftest tx-upsert
  (with-open [node (test-node {:xt/id :id/foo
                               :foo "bar"})]
    (is (= (bxt/tx-xform-upsert
            {:biff/db (xt/db node)}
            [{:db/doc-type :user
              :db.op/upsert {:foo "bar"}
              :baz "quux"}])
           '({:db/doc-type :user,
              :baz "quux",
              :foo "bar",
              :db/op :merge,
              :xt/id :id/foo})))
    (is (= (bxt/tx-xform-upsert
            {:biff/db (xt/db node)}
            [{:db/doc-type :user
              :db.op/upsert {:foo "eh"}
              :baz "quux"}])
           '({:db/doc-type :user,
              :baz "quux",
              :foo "eh",
              :db/op :merge,
              :xt/id nil}
             [:xtdb.api/fn :biff/ensure-unique {:foo "eh"}])))))

(deftest tx-unique
  (is (= (bxt/tx-xform-unique
          nil
          [{:foo "bar"
            :baz [:db/unique "quux"]
            :spam [:db/unique "eggs"]}
           {:hello "there"}])
         '({:foo "bar", :baz "quux", :spam "eggs"}
           [:xtdb.api/fn :biff/ensure-unique {:baz "quux"}]
           [:xtdb.api/fn :biff/ensure-unique {:spam "eggs"}]
           {:hello "there"}))))

(defn get-sys [node]
  {:biff/db (xt/db node)
   :biff/now #inst "1970"
   :biff/malli-opts #'malli-opts})

(deftest tx-all
  (with-open [node (test-node {:xt/id :user/alice
                               :user/email "alice@example.com"}
                              {:xt/id :user/bob
                               :user/email "bob@example.com"})]
    (is (= (bxt/biff-tx->xt
            (get-sys node)
            [{:db/doc-type :user
              :db.op/upsert {:user/email "alice@example.com"}
              :user/foo "bar"}
             {:db/doc-type :user
              :db/op :update
              :xt/id :user/bob
              :user/bar "baz"}])
           '([:xtdb.api/match
              :user/alice
              {:user/email "alice@example.com", :xt/id :user/alice}]
             [:xtdb.api/put
              {:user/email "alice@example.com",
               :xt/id :user/alice,
               :user/foo "bar"}]
             [:xtdb.api/match
              :user/bob
              {:user/email "bob@example.com", :xt/id :user/bob}]
             [:xtdb.api/put
              {:user/email "bob@example.com", :xt/id :user/bob, :user/bar "baz"}])))))
