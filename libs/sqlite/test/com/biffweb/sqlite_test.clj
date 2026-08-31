(ns com.biffweb.sqlite-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [com.biffweb.graph :as biff.graph]
            [com.biffweb.sqlite :as sqlite]
            [com.biffweb.sqlite.impl.pool :as pool]
            [next.jdbc :as jdbc])
  (:import [java.nio.file Files]
           [java.time Instant]
           [java.util UUID]))

(def columns
  {:pet/id         {:type :uuid :primary-key true}
   :pet/species    {:type        :enum
                    :enum-values {0 :pet.species/iguana
                                  1 :pet.species/tardigrade}}
   :user/id        {:type :uuid :primary-key true}
   :user/email     {:type :text :unique true :required true}
   :user/score     {:type :int :required true :unique-with [:user/email]}
   :user/active    {:type :boolean}
   :user/joined-at {:type :inst :index true}
   :user/prefs     {:type :edn}
   :user/pet-id    {:type :uuid :ref :pet/id}})

(defn- temp-db-path []
  (str (Files/createTempFile "biff-sqlite-test" ".db"
                             (make-array
                              java.nio.file.attribute.FileAttribute 0))))

(defn- split-sql [sql]
  (->> (str/split sql #";")
       (map str/trim)
       (remove empty?)))

(defn- open-ctx
  ([]
   (open-ctx columns))
  ([columns]
   (let [ctx (pool/start {:biff.sqlite/db-path (temp-db-path)
                          :biff.sqlite/columns columns})]
     (doseq [statement (split-sql (sqlite/schema-sql ctx))]
       (sqlite/execute ctx statement))
     ctx)))

(defn- close-ctx [{:keys [biff.sqlite/read-pool biff.sqlite/write-conn]}]
  (.close write-conn)
  (.close read-pool))

(defn- with-ctx [f]
  (let [ctx (open-ctx)]
    (try
      (f ctx)
      (finally
        (close-ctx ctx)))))

(defn- thrown-data [f]
  (try
    (f)
    nil
    (catch clojure.lang.ExceptionInfo e
      (ex-data e))))

(deftest schema-sql-translates-columns-to-strict-sql
  (is (= (str "CREATE TABLE pet (\n"
              "  id BLOB PRIMARY KEY NOT NULL,\n"
              "  species INT CHECK (species IN (0, 1)) "
              "-- iguana (0), tardigrade (1)\n"
              ") STRICT;\n\n"
              "CREATE TABLE user (\n"
              "  id BLOB PRIMARY KEY NOT NULL,\n"
              "  email TEXT NOT NULL,\n"
              "  score INT NOT NULL,\n"
              "  active INT,\n"
              "  joined_at INT,\n"
              "  pet_id BLOB,\n"
              "  prefs BLOB,\n"
              "  FOREIGN KEY(pet_id) REFERENCES pet(id),\n"
              "  UNIQUE(email),\n"
              "  UNIQUE(score, email)\n"
              ") STRICT;\n\n"
              "CREATE INDEX idx_user_joined_at ON user(joined_at);")
         (sqlite/schema-sql {:biff.sqlite/columns columns}))))

(deftest execute-coerces-rich-values-on-write-and-read
  (with-ctx
    (fn [ctx]
      (let [pet-id    (UUID/randomUUID)
            user-id   (UUID/randomUUID)
            joined-at (Instant/parse "2024-02-03T04:05:06Z")
            prefs     {:theme :dark :alerts [:email :sms]}]
        (sqlite/execute ctx
                        {:insert-into :pet
                         :values      [{:pet/id pet-id

                                        :pet/species
                                        [:lift :pet.species/iguana]}]})
        (sqlite/execute ctx
                        {:insert-into :user
                         :values      [{:user/id        user-id
                                        :user/email     "ada@example.com"
                                        :user/score     42
                                        :user/active    true
                                        :user/joined-at joined-at
                                        :user/prefs     [:lift prefs]
                                        :user/pet-id    pet-id}]})
        (is (= [{:user/id        user-id
                 :user/email     "ada@example.com"
                 :user/score     42
                 :user/active    true
                 :user/joined-at joined-at
                 :user/prefs     prefs
                 :user/pet-id    pet-id}]
               (sqlite/execute ctx
                               {:select [:user/id
                                         :user/email
                                         :user/score
                                         :user/active
                                         :user/joined-at
                                         :user/prefs
                                         :user/pet-id]
                                :from   :user})))
        (is (= [{:pet/species :pet.species/iguana}]
               (sqlite/execute ctx
                               {:select [:pet/species]
                                :from   :pet})))
        (is (= [{:user/joined-at joined-at}]
               (sqlite/execute ctx
                               {:select [[[:max :user/joined-at]
                                          :user/joined-at]]
                                :from   :user})))))))

(deftest execute-validates-honeysql-writes-and-rejects-unknown-enums
  (with-ctx
    (fn [ctx]
      (is (= {:column :user/score
              :value  "not an int"
              :schema :int}
             (thrown-data
              #(sqlite/execute ctx
                               {:insert-into :user
                                :values      [{:user/id    (UUID/randomUUID)
                                               :user/email "ada@example.com"
                                               :user/score "not an int"}]}))))
      (is (= :pet.species/missing
             (:value
              (thrown-data
               #(sqlite/execute ctx
                                ["SELECT ? AS species"
                                 :pet.species/missing]))))))))

