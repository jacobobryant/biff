(ns com.biffweb.xtdb-test
  (:require [clojure.test :refer [deftest is]]
            [com.biffweb.core :as biff.core]
            [com.biffweb.graph :as biff.graph]
            [com.biffweb.xtdb :as biff.xtdb])
  (:import [java.util UUID]))

(def columns
  {:pet/species {:schema :keyword}
   :user/email  {:schema :string}
   :user/score  {:schema :int}
   :user/pet-id {:schema :uuid
                 :ref    :pet/id}})

(defn- open-ctx
  ([]
   (open-ctx {}))
  ([initial-ctx]
   (let [modules-var (atom [(biff.xtdb/module)])
         init        ((:biff.core/init (biff.xtdb/module)) modules-var)]
     (biff.xtdb/use-xtdb (merge init
                                {:biff.core/stop    []
                                 :biff.xtdb/columns columns
                                 :biff.xtdb/log     :memory
                                 :biff.xtdb/storage :memory}
                                initial-ctx)))))

(defn- close-ctx [ctx]
  (doseq [stop-fn (:biff.core/stop ctx)]
    (stop-fn)))

(defmacro with-ctx [binding & body]
  `(let [~binding (open-ctx)]
     (try
       ~@body
       (finally
         (close-ctx ~binding)))))

(defn- thrown-data [f]
  (try
    (f)
    nil
    (catch clojure.lang.ExceptionInfo e
      (ex-data e))
    (catch AssertionError e
      (ex-data e))))

(deftest execute-tx-expands-custom-ops-and-validates-values
  (with-ctx ctx
    (biff.core/register (biff.xtdb/columns->schema columns))
    (let [id-a (UUID/randomUUID)
          id-b (UUID/randomUUID)]
      (biff.xtdb/execute-tx
       ctx
       [[:biff/upsert :user [:user/email]
         {:xt/id      id-a
          :user/email "a@example.com"
          :user/score 1}
         {:xt/id      id-b
          :user/email "b@example.com"
          :user/score 2}]])
      (is (= [{:xt/id      id-a
               :user/email "a@example.com"
               :user/score 1}
              {:xt/id      id-b
               :user/email "b@example.com"
               :user/score 2}]
             (sort-by :user/email
                      (biff.xtdb/q ctx {:select [:xt/id :user/email :user/score]
                                        :from   [:user]}))))
      (biff.xtdb/execute-tx
       ctx
       [[:biff/upsert :user [:user/email]
         {:user/email     "a@example.com"
          :user/score     3
          :biff/on-update {:user/score 4}}]])
      (is (= [{:user/score 4}]
             (biff.xtdb/q ctx {:select [:user/score]
                               :from   [:user]
                               :where  [:= :user/email "a@example.com"]})))
      (is (thrown?
           AssertionError
           (biff.xtdb/execute-tx
            ctx
            [[:put-docs :user
              {:xt/id      (UUID/randomUUID)
               :user/email "c@example.com"
               :user/score "bad"}]]))))))

(deftest kv-round-trips-values-and-lists-keys
  (with-ctx ctx
    ((:biff.core/kv-set ctx) ctx :com.example/cache "b" {:foo/bar 1})
    ((:biff.core/kv-set ctx) ctx :com.example/cache "a" [1 2 3])
    ((:biff.core/kv-set ctx) ctx :com.example/cache "aa" :x)
    (is (= {:foo/bar 1}
           ((:biff.core/kv-get ctx) ctx :com.example/cache "b")))
    (is (= ["a" "aa" "b"]
           ((:biff.core/kv-list ctx) ctx :com.example/cache)))
    (is (= ["a" "aa"]
           ((:biff.core/kv-list ctx) ctx :com.example/cache "a")))
    ((:biff.core/kv-set ctx) ctx :com.example/cache "b" nil)
    (is (nil? ((:biff.core/kv-get ctx) ctx :com.example/cache "b")))))

(deftest submit-tx-returns-before-indexing-and-calls-on-tx-afterward
  (let [called (promise)
        ctx    (open-ctx {:biff.core/on-tx
                          (fn [ctx]
                            (deliver called (:biff.xtdb/latest-system-time ctx)))})]
    (try
      (let [result (biff.xtdb/submit-tx
                    ctx
                    [[:put-docs :user
                      {:xt/id      (UUID/randomUUID)
                       :user/email "submit@example.com"
                       :user/score 1}]])]
        (is (contains? result :tx-id))
        (is (not (contains? result :system-time)))
        (is (inst? (deref called 10000 nil)))
        (is (= [{:user/email "submit@example.com"}]
               (biff.xtdb/q ctx {:select [:user/email]
                                 :from   [:user]
                                 :where  [:= :user/email "submit@example.com"]}))))
      (finally
        (close-ctx ctx)))))

(deftest graph-resolvers-use-table-specific-input-keys
  (with-ctx ctx
    (let [pet-id  (UUID/randomUUID)
          user-id (UUID/randomUUID)
          ctx     (merge (biff.graph/new-ctx (biff.xtdb/make-resolvers ctx))
                         ctx)]
      (biff.xtdb/execute-tx
       ctx
       [[:put-docs :pet
         {:xt/id       pet-id
          :pet/species :pet.species/iguana}]
        [:put-docs :user
         {:xt/id       user-id
          :user/email  "ada@example.com"
          :user/score  1
          :user/pet-id pet-id}]])
      (is (= {:user/email  "ada@example.com"
              :user/pet-id pet-id
              :user/pet    {:pet/id      pet-id
                            :pet/species :pet.species/iguana}}
             (biff.graph/query
              ctx
              {:user/id user-id}
              [:user/email
               :user/pet-id
               {:user/pet [:pet/id :pet/species]}]))))))

(deftest authorized-write-generates-diff-and-rejects-sql
  (with-ctx base-ctx
    (let [user-id (UUID/randomUUID)
          seen    (atom nil)
          ctx     (assoc base-ctx
                         :biff.xtdb/authorize
                         (fn [_ diff]
                           (reset! seen diff)
                           true))]
      (is (= [{:table  :user
               :op     :create
               :before nil
               :after  {:xt/id      user-id
                        :user/email "ada@example.com"
                        :user/score 1}}]
             (:biff.xtdb/diff
              (biff.xtdb/authorized-write
               ctx
               [[:put-docs :user
                 {:xt/id      user-id
                  :user/email "ada@example.com"
                  :user/score 1}]]))))
      (is (= @seen
             [{:table  :user
               :op     :create
               :before nil
               :after  {:xt/id      user-id
                        :user/email "ada@example.com"
                        :user/score 1}}]))
      (is (= [{:xt/id user-id}]
             (biff.xtdb/q ctx {:select [:xt/id]
                               :from   [:user]})))
      (is (= {:op [:sql "UPDATE user SET score = 2"]}
             (thrown-data
              #(biff.xtdb/authorized-write
                ctx
                [[:sql "UPDATE user SET score = 2"]])))))))

(deftest module-provides-fx-kv-columns-and-wrap-read-tx
  (let [modules-var (atom [{:biff.xtdb/columns {:app/name {:schema :string}}}])
        module      (biff.xtdb/module)
        init        ((:biff.core/init module) modules-var)]
    (is (= #{:biff.xtdb.fx/execute-tx
             :biff.xtdb.fx/submit-tx
             :biff.xtdb.fx/authorized-write}
           (set (keys (:biff.fx/handlers module)))))
    (is (not (contains? module :biff.xtdb/columns)))
    (is (not (contains? init :biff.xtdb/columns)))
    (is (ifn? (:biff.core/kv-get init)))
    (is (ifn? (:biff.core/kv-set init)))
    (is (ifn? (:biff.core/kv-list init)))
    (is (ifn? (:biff.core/wrap-read-tx init)))))
