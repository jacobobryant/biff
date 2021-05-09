(ns biff.crux-test
  (:require
    [biff.crux :as bc]
    [biff.util :as bu]
    [crux.api :as crux]
    [biff.misc :as misc]
    [clojure.test :refer [deftest is run-tests]]))

(defn submit-await-tx [node tx]
  (crux/await-tx
    node
    (crux/submit-tx
      node
      tx)))

(defn start-node [docs]
  (let [node (crux/start-node {})]
    (when (not-empty docs)
      (crux/await-tx
        node
        (crux/submit-tx
          node
          (for [d docs]
            [:crux.tx/put d]))))
    node))

(deftest test-foo
  (is (= 4 (+ 2 2))))

(defn authorize [{:keys [doc-type operation]} & _]
  (case [doc-type operation]
    [:foo :read] true
    [:bar :create] true
    false))

(def doc {:crux.db/id :foo})

(deftest check-read
  (is (some? (bc/check-read {} {:docs [doc]})))
  (with-redefs [bc/authorize (constantly true)]
    (is (nil? (bc/check-read {} {:docs [doc]}))))
  (with-redefs [bc/authorize authorize]
    (is (nil? (bc/check-read
                {}
                {:query {:doc-type :foo
                         :where []}
                 :docs [doc]})))))

(deftest check-write
  (is (some? (bc/check-write {} {:changes [{:after doc}]})))
  (with-redefs [bc/authorize (constantly true)]
    (is (nil? (bc/check-write {} {:changes [{:after doc}]}))))
  (with-redefs [bc/authorize authorize]
    (is (nil? (bc/check-write
                {}
                {:changes [{:after doc
                            :doc-type :bar}]})))))

(deftest normalize-tx-doc
  (is (= {:crux.db/id {:foo "bar"}
          :foo "bar"
          :yeeted-at #inst "1970"
          :numbers #{1 2 3}
          :ek-ek-ek #{:whoppa}
          :flavors #{"peach"}
          :hot-dogs 116
          :hello "there"}
         (bc/normalize-tx-doc
           {:doc-id {:foo "bar"}
            :tx-doc {:db/merge true
                     :yeeted-at :db/server-timestamp
                     :numbers [:db/union 1 2]
                     :ek-ek-ek [:db/union :whoppa]
                     :flavors [:db/difference "dirt"]
                     :zebra :db/remove
                     :hot-dogs [:db/add 50]}
            :server-timestamp #inst "1970"
            :before {:hello "there"
                     :numbers #{3}
                     :flavors #{"peach" "dirt"}
                     :zebra "kevin"
                     :hot-dogs 66}}))))

(deftest get-changes
  (let [random-ids [:x :y :z]
        expected [{:tx-item [[:foo :c] {:hello "there"}]
                   :doc-id :c
                   :doc-type :foo
                   :before nil
                   :after {:crux.db/id :c
                           :hello "there"}}
                  {:tx-item [[:foo] {:hello "there"
                                     :db/merge true}]
                   :doc-id :y
                   :doc-type :foo
                   :before {:crux.db/id :y
                            :a "b"}
                   :after {:crux.db/id :y
                           :hello "there"
                           :a "b"}}
                  {:tx-item [[:foo :a] nil]
                   :doc-id :a
                   :doc-type :foo
                   :before {:crux.db/id :a
                            :a "b"}
                   :after nil}]]
    (with-open [node (start-node
                       [{:crux.db/id :y :a "b"}
                        {:crux.db/id :a :a "b"}])]
      (is (= expected
             (bc/get-changes
               {:db (crux/db node)
                :random-uuids random-ids
                :biff-tx (map :tx-item expected)}))))))

(def registry
  {:user/id     :uuid
   :user/email  :string
   :user/foo    :string
   :user/bar    :string
   :user        [:map {:closed true}
                 [:crux.db/id :user/id]
                 :user/email
                 [:user/foo {:optional true}]
                 [:user/bar {:optional true}]]
   :msg/id      :uuid
   :msg/user    :user/id
   :msg/text    :string
   :msg/sent-at inst?
   :msg         [:map {:closed true}
                 [:crux.db/id :msg/id]
                 :msg/user
                 :msg/text
                 :msg/sent-at]})