(deftest execute-tx-rolls-back-and-returns-results
  (with-ctx
    (fn [ctx]
      (let [id-a (UUID/randomUUID)
            id-b (UUID/randomUUID)]
        (is (= [[{:next.jdbc/update-count 1}]
                [{:next.jdbc/update-count 1}]]
               (sqlite/execute-tx
                ctx
                [{:insert-into :user
                  :values      [{:user/id    id-a
                                 :user/email "a@example.com"
                                 :user/score 1}]}
                 {:insert-into :user
                  :values      [{:user/id    id-b
                                 :user/email "b@example.com"
                                 :user/score 2}]}])))
        (is (thrown? Exception
                     (sqlite/execute-tx
                      ctx
                      [{:insert-into :user
                        :values      [{:user/id    (UUID/randomUUID)
                                       :user/email "c@example.com"
                                       :user/score 3}]}
                       "INSERT INTO missing_table VALUES (1)"])))
        (is (= []
               (sqlite/execute ctx
                               {:select [:user/id]
                                :from   :user
                                :where  [:= :user/email "c@example.com"]})))))))

(deftest execute-calls-on-tx-for-writes-only
  (with-ctx
    (fn [ctx]
      (let [calls (atom 0)
            ctx   (assoc ctx :biff.core/on-tx (fn [_] (swap! calls inc)))]
        (sqlite/execute ctx
                        {:select [:*]
                         :from   :user})
        (is (= 0 @calls))
        (sqlite/execute ctx
                        {:insert-into :user
                         :values      [{:user/id    (UUID/randomUUID)
                                        :user/email "ada@example.com"
                                        :user/score 1}]})
        (is (= 1 @calls))))))

