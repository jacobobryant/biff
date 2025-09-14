(ns com.biffweb.impl.xtdb-test
  (:require [clojure.test :refer [deftest is]]
            [xtdb.api :as xt]
            [com.biffweb :as biff :refer [test-xtdb-node]]
            [com.biffweb.impl.xtdb :as impl]
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

(deftest ensure-unique
  (with-open [node (test-xtdb-node [{:foo "bar"}])]
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
  (with-open [node (test-xtdb-node [{:xt/id :id/foo
                                :foo "bar"}])]
    (is (= (biff/tx-xform-upsert
            {:biff/db (xt/db node)}
            [{:db/doc-type :user
              :db.op/upsert {:foo "bar"}
              :baz "quux"}])
           '({:db/doc-type :user,
              :baz "quux",
              :foo "bar",
              :db/op :merge,
              :xt/id :id/foo})))
    (is (= (biff/tx-xform-upsert
            {:biff/db (xt/db node)}
            [{:db/doc-type :user
              :db.op/upsert {:foo "eh"}
              :baz "quux"}])
           '({:db/doc-type :user,
              :baz "quux",
              :foo "eh",
              :db/op :merge}
             [:xtdb.api/fn :biff/ensure-unique {:foo "eh"}])))))

(deftest tx-unique
  (is (= (biff/tx-xform-unique
          nil
          [{:foo "bar"
            :baz [:db/unique "quux"]
            :spam [:db/unique "eggs"]}
           {:hello "there"}])
         '({:foo "bar", :baz "quux", :spam "eggs"}
           [:xtdb.api/fn :biff/ensure-unique {:baz "quux"}]
           [:xtdb.api/fn :biff/ensure-unique {:spam "eggs"}]
           {:hello "there"}))))

(deftest tx-tmp-ids
  (let [[{:keys [a b c]}
         {:keys [d]}] (biff/tx-xform-tmp-ids
                       nil
                       [{:a 1
                         :b :db.id/foo
                         :c :db.id/bar}
                        {:d :db.id/foo}])]
    (is (every? uuid? [b c d]))
    (is (= b d))
    (is (not= b c))))

(defn get-context [node]
  {:biff/db (xt/db node)
   :biff/now #inst "1970"
   :biff/malli-opts #'malli-opts})

(def test-docs [{:xt/id :user/alice
                 :user/email "alice@example.com"}
                {:xt/id :user/bob
                 :user/email "bob@example.com"}])

(deftest tx-default
  (with-open [node (test-xtdb-node (into test-docs
                                         [{:xt/id :user/carol
                                           :user/email "carol@example.com"
                                           :user/foo "x"}]))]
    (is (= (biff/biff-tx->xt
            (get-context node)
            [{:db/doc-type :user
              :db/op :update
              :xt/id :user/bob
              :user/foo [:db/default "default-value"]}
             {:db/doc-type :user
              :db/op :update
              :xt/id :user/carol
              :user/foo [:db/default "default-value"]}])
           '([:xtdb.api/match
              :user/bob
              {:user/email "bob@example.com", :xt/id :user/bob}]
             [:xtdb.api/put
              {:user/email "bob@example.com",
               :xt/id :user/bob,
               :user/foo "default-value"}]
             [:xtdb.api/match
              :user/carol
              {:user/email "carol@example.com", :user/foo "x", :xt/id :user/carol}]
             [:xtdb.api/put
              {:user/email "carol@example.com", :user/foo "x", :xt/id :user/carol}])))))

(deftest tx-all
  (with-open [node (test-xtdb-node test-docs)]
    (is (= (biff/biff-tx->xt
            (get-context node)
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

(deftest lookup
  (with-open [node (test-xtdb-node [{:xt/id :user/alice
                                     :user/email "alice@example.com"
                                     :user/foo "foo"}
                                    {:xt/id :user/bob
                                     :user/email "bob@example.com"
                                     :user/foo "foo"}
                                    {:xt/id :user/carol
                                     :user/email "bob@example.com"}
                                    {:xt/id :msg/a
                                     :msg/user :user/alice
                                     :msg/text "hello"
                                     :msg/sent-at #inst "1970"}
                                    {:xt/id :msg/b
                                     :msg/user :user/alice
                                     :msg/text "there"
                                     :msg/sent-at #inst "1971"}])]
    (let [db (xt/db node)]
      (is (= :user/alice (biff/lookup-id db :user/email "alice@example.com")))
      (is (= '(:user/alice :user/bob) (sort (biff/lookup-id-all db :user/foo "foo"))))
      (is (= {:user/email "alice@example.com", :user/foo "foo", :xt/id :user/alice}
             (biff/lookup db :user/email "alice@example.com")))
      (is (= '({:user/email "alice@example.com", :user/foo "foo", :xt/id :user/alice}
               {:user/email "bob@example.com", :user/foo "foo", :xt/id :user/bob})
             (sort-by :user/email (biff/lookup-all db :user/foo "foo"))))
      (is (= '{:user/email "alice@example.com",
               :user/foo "foo",
               :xt/id :user/alice,
               :user/messages
               ({:msg/user :user/alice,
                 :msg/text "hello",
                 :msg/sent-at #inst "1970-01-01T00:00:00.000-00:00",
                 :xt/id :msg/a}
                {:msg/user :user/alice,
                 :msg/text "there",
                 :msg/sent-at #inst "1971-01-01T00:00:00.000-00:00",
                 :xt/id :msg/b})}
             (-> (biff/lookup db
                              '[* {(:msg/_user {:as :user/messages}) [*]}]
                              :user/email
                              "alice@example.com")
                 (update :user/messages #(sort-by :msg/sent-at %)))))
      (is (#{:user/alice :user/bob} (biff/lookup-id db :user/foo "foo"))))))

(deftest apply-special-vals
  (is (= (impl/apply-special-vals {:a 1
                                   :b 2
                                   :d #{1 2 3 4}
                                   :e 5
                                   :g 8}
                                  {:b :db/dissoc
                                   :c [:db/union 3]
                                   :d [:db/difference 4]
                                   :e [:db/add 2]
                                   :f [:db/default 6]
                                   :g [:db/default 7]})
         {:a 1, :d #{1 3 2}, :e 7, :g 8, :c #{3}, :f 6})))