(def schema (misc/map->MalliSchema
              {:doc-types [:user :msg]
               :malli-opts {:registry (misc/malli-registry registry)}}))

(deftest get-tx-info
  (is (thrown-with-msg?
        Exception #"TX doesn't match schema."
        (bc/get-tx-info nil {1 {:foo "bar"}})))
  (with-open [node (start-node [])]
    (is (some? (bc/get-tx-info
                 {:biff/schema schema
                  :biff.crux/db (delay (crux/db node))}
                 {}))))
  (with-open [node (start-node [])]
    (is (thrown-with-msg?
          Exception #"Attempted to update on a new doc."
          (bc/get-tx-info
            {:biff/schema schema
             :biff.crux/db (delay (crux/db node))}
            {[:user] {:db/update true}}))))
  (with-open [node (start-node [])]
    (is (thrown-with-msg?
          Exception #"Doc doesn't match doc-type."
          (bc/get-tx-info
            {:biff/schema schema
             :biff.crux/db (delay (crux/db node))}
            {[:user #uuid "0b5377f2-126d-44c9-b73c-d90f0efd0d7c"]
             {:hello "there"}}))))
  (with-open [node (start-node [])]
    (let [uid (java.util.UUID/randomUUID)]
      (is (= (dissoc (bc/get-tx-info
                       {:biff/schema schema
                        :biff.crux/db (delay (crux/db node))}
                       {[:user uid] {:user/email "username@example.com"}})
                     :db-before :db-after :server-timestamp)
             {:changes [{:tx-item [[:user uid] #:user{:email "username@example.com"}],
                         :doc-id uid
                         :doc-type :user,
                         :before nil,
                         :after {:user/email "username@example.com",
                          :crux.db/id uid}}],
              :crux-tx [[:crux.tx/match uid nil]
                        [:crux.tx/put {:user/email "username@example.com",
                                       :crux.db/id uid}]]})))))

(deftest subscription+updates
  (with-open [node (start-node [{:crux.db/id :foo
                                 :a 1}])]
    (is (= (#'bc/subscription+updates
             {:txes [{:crux.tx.event/tx-events [[nil :foo]]}]
              :db-before (crux/db node)
              :db-after (crux/with-tx (crux/db node)
                          [[:crux.tx/put {:crux.db/id :foo
                                          :a 2}]])
              :subscriptions #{{:query {:id :foo
                                        :doc-type :x}}
                               {:query {:where [[:a 1]]
                                        :doc-type :x}}
                               {:query {:where '[[:a a]
                                                 [(<= a 2)]]
                                        :doc-type :x}}
                               {:query {:id :bar
                                        :doc-type :x}}}})
           '([{:query {:id :foo,
                       :doc-type :x}}
              {[:x :foo] {:crux.db/id :foo,
                          :a 2}}]
             [{:query {:where [[:a 1]],
                       :doc-type :x}}
              {[:x :foo] nil}]
             [{:query {:where [[:a a]
                               [(<= a 2)]],
                       :doc-type :x}}
              {[:x :foo] {:crux.db/id :foo,
                          :a 2}}])))))

(deftest biff-q
  (with-open [node (start-node [{:crux.db/id :foo
                                 :a 1}])]
    (with-redefs [bc/check-read (constantly nil)]
      (is (= (bc/biff-q {:biff.crux/db (delay (crux/db node))}
                        {:doc-type :x
                         :id :foo})
             {[:x :foo] {:crux.db/id :foo, :a 1}}))
      (is (= (bc/biff-q {:biff.crux/db (delay (crux/db node))}
                        {:doc-type :x
                         :where [[:a]]})
             {[:x :foo] {:crux.db/id :foo, :a 1}}))
      (is (= (bc/biff-q {:biff.crux/db (delay (crux/db node))}
                        {:doc-type :x
                         :where '[[:a a]
                                  [(== a 1)]]})
             {[:x :foo] {:crux.db/id :foo, :a 1}}))
      (is (= (bc/biff-q {:biff.crux/db (delay (crux/db node))}
                        {:doc-type :x
                         :where [[:b]]})
             {}))
      (is (thrown-with-msg?
            Exception #"fn in query not authorized."
            (bc/biff-q {:biff.crux/db (delay (crux/db node))}
                       {:doc-type :x
                        :where '[[:a 1]
                                 [(foo)]]}))))))

#_(run-tests)