(deftest authorized-write-generates-diffs-and-enforces-authorization
  (with-ctx
    (fn [base-ctx]
      (let [pet-id  (UUID/randomUUID)
            user-id (UUID/randomUUID)
            diffs   (atom [])
            on-tx   (atom 0)
            ctx     (assoc base-ctx
                           :biff.sqlite/authorize
                           (fn [auth-ctx diff]
                             (is (some? (:biff.sqlite/before-conn auth-ctx)))
                             (is (some? (:biff.sqlite/after-conn auth-ctx)))
                             (swap! diffs conj diff)
                             true)
                           :biff.core/on-tx
                           (fn [_] (swap! on-tx inc)))

            create-diff
            (sqlite/authorized-write
             ctx
             {:insert-into :user
              :values      [{:user/id    user-id
                             :user/email "ada@example.com"
                             :user/score 1}]})]
        (is (= [{:table  :user
                 :op     :create
                 :before nil
                 :after  {:user/id        user-id
                          :user/email     "ada@example.com"
                          :user/score     1
                          :user/active    nil
                          :user/joined-at nil
                          :user/prefs     nil
                          :user/pet-id    nil}}]
               create-diff))
        (sqlite/execute ctx
                        {:insert-into :pet
                         :values      [{:pet/id pet-id

                                        :pet/species
                                        [:lift :pet.species/tardigrade]}]})
        (is (= [{:table  :user
                 :op     :update
                 :before {:user/id        user-id
                          :user/email     "ada@example.com"
                          :user/score     1
                          :user/active    nil
                          :user/joined-at nil
                          :user/prefs     nil
                          :user/pet-id    nil}
                 :after  {:user/id        user-id
                          :user/email     "ada@example.com"
                          :user/score     2
                          :user/active    nil
                          :user/joined-at nil
                          :user/prefs     nil
                          :user/pet-id    pet-id}}]
               (sqlite/authorized-write
                ctx
                {:update :user
                 :set    {:user/score  2
                          :user/pet-id pet-id}
                 :where  [:= :user/id user-id]})))
        (is (= [{:table  :user
                 :op     :delete
                 :before {:user/id        user-id
                          :user/email     "ada@example.com"
                          :user/score     2
                          :user/active    nil
                          :user/joined-at nil
                          :user/prefs     nil
                          :user/pet-id    pet-id}
                 :after  nil}]
               (sqlite/authorized-write
                ctx
                {:delete-from :user
                 :where       [:= :user/id user-id]})))
        (is (= 4 @on-tx))
        (is (= 3 (count @diffs)))
        (let [rejecting-ctx (assoc base-ctx
                                   :biff.sqlite/authorize
                                   (constantly false))]
          (is (= [{:table  :user
                   :op     :create
                   :before nil
                   :after  {:user/id        user-id
                            :user/email     "ada@example.com"
                            :user/score     1
                            :user/active    nil
                            :user/joined-at nil
                            :user/prefs     nil
                            :user/pet-id    nil}}]
                 (:biff.sqlite/diff
                  (thrown-data
                   #(sqlite/authorized-write
                     rejecting-ctx
                     {:insert-into :user
                      :values      [{:user/id    user-id
                                     :user/email "ada@example.com"
                                     :user/score 1}]})))))
          (is (= []
                 (sqlite/execute base-ctx
                                 {:select [:user/id]
                                  :from   :user
                                  :where  [:= :user/id user-id]}))))))))

(deftest authorized-write-tx-merges-diffs-and-disallows-primary-key-updates
  (with-ctx
    (fn [ctx]
      (let [user-id (UUID/randomUUID)
            ctx     (assoc ctx :biff.sqlite/authorize (constantly true))]
        (is (= []
               (sqlite/authorized-write-tx
                ctx
                [{:insert-into :user
                  :values      [{:user/id    user-id
                                 :user/email "ada@example.com"
                                 :user/score 1}]}
                 {:update :user
                  :set    {:user/score 2}
                  :where  [:= :user/id user-id]}
                 {:delete-from :user
                  :where       [:= :user/id user-id]}])))
        (is (= {:primary-key :user/id}
               (thrown-data
                #(sqlite/authorized-write
                  ctx
                  {:update :user
                   :set    {:user/id (UUID/randomUUID)}
                   :where  [:= :user/id user-id]}))))))))

(deftest make-resolvers-builds-batch-resolvers-with-ref-joins
  (with-ctx
    (fn [ctx]
      (let [pet-id  (UUID/randomUUID)
            user-id (UUID/randomUUID)
            ctx     (merge (biff.graph/new-ctx (sqlite/make-resolvers columns))
                           ctx)]
        (sqlite/execute ctx
                        {:insert-into :pet
                         :values      [{:pet/id pet-id

                                        :pet/species
                                        [:lift :pet.species/iguana]}]})
        (sqlite/execute ctx
                        {:insert-into :user
                         :values      [{:user/id     user-id
                                        :user/email  "ada@example.com"
                                        :user/score  1
                                        :user/pet-id pet-id}]})
        (is (= {:user/email  "ada@example.com"
                :user/pet-id pet-id
                :user/pet    {:pet/id      pet-id
                              :pet/species :pet.species/iguana}}
               (biff.graph/query
                ctx
                {:user/id user-id}
                [:user/email
                 :user/pet-id
                 {:user/pet [:pet/id
                             :pet/species]}])))))))

(deftest module-provides-fx-kv-schema-and-wrap-db-snapshot
  (let [modules-var (atom [{:biff.sqlite/columns
                            {:app/id {:type :uuid :primary-key true}}}])
        module      (sqlite/module)
        init        ((:biff.core/init module) modules-var)]
    (is (= #{:biff.sqlite.fx/execute
             :biff.sqlite.fx/execute-tx
             :biff.sqlite.fx/authorized-write
             :biff.sqlite.fx/authorized-write-tx}
           (set (keys (:biff.fx/handlers module)))))
    (is (contains? (:biff.sqlite/columns module) :biff-sqlite-kv/id))
    (is (= {:app/id {:type :uuid :primary-key true}}
           (:biff.sqlite/columns init)))
    (is (not (contains? init :biff.core/kv-get)))
    (is (not (contains? init :biff.core/kv-set)))
    (is (not (contains? init :biff.core/kv-list)))
    (is (not (contains? init :biff.core/wrap-db-snapshot)))))

(deftest schema-module-provides-schema-authorization-and-resolvers
  (let [authorize (constantly true)
        columns   {:app/id   {:type :uuid :primary-key true}
                   :app/name {:type :text}}
        module    (sqlite/schema-module
                   {:biff.sqlite/authorize      authorize
                    :biff.sqlite/columns        columns
                    :biff.sqlite/extra-init-sql ["CREATE INDEX app_name"]})]
    (is (= columns (:biff.sqlite/columns module)))
    (is (= (mapv #(dissoc % :biff.graph/resolve-fn)
                 (sqlite/make-resolvers columns))
           (mapv #(dissoc % :biff.graph/resolve-fn)
                 (:biff.graph/resolvers module))))
    (is (= {:biff.sqlite/authorize      authorize
            :biff.sqlite/extra-init-sql ["CREATE INDEX app_name"]}
           ((:biff.core/init module) (atom []))))))

(deftest pool-adds-read-and-write-connections-with-pragmas
  (let [ctx (pool/start {:biff.sqlite/db-path (temp-db-path)})]
    (try
      (is (some? (:biff.sqlite/read-pool ctx)))
      (is (some? (:biff.sqlite/write-conn ctx)))
      (is (ifn? (:biff.core/kv-get ctx)))
      (is (ifn? (:biff.core/kv-set ctx)))
      (is (ifn? (:biff.core/kv-list ctx)))
      (is (ifn? (:biff.core/wrap-db-snapshot ctx)))
      (is (= [{:journal_mode "wal"}]
             (jdbc/execute! (:biff.sqlite/write-conn ctx)
                            ["PRAGMA journal_mode"])))
      (is (= [{:foreign_keys 1}]
             (jdbc/execute! (:biff.sqlite/write-conn ctx)
                            ["PRAGMA foreign_keys"])))
      (is (= [{:timeout 5000}]
             (jdbc/execute! (:biff.sqlite/write-conn ctx)
                            ["PRAGMA busy_timeout"])))
      (is (= [{:synchronous 1}]
             (jdbc/execute! (:biff.sqlite/write-conn ctx)
                            ["PRAGMA synchronous"])))
      (finally
        (close-ctx ctx)))))
