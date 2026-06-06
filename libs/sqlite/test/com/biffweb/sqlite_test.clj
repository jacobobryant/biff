(ns com.biffweb.sqlite-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [com.biffweb.graph :as biff.graph]
   [com.biffweb.sqlite :as biff.sqlite]
   [com.biffweb.sqlite.impl.coerce :as coerce]
   [com.biffweb.sqlite.impl.query :as query]
   [com.biffweb.sqlite.impl.schema :as schema]
   [com.biffweb.sqlite.impl.util :as util]
   [honey.sql :as hsql]
   [next.jdbc :as jdbc]
   [taoensso.nippy :as nippy])
  (:import
   [java.time Instant]
   [java.util UUID]))

;; --- Test column definitions (new map format) ---

(def test-columns
  {:user/id        {:type :text :primary-key true}
   :user/name      {:type :text :required true}
   :user/joined-at {:type :inst :required true}})

(def ^:dynamic *conn* nil)
(def ^:dynamic *read-pool* nil)
(def ^:dynamic *write-conn* nil)

(defn with-sqlite [f]
  (let [db-file (java.io.File/createTempFile "biff-sqlite-test" ".db")
        db-path (.getAbsolutePath db-file)]
    (.delete db-file)
    (try
      (with-open [write-conn (jdbc/get-connection (str "jdbc:sqlite:" db-path))
                  read-conn  (jdbc/get-connection (str "jdbc:sqlite:" db-path))]
        (jdbc/execute! write-conn ["PRAGMA journal_mode=WAL"])
        (jdbc/execute! read-conn ["PRAGMA journal_mode=WAL"])
        (jdbc/execute! write-conn ["CREATE TABLE user (id TEXT PRIMARY KEY, name TEXT NOT NULL, joined_at INT NOT NULL) STRICT"])
        (jdbc/execute! write-conn ["INSERT INTO user (id, name, joined_at) VALUES (?, ?, ?)"
                                   "u1" "Alice" 1700000000000])
        (binding [*conn*       write-conn
                  *write-conn* write-conn
                  *read-pool*  read-conn]
          (f)))
      (finally
        (.delete (java.io.File. db-path))
        (.delete (java.io.File. (str db-path "-wal")))
        (.delete (java.io.File. (str db-path "-shm")))))))

(use-fixtures :each with-sqlite)

(deftest direct-column-coercion-test
  (let [ctx {:biff.sqlite/read-pool *read-pool*  :biff.sqlite/write-conn *write-conn*
             :biff.sqlite/columns   test-columns}]
    (testing "direct column name matches coercion"
      (is (= [{:user/id "u1" :user/name "Alice" :user/joined-at (Instant/ofEpochMilli 1700000000000)}]
             (biff.sqlite/execute ctx ["SELECT id, name, joined_at FROM user"]))))

    (testing "non-SELECT statement does not fail"
      (is (= [{:next.jdbc/update-count 0}]
             (biff.sqlite/execute ctx ["DELETE FROM user WHERE id = ?" "nonexistent"]))))))

(deftest handle-execute-test
  (let [ctx {:biff.sqlite/read-pool  *read-pool*
             :biff.sqlite/write-conn *write-conn*
             :biff.sqlite/columns    test-columns}]
    (is (= [{:user/id        "u1"
             :user/name      "Alice"
             :user/joined-at (Instant/ofEpochMilli 1700000000000)}]
           ((:biff.sqlite.fx/execute biff.sqlite/fx-handlers)
            ctx
            ["SELECT id, name, joined_at FROM user"])))))

(deftest handle-authorized-write-test
  (let [ctx       {:biff.sqlite/read-pool  *read-pool*
                   :biff.sqlite/write-conn *write-conn*
                   :biff.sqlite/columns    test-columns
                   :biff.sqlite/authorize  (constantly true)}
        joined-at (Instant/ofEpochMilli 1700000001000)
        diff      ((:biff.sqlite.fx/authorized-write biff.sqlite/fx-handlers)
                   ctx
                   {:insert-into :user
                    :values      [{:user/id        "u2"
                                   :user/name      "Bob"
                                   :user/joined-at joined-at}]})]
    (is (= :create (:op (first diff))))
    (is (= [{:user/name "Bob"}]
           (biff.sqlite/execute ctx
                                {:select [:user/name]
                                 :from   :user
                                 :where  [:= :user/id "u2"]})))))

(deftest module-exposes-fx-handlers
  (let [module      (biff.sqlite/module)
        on-tx-calls (atom [])
        modules-var (atom [{:biff.db/on-tx (fn [ctx] (swap! on-tx-calls conj [:a ctx]))}
                           {:biff.db/on-tx (fn [ctx] (swap! on-tx-calls conj [:b ctx]))}])
        init        ((:biff.core/init module) modules-var)]
    (is (= biff.sqlite/fx-handlers (:biff.fx/handlers module)))
    (is (fn? (:biff.core/init module)))
    (is (fn? (:biff.db/get-kv init)))
    (is (fn? (:biff.db/list-kv init)))
    (is (fn? (:biff.db/set-kv init)))
    ((:biff.db/on-tx init) {:demo true})
    (is (= [[:a {:demo true}] [:b {:demo true}]]
           @on-tx-calls))
    (reset! on-tx-calls [])
    (swap! modules-var conj {:biff.db/on-tx (fn [ctx] (swap! on-tx-calls conj [:c ctx]))})
    ((:biff.db/on-tx init) {:demo :updated})
    (is (= [[:a {:demo :updated}] [:b {:demo :updated}] [:c {:demo :updated}]]
           @on-tx-calls))))

;; --- Schema SQL generation tests ---

(deftest schema-sql-basic-test
  (testing "generates CREATE TABLE with correct types and constraints"
    (let [columns {:widget/id     {:type :uuid :primary-key true}
                   :widget/label  {:type :text :required true}
                   :widget/count  {:type :int :required true}
                   :widget/score  {:type :real :required true}
                   :widget/active {:type :boolean :required true}}
          sql     (schema/generate-schema-sql (util/normalize-columns columns) [])]
      (is (str/includes? sql "CREATE TABLE widget"))
      (is (str/includes? sql "id BLOB PRIMARY KEY NOT NULL"))
      (is (str/includes? sql "label TEXT NOT NULL"))
      (is (str/includes? sql "count INT NOT NULL"))
      (is (str/includes? sql "score REAL NOT NULL"))
      (is (str/includes? sql "active INT NOT NULL"))
      (is (str/includes? sql "STRICT;")))))

(deftest schema-sql-optional-test
  (testing "optional columns omit NOT NULL"
    (let [columns {:item/id   {:type :text :primary-key true}
                   :item/note {:type :text}}
          sql     (schema/generate-schema-sql (util/normalize-columns columns) [])]
      (is (str/includes? sql "id TEXT PRIMARY KEY NOT NULL"))
      (is (re-find #"note TEXT\b" sql))
      (is (not (str/includes? sql "note TEXT NOT NULL"))))))

(deftest schema-sql-enum-test
  (testing "enum columns get CHECK constraints"
    (let [columns {:task/id     {:type :text :primary-key true}
                   :task/status {:type        :enum                   :required true
                                 :enum-values {0 :task.status/pending
                                               1 :task.status/done}}}
          sql     (schema/generate-schema-sql (util/normalize-columns columns) [])]
      (is (str/includes? sql "status INT NOT NULL CHECK"))
      (is (str/includes? sql "IN (0, 1)")))))

(deftest schema-sql-unique-constraints-test
  (testing "unique constraints from :unique-with"
    (let [columns {:membership/id       {:type :text :primary-key true}
                   :membership/user-id  {:type        :text                  :required true
                                         :unique-with [:membership/group-id]}
                   :membership/group-id {:type :text :required true}}
          sql     (schema/generate-schema-sql (util/normalize-columns columns) [])]
      (is (str/includes? sql "UNIQUE(user_id, group_id)")))))

(deftest schema-sql-unique-column-test
  (testing "unique constraint from :unique true"
    (let [columns {:user/id    {:type :uuid :primary-key true}
                   :user/email {:type :text :unique true :required true}}
          sql     (schema/generate-schema-sql (util/normalize-columns columns) [])]
      (is (str/includes? sql "UNIQUE(email)")))))

(deftest schema-sql-foreign-key-test
  (testing "foreign key constraints from :ref"
    (let [columns {:account/id     {:type :text :primary-key true}
                   :post/id        {:type :text :primary-key true}
                   :post/author-id {:type :text :required true :ref :account/id}}
          sql     (schema/generate-schema-sql (util/normalize-columns columns) [])]
      (is (str/includes? sql "FOREIGN KEY(author_id) REFERENCES account(id)")))))

(deftest schema-sql-index-test
  (testing "index generation from :index true"
    (let [columns {:item/id      {:type :uuid :primary-key true}
                   :item/user-id {:type :uuid :required true :index true}}
          sql     (schema/generate-schema-sql (util/normalize-columns columns) [])]
      (is (str/includes? sql "CREATE INDEX idx_item_user_id ON item(user_id)")))))

(deftest schema-sql-edn-type-test
  (testing "edn type generates BLOB column"
    (let [columns {:user/id          {:type :uuid :primary-key true}
                   :user/digest-days {:type :edn}}
          sql     (schema/generate-schema-sql (util/normalize-columns columns) [])]
      (is (str/includes? sql "digest_days BLOB")))))

(deftest schema-sql-blob-type-test
  (testing "blob type generates BLOB column"
    (let [columns {:asset/id   {:type :uuid :primary-key true}
                   :asset/data {:type :blob}}
          sql     (schema/generate-schema-sql (util/normalize-columns columns) [])]
      (is (str/includes? sql "data BLOB")))))

(deftest schema-sql-topo-sort-test
  (testing "tables are ordered by foreign key dependencies"
    (let [columns {:post/id        {:type :uuid :primary-key true}
                   :post/author-id {:type :uuid :required true :ref :user/id}
                   :user/id        {:type :uuid :primary-key true}}
          sql     (schema/generate-schema-sql (util/normalize-columns columns) [])]
      (is (< (str/index-of sql "CREATE TABLE user")
             (str/index-of sql "CREATE TABLE post"))))))

(deftest primary-key-implies-required-test
  (testing ":primary-key true implies :required true in schema output"
    (let [columns {:item/id {:type :uuid :primary-key true}}
          sql     (schema/generate-schema-sql (util/normalize-columns columns) [])]
      (is (str/includes? sql "id BLOB PRIMARY KEY NOT NULL")))))

(deftest schema-sql-column-sort-order-test
  (testing "columns are sorted: primary key first, then required (alpha), then optional (alpha)"
    (let [columns       {:widget/id        {:type :uuid :primary-key true}
                         :widget/zebra     {:type :text :required true}
                         :widget/alpha     {:type :text :required true}
                         :widget/zoptional {:type :text}
                         :widget/beta      {:type :int}}
          sql           (schema/generate-schema-sql (util/normalize-columns columns) [])
          id-pos        (str/index-of sql "id BLOB PRIMARY KEY")
          alpha-pos     (str/index-of sql "alpha TEXT NOT NULL")
          zebra-pos     (str/index-of sql "zebra TEXT NOT NULL")
          beta-pos      (str/index-of sql "beta INT")
          zoptional-pos (str/index-of sql "zoptional TEXT")]
      (is (some? id-pos) "primary key column should be present")
      (is (some? alpha-pos) "required column alpha should be present")
      (is (some? zebra-pos) "required column zebra should be present")
      (is (some? beta-pos) "optional column beta should be present")
      (is (some? zoptional-pos) "optional column zoptional should be present")
      ;; Primary key comes first
      (is (< id-pos alpha-pos) "primary key before required columns")
      (is (< id-pos zebra-pos) "primary key before required columns")
      ;; Required columns come before optional columns
      (is (< alpha-pos beta-pos) "required before optional")
      (is (< zebra-pos beta-pos) "required before optional")
      ;; Required columns are sorted alphabetically
      (is (< alpha-pos zebra-pos) "required columns sorted alphabetically")
      ;; Optional columns are sorted alphabetically
      (is (< beta-pos zoptional-pos) "optional columns sorted alphabetically"))))

;; --- Type coercion tests ---

(deftest coercion-roundtrip-test
  (testing "UUID, Instant, boolean, enum, and nippy coercions roundtrip correctly"
    (let [columns        {:thing/id         {:type :uuid :primary-key true}
                          :thing/active     {:type :boolean}
                          :thing/created-at {:type :inst}
                          :thing/tags       {:type :edn}
                          :thing/color      {:type        :enum
                                             :enum-values {0 :thing.color/red
                                                           1 :thing.color/blue}}}
          cols           (util/normalize-columns columns)
          read-coercions (coerce/build-all-read-coercions cols)
          enum-val->int  (coerce/build-enum-val->int cols)
          test-uuid      (UUID/randomUUID)
          test-inst      (Instant/ofEpochMilli 1700000000000)
          test-tags      {:a 1 :b [2 3]}]
      ;; UUID roundtrip
      (is (= test-uuid ((get read-coercions :thing/id)
                        (coerce/uuid->bytes test-uuid))))
      ;; Boolean roundtrip
      (is (= true ((get read-coercions :thing/active)
                   (coerce/bool->int true))))
      (is (= false ((get read-coercions :thing/active)
                    (coerce/bool->int false))))
      ;; Instant roundtrip
      (is (= test-inst ((get read-coercions :thing/created-at)
                        (coerce/inst->epoch-ms test-inst))))
      ;; Nippy roundtrip (edn type)
      (let [frozen (nippy/fast-freeze test-tags)]
        (is (= test-tags ((get read-coercions :thing/tags) frozen))))
      ;; Enum roundtrip
      (is (= :thing.color/red ((get read-coercions :thing/color)
                               (get enum-val->int :thing.color/red)))))))

(deftest execute-string-input-test
  (testing "execute accepts a bare SQL string"
    (let [ctx {:biff.sqlite/read-pool *read-pool*  :biff.sqlite/write-conn *write-conn*
               :biff.sqlite/columns   test-columns}]
      (is (= [{:user/id "u1" :user/name "Alice" :user/joined-at (Instant/ofEpochMilli 1700000000000)}]
             (biff.sqlite/execute ctx "SELECT id, name, joined_at FROM user")))))

  (testing "execute accepts a HoneySQL map"
    (let [ctx {:biff.sqlite/read-pool *read-pool*  :biff.sqlite/write-conn *write-conn*
               :biff.sqlite/columns   test-columns}]
      (is (= [{:user/id "u1"}]
             (biff.sqlite/execute ctx {:select :id :from :user}))))))

(deftest kv-store-roundtrip-test
  (let [db-file (java.io.File/createTempFile "biff-sqlite-kv" ".db")
        db-path (.getAbsolutePath db-file)]
    (.delete db-file)
    (try
      (let [ctx       (biff.sqlite/use-sqlite {:biff.core/stop      []
                                               :biff.sqlite/db-path db-path
                                               :biff.sqlite/columns test-columns})
            set-value (:biff.kv/set-value ctx)
            get-value (:biff.kv/get-value ctx)
            list-kv   (:biff.db/list-kv ctx)]
        (is (contains? (:biff.sqlite/columns ctx) :biff-sqlite-kv/namespace))
        (set-value ctx :demo/settings "theme" {:mode :dark})
        (set-value ctx :demo/settings "theme-2" {:mode :blue})
        (is (= {:mode :dark}
               (get-value ctx :demo/settings "theme")))
        (is (= ["theme" "theme-2"]
               (list-kv ctx :demo/settings "theme")))
        (is (= ["theme" "theme-2"]
               (list-kv ctx :demo/settings nil)))
        (set-value ctx :demo/settings "theme" {:mode :light})
        (is (= {:mode :light}
               (get-value ctx :demo/settings "theme")))
        (set-value ctx :demo/settings "theme" nil)
        (is (nil? (get-value ctx :demo/settings "theme")))
        (is (thrown? AssertionError
                     (set-value ctx "demo/settings" "theme" :bad)))
        (is (thrown? AssertionError
                     (set-value ctx :settings "theme" :bad)))
        (is (thrown? AssertionError
                     (get-value ctx :demo/settings :theme)))
        (is (thrown? AssertionError
                     (list-kv ctx :demo/settings :theme)))
        ((first (:biff.core/stop ctx))))
      (finally
        (.delete (java.io.File. db-path))
        (.delete (java.io.File. (str db-path "-wal")))
        (.delete (java.io.File. (str db-path "-shm")))))))

(deftest execute-and-authorized-write-run-on-tx-test
  (let [calls (atom [])
        ctx   {:biff.sqlite/read-pool  *read-pool*
               :biff.sqlite/write-conn *write-conn*
               :biff.sqlite/columns    test-columns
               :biff.db/on-tx          (fn [_] (swap! calls conj :called))
               :biff.sqlite/authorize  (constantly true)}]
    (biff.sqlite/execute ctx {:insert-into :user
                              :values      [{:user/id        "u2"
                                             :user/name      "Bob"
                                             :user/joined-at (Instant/ofEpochMilli 1700000000000)}]})
    (biff.sqlite/authorized-write ctx
                                  {:update :user
                                   :set    {:user/name "Robert"}
                                   :where  [:= :user/id "u2"]})
    (is (= [:called :called] @calls))))

(deftest kv-store-migrates-legacy-composite-schema-test
  (let [db-file (java.io.File/createTempFile "biff-sqlite-kv-migrate" ".db")
        db-path (.getAbsolutePath db-file)
        frozen  (nippy/fast-freeze {:mode :dark})]
    (.delete db-file)
    (try
      (with-open [conn (jdbc/get-connection (str "jdbc:sqlite:" db-path))]
        (jdbc/execute! conn ["CREATE TABLE biff_sqlite_kv (namespace TEXT NOT NULL, key_ TEXT NOT NULL, value_ BLOB NOT NULL, PRIMARY KEY(namespace, key_)) STRICT"])
        (jdbc/execute! conn ["INSERT INTO biff_sqlite_kv (namespace, key_, value_) VALUES (?, ?, ?)"
                             ":demo/settings"
                             "theme"
                             frozen]))
      (let [ctx       (biff.sqlite/use-sqlite {:biff.core/stop      []
                                               :biff.sqlite/db-path db-path
                                               :biff.sqlite/columns test-columns})
            set-value (:biff.kv/set-value ctx)
            get-value (:biff.kv/get-value ctx)
            kv-rows   (biff.sqlite/execute ctx {:select :* :from :biff-sqlite-kv})]
        (is (= {:mode :dark}
               (get-value ctx :demo/settings "theme")))
        (is (uuid? (:biff-sqlite-kv/id (first kv-rows))))
        (set-value ctx :demo/settings "theme" {:mode :light})
        (is (= {:mode :light}
               (get-value ctx :demo/settings "theme")))
        ((first (:biff.core/stop ctx))))
      (finally
        (.delete (java.io.File. db-path))
        (.delete (java.io.File. (str db-path "-wal")))
        (.delete (java.io.File. (str db-path "-shm")))))))

;; --- Namespaced alias tests ---

(deftest namespaced-alias-honeysql-test
  (let [ctx {:biff.sqlite/read-pool *read-pool*  :biff.sqlite/write-conn *write-conn*
             :biff.sqlite/columns   test-columns}]
    (testing "namespaced alias on column from same table produces correct key"
      (is (= [{:user/joined-at (Instant/ofEpochMilli 1700000000000)}]
             (biff.sqlite/execute ctx {:select [[:joined-at :user/joined-at]]
                                       :from   :user}))))

    (testing "namespaced alias on column from different namespace"
      (let [results (biff.sqlite/execute ctx {:select [[:name :item/name]]
                                              :from   :user})]
        (is (= [{:item/name "Alice"}] results))
        (is (= :item/name (first (keys (first results)))))))

    (testing "namespaced alias with coercion applied via column definition match"
      (is (= [{:user/joined-at (Instant/ofEpochMilli 1700000000000)}]
             (biff.sqlite/execute ctx {:select [[:joined-at :user/joined-at]]
                                       :from   :user}))))

    (testing "mix of regular columns and namespaced aliases"
      (let [results (biff.sqlite/execute ctx {:select [:user/id [:joined-at :user/joined-at]]
                                              :from   :user})]
        (is (= "u1" (:user/id (first results))))
        (is (= (Instant/ofEpochMilli 1700000000000) (:user/joined-at (first results))))))

    (testing "namespaced alias on aggregate expression"
      (let [results (biff.sqlite/execute ctx {:select [[[:count :*] :user/total]]
                                              :from   :user})]
        (is (= [{:user/total 1}] results))))

    (testing "regular non-namespaced alias gets table-qualified by JDBC"
      (let [results (biff.sqlite/execute ctx {:select [[:name :alias]]
                                              :from   :user})]
        (is (= "Alice" (:user/alias (first results))))))))

(deftest namespaced-alias-honeysql-format-test
  (testing "namespaced alias in select is formatted as quoted string"
    (let [input     {:select [[:age :user/age-years]] :from :user}
          processed (update input :select query/preprocess-select)
          sql       (first (hsql/format processed))]
      (is (str/includes? sql "\"user/age_years\"")))))

(deftest namespaced-alias-raw-sql-test
  (let [ctx {:biff.sqlite/read-pool *read-pool*  :biff.sqlite/write-conn *write-conn*
             :biff.sqlite/columns   test-columns}]
    (testing "raw SQL with quoted namespaced alias works"
      (let [results (biff.sqlite/execute ctx
                                         ["SELECT joined_at AS \"user/joined_at\" FROM user"])]
        (is (= [{:user/joined-at (Instant/ofEpochMilli 1700000000000)}] results))))))

;; --- Validation tests ---

(deftest validation-rejects-bad-insert-test
  (testing "validation rejects INSERT with wrong type"
    (let [columns {:user/id        {:type :text :primary-key true}
                   :user/name      {:type :text :required true}
                   :user/joined-at {:type :inst :required true}}
          ctx     {:biff.sqlite/read-pool *read-pool* :biff.sqlite/write-conn *write-conn*
                   :biff.sqlite/columns   columns}]
      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo #"Validation failed"
           (biff.sqlite/execute ctx {:insert-into :user
                                     :values      [{:user/id        "u2"
                                                    :user/name      "Bob"
                                                    :user/joined-at "not-an-inst"}]}))))))

(deftest validation-accepts-good-insert-test
  (testing "validation accepts INSERT with correct types"
    (let [columns {:user/id        {:type :text :primary-key true}
                   :user/name      {:type :text :required true}
                   :user/joined-at {:type :inst :required true}}
          ctx     {:biff.sqlite/read-pool *read-pool* :biff.sqlite/write-conn *write-conn*
                   :biff.sqlite/columns   columns}]
      ;; Should not throw
      (biff.sqlite/execute ctx {:insert-into :user
                                :values      [{:user/id        "u2"
                                               :user/name      "Bob"
                                               :user/joined-at (Instant/ofEpochMilli 1700000000000)}]}))))

(deftest validation-rejects-bad-update-test
  (testing "validation rejects UPDATE with wrong type via :set"
    (let [columns {:user/id        {:type :text :primary-key true}
                   :user/name      {:type :text :required true}
                   :user/joined-at {:type :inst :required true}}
          ctx     {:biff.sqlite/read-pool *read-pool* :biff.sqlite/write-conn *write-conn*
                   :biff.sqlite/columns   columns}]
      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo #"Validation failed"
           (biff.sqlite/execute ctx {:update :user
                                     :set    {:user/joined-at "not-an-inst"}
                                     :where  [:= :user/id "u1"]}))))))

(deftest validation-skips-non-literal-values-test
  (testing "validation skips maps and non-lift vectors"
    (let [columns {:user/id        {:type :text :primary-key true}
                   :user/name      {:type :text :required true}
                   :user/joined-at {:type :inst :required true}}
          ctx     {:biff.sqlite/read-pool *read-pool* :biff.sqlite/write-conn *write-conn*
                   :biff.sqlite/columns   columns}]
      ;; vector values (not [:lift ...]) should be skipped - no validation exception
      (biff.sqlite/execute ctx {:update :user
                                :set    {:user/name [:|| :user/name " Jr."]}
                                :where  [:= :user/id "u1"]}))))

(deftest validation-with-custom-schema-test
  (testing "custom :schema on column is validated"
    (let [columns {:user/id        {:type :text :primary-key true}
                   :user/name      {:type   :text             :required true
                                    :schema [:re #"^[A-Z].*"]}
                   :user/joined-at {:type :inst :required true}}
          ctx     {:biff.sqlite/read-pool *read-pool* :biff.sqlite/write-conn *write-conn*
                   :biff.sqlite/columns   columns}]
      ;; lowercase name should fail validation
      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo #"Validation failed"
           (biff.sqlite/execute ctx {:insert-into :user
                                     :values      [{:user/id        "u3"
                                                    :user/name      "lowercase"
                                                    :user/joined-at (Instant/ofEpochMilli 1700000000000)}]})))
      ;; uppercase name should pass
      (biff.sqlite/execute ctx {:insert-into :user
                                :values      [{:user/id        "u3"
                                               :user/name      "Uppercase"
                                               :user/joined-at (Instant/ofEpochMilli 1700000000000)}]}))))

(deftest validation-lift-test
  (testing "[:lift value] is treated as a literal and validated"
    (let [columns {:user/id        {:type :text :primary-key true}
                   :user/name      {:type :text :required true}
                   :user/joined-at {:type :inst :required true}}
          ctx     {:biff.sqlite/read-pool *read-pool* :biff.sqlite/write-conn *write-conn*
                   :biff.sqlite/columns   columns}]
      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo #"Validation failed"
           (biff.sqlite/execute ctx {:update :user
                                     :set    {:user/joined-at [:lift "not-an-inst"]}
                                     :where  [:= :user/id "u1"]}))))))

(deftest validation-nullable-column-test
  (testing "nullable column accepts nil in validation"
    (let [;; Create a table with a nullable column
          _       (jdbc/execute! *conn* ["CREATE TABLE item (id TEXT PRIMARY KEY, note TEXT) STRICT"])
          _       (jdbc/execute! *conn* ["INSERT INTO item (id, note) VALUES (?, ?)" "i1" "hello"])
          columns {:item/id   {:type :text :primary-key true}
                   :item/note {:type :text}}
          ctx     {:biff.sqlite/read-pool *read-pool* :biff.sqlite/write-conn *write-conn*
                   :biff.sqlite/columns   columns}]
      ;; nil should be valid for a non-required column
      (biff.sqlite/execute ctx {:update :item
                                :set    {:item/note nil}
                                :where  [:= :item/id "i1"]}))))

;; --- generate-schema-sql public API test ---

(deftest generate-schema-sql-test
  (testing "public generate-schema-sql returns full SQL string"
    (let [columns {:user/id    {:type :uuid :primary-key true}
                   :user/email {:type :text :unique true :required true}}
          result  (biff.sqlite/generate-schema-sql
                   {:biff.sqlite/columns        columns
                    :biff.sqlite/extra-init-sql ["CREATE INDEX custom_idx ON user(email);"]})]
      (is (str/includes? result "-- Auto-generated; do not edit."))
      (is (str/includes? result "CREATE TABLE user"))
      (is (str/includes? result "CREATE INDEX custom_idx")))))

;; --- make-resolvers tests ---

(def resolver-columns
  {:user/id        {:type :text :primary-key true}
   :user/name      {:type :text :required true}
   :user/joined-at {:type :inst :required true}
   :post/id        {:type :text :primary-key true}
   :post/title     {:type :text :required true}
   :post/author-id {:type :text :required true :ref :user/id}})

(deftest make-resolvers-shape-test
  (testing "returns one resolver per table with correct structure"
    (let [resolvers (biff.sqlite/make-resolvers {:biff.sqlite/columns resolver-columns})
          by-id     (into {} (map (juxt :id identity)) resolvers)]
      (is (= 2 (count resolvers)))
      ;; User resolver
      (let [r (by-id :com.biffweb.sqlite/user-resolver)]
        (is (some? r))
        (is (= [:user/id] (:input r)))
        (is (= true (:batch r)))
        (is (every? #{:user/name :user/joined-at} (:output r)))
        (is (not (contains? (set (:output r)) :user/id))))
      ;; Post resolver
      (let [r (by-id :com.biffweb.sqlite/post-resolver)]
        (is (some? r))
        (is (= [:post/id] (:input r)))
        (is (= true (:batch r)))
        (is (contains? (set (:output r)) :post/title))
        (is (contains? (set (:output r)) :post/author-id))
        (is (contains? (set (:output r)) {:post/author [:user/id]}))))))

(deftest make-resolvers-batch-resolve-test
  (testing "batch resolve returns correct data and join keys"
    (jdbc/execute! *conn* ["CREATE TABLE post (id TEXT PRIMARY KEY, title TEXT NOT NULL, author_id TEXT NOT NULL, FOREIGN KEY(author_id) REFERENCES user(id)) STRICT"])
    (jdbc/execute! *conn* ["INSERT INTO post (id, title, author_id) VALUES (?, ?, ?)" "p1" "Hello" "u1"])
    (jdbc/execute! *conn* ["INSERT INTO post (id, title, author_id) VALUES (?, ?, ?)" "p2" "World" "u1"])
    (let [ctx           {:biff.sqlite/read-pool  *read-pool*
                         :biff.sqlite/write-conn *write-conn*
                         :biff.sqlite/columns    resolver-columns}
          resolvers     (biff.sqlite/make-resolvers ctx)
          post-resolver (first (filter #(= :com.biffweb.sqlite/post-resolver (:id %)) resolvers))
          results       ((:resolve post-resolver) ctx [{:post/id "p1"} {:post/id "p2"}])]
      (is (= 2 (count results)))
      (is (= "Hello" (:post/title (first results))))
      (is (= "World" (:post/title (second results))))
      ;; Raw ref column preserved
      (is (= "u1" (:post/author-id (first results))))
      ;; Join key added
      (is (= {:user/id "u1"} (:post/author (first results)))))))

(deftest make-resolvers-missing-entity-test
  (testing "batch resolve returns empty map for missing entities"
    (let [ctx           {:biff.sqlite/read-pool  *read-pool*
                         :biff.sqlite/write-conn *write-conn*
                         :biff.sqlite/columns    resolver-columns}
          resolvers     (biff.sqlite/make-resolvers ctx)
          user-resolver (first (filter #(= :com.biffweb.sqlite/user-resolver (:id %)) resolvers))
          results       ((:resolve user-resolver) ctx [{:user/id "u1"} {:user/id "nonexistent"}])]
      (is (= 2 (count results)))
      (is (= "Alice" (:user/name (first results))))
      (is (= {} (second results))))))

(deftest make-resolvers-no-primary-key-test
  (testing "throws when table has no primary key"
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo #"No primary key"
         (biff.sqlite/make-resolvers
          {:biff.sqlite/columns {:broken/name {:type :text}}})))))

(deftest make-resolvers-no-refs-test
  (testing "tables without ref columns have no extra join keys"
    (let [resolvers (biff.sqlite/make-resolvers
                     {:biff.sqlite/columns {:item/id    {:type :text :primary-key true}
                                            :item/label {:type :text}}})
          r         (first resolvers)]
      (is (= [:item/label] (:output r))))))

(deftest make-resolvers-graph-join-test
  (testing "generated ref joins work with biff.graph"
    (jdbc/execute! *conn* ["CREATE TABLE article (id TEXT PRIMARY KEY, title TEXT NOT NULL, author_id TEXT NOT NULL, FOREIGN KEY(author_id) REFERENCES user(id)) STRICT"])
    (jdbc/execute! *conn* ["INSERT INTO article (id, title, author_id) VALUES (?, ?, ?)" "a1" "Post" "u1"])
    (let [cols {:user/id           {:type :text :primary-key true}
                :user/name         {:type :text :required true}
                :user/joined-at    {:type :inst :required true}
                :article/id        {:type :text :primary-key true}
                :article/title     {:type :text :required true}
                :article/author-id {:type :text :required true :ref :user/id}}
          ctx  {:biff.sqlite/read-pool  *read-pool*
                :biff.sqlite/write-conn *write-conn*
                :biff.sqlite/columns    cols}
          idx  (biff.graph/build-index (biff.sqlite/make-resolvers ctx))]
      (is (= {:article/title  "Post"
              :article/author {:user/name "Alice"}}
             (biff.graph/query (assoc ctx :biff.graph/index idx)
                               {:article/id "a1"}
                               [:article/title
                                {:article/author [:user/name]}]))))))

(deftest make-resolvers-nil-values-test
  (testing "resolver does not return keys with nil values"
    (jdbc/execute! *conn* ["CREATE TABLE entry (id TEXT PRIMARY KEY, title TEXT, author_id TEXT) STRICT"])
    (jdbc/execute! *conn* ["INSERT INTO entry (id, title, author_id) VALUES (?, ?, ?)" "e1" nil nil])
    (jdbc/execute! *conn* ["INSERT INTO entry (id, title, author_id) VALUES (?, ?, ?)" "e2" "Hello" "u1"])
    (let [cols      {:entry/id        {:type :text :primary-key true}
                     :entry/title     {:type :text}
                     :entry/author-id {:type :text :ref :user/id}}
          ctx       {:biff.sqlite/read-pool  *read-pool*
                     :biff.sqlite/write-conn *write-conn*
                     :biff.sqlite/columns    cols}
          resolvers (biff.sqlite/make-resolvers ctx)
          r         (first resolvers)
          results   ((:resolve r) ctx [{:entry/id "e1"} {:entry/id "e2"}])]
      ;; e1 has all nil values, so result should have no keys (just empty from id->result)
      (is (not (contains? (first results) :entry/title)))
      (is (not (contains? (first results) :entry/author-id)))
      (is (not (contains? (first results) :entry/author)))
      ;; e2 has values, so all keys present
      (is (= "Hello" (:entry/title (second results))))
      (is (= "u1" (:entry/author-id (second results))))
      (is (= {:user/id "u1"} (:entry/author (second results)))))))

;; --- authorized-write tests ---

(defn- allow-all [_ctx _diff] true)
(defn- deny-all [_ctx _diff] false)

(deftest authorized-write-insert-test
  (testing "insert generates :create diff entries"
    (let [ctx  {:biff.sqlite/read-pool  *read-pool*
                :biff.sqlite/write-conn *write-conn*
                :biff.sqlite/columns    test-columns
                :biff.sqlite/authorize  allow-all}
          now  (Instant/ofEpochMilli 1700000000000)
          diff (biff.sqlite/authorized-write
                ctx
                {:insert-into :user
                 :values      [{:user/id        "u2"
                                :user/name      "Bob"
                                :user/joined-at now}]})]
      (is (= 1 (count diff)))
      (is (= :user (-> diff first :table)))
      (is (= :create (-> diff first :op)))
      (is (nil? (-> diff first :before)))
      (is (= "u2" (-> diff first :after :user/id)))
      (is (= "Bob" (-> diff first :after :user/name)))
      (is (= now (-> diff first :after :user/joined-at))))))

(deftest authorized-write-insert-multiple-test
  (testing "insert with multiple values generates multiple :create diff entries"
    (let [ctx  {:biff.sqlite/read-pool  *read-pool*
                :biff.sqlite/write-conn *write-conn*
                :biff.sqlite/columns    test-columns
                :biff.sqlite/authorize  allow-all}
          now  (Instant/ofEpochMilli 1700000000000)
          diff (biff.sqlite/authorized-write
                ctx
                {:insert-into :user
                 :values      [{:user/id        "u2"
                                :user/name      "Bob"
                                :user/joined-at now}
                               {:user/id        "u3"
                                :user/name      "Charlie"
                                :user/joined-at now}]})]
      (is (= 2 (count diff)))
      (is (every? #(= :create (:op %)) diff))
      (is (every? #(nil? (:before %)) diff))
      (is (= #{"u2" "u3"} (set (map #(-> % :after :user/id) diff)))))))

(deftest authorized-write-delete-test
  (testing "delete generates :delete diff entries with :before values"
    (let [ctx  {:biff.sqlite/read-pool  *read-pool*
                :biff.sqlite/write-conn *write-conn*
                :biff.sqlite/columns    test-columns
                :biff.sqlite/authorize  allow-all}
          diff (biff.sqlite/authorized-write
                ctx
                {:delete-from :user
                 :where       [:= :user/id "u1"]})]
      (is (= 1 (count diff)))
      (is (= :user (-> diff first :table)))
      (is (= :delete (-> diff first :op)))
      (is (= "u1" (-> diff first :before :user/id)))
      (is (= "Alice" (-> diff first :before :user/name)))
      (is (nil? (-> diff first :after))))))

(deftest authorized-write-delete-nonexistent-test
  (testing "delete of nonexistent record produces empty diff"
    (let [ctx  {:biff.sqlite/read-pool  *read-pool*
                :biff.sqlite/write-conn *write-conn*
                :biff.sqlite/columns    test-columns
                :biff.sqlite/authorize  allow-all}
          diff (biff.sqlite/authorized-write
                ctx
                {:delete-from :user
                 :where       [:= :user/id "nonexistent"]})]
      (is (= [] diff)))))

(deftest authorized-write-update-test
  (testing "update generates :update diff with :before and :after"
    (let [ctx  {:biff.sqlite/read-pool  *read-pool*
                :biff.sqlite/write-conn *write-conn*
                :biff.sqlite/columns    test-columns
                :biff.sqlite/authorize  allow-all}
          diff (biff.sqlite/authorized-write
                ctx
                {:update :user
                 :set    {:user/name "Alicia"}
                 :where  [:= :user/id "u1"]})]
      (is (= 1 (count diff)))
      (is (= :user (-> diff first :table)))
      (is (= :update (-> diff first :op)))
      (is (= "Alice" (-> diff first :before :user/name)))
      (is (= "Alicia" (-> diff first :after :user/name)))
      (is (= "u1" (-> diff first :before :user/id)))
      (is (= "u1" (-> diff first :after :user/id))))))

(deftest authorized-write-update-pk-change-test
  (testing "update that changes primary key throws an exception"
    (let [ctx {:biff.sqlite/read-pool  *read-pool*
               :biff.sqlite/write-conn *write-conn*
               :biff.sqlite/columns    test-columns
               :biff.sqlite/authorize  allow-all}]
      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo #"does not allow changing primary key"
           (biff.sqlite/authorized-write
            ctx
            {:update :user
             :set    {:user/id "u1-new"}
             :where  [:= :user/id "u1"]}))))))

(deftest authorized-write-upsert-test
  (testing "insert with :on-conflict generates correct diff"
    (let [ctx  {:biff.sqlite/read-pool  *read-pool*
                :biff.sqlite/write-conn *write-conn*
                :biff.sqlite/columns    test-columns
                :biff.sqlite/authorize  allow-all}
          ;; u1 already exists with name "Alice", so this should update
          diff (biff.sqlite/authorized-write
                ctx
                {:insert-into   :user
                 :values        [{:user/id        "u1"
                                  :user/name      "Alice2"
                                  :user/joined-at (Instant/ofEpochMilli 1700000000000)}]
                 :on-conflict   [:user/id]
                 :do-update-set [:user/name]})]
      (is (= 1 (count diff)))
      (is (= :update (-> diff first :op)))
      (is (= "Alice" (-> diff first :before :user/name)))
      (is (= "Alice2" (-> diff first :after :user/name))))))

(deftest authorized-write-upsert-new-record-test
  (testing "insert with :on-conflict creates new record when no conflict"
    (let [ctx  {:biff.sqlite/read-pool  *read-pool*
                :biff.sqlite/write-conn *write-conn*
                :biff.sqlite/columns    test-columns
                :biff.sqlite/authorize  allow-all}
          diff (biff.sqlite/authorized-write
                ctx
                {:insert-into   :user
                 :values        [{:user/id        "u-new"
                                  :user/name      "NewUser"
                                  :user/joined-at (Instant/ofEpochMilli 1700000000000)}]
                 :on-conflict   [:user/id]
                 :do-update-set [:user/name]})]
      (is (= 1 (count diff)))
      (is (= :create (-> diff first :op)))
      (is (nil? (-> diff first :before)))
      (is (= "NewUser" (-> diff first :after :user/name))))))

(deftest authorized-write-upsert-pk-in-do-update-set-test
  (testing "upsert with primary key in :do-update-set throws"
    (let [ctx {:biff.sqlite/read-pool  *read-pool*
               :biff.sqlite/write-conn *write-conn*
               :biff.sqlite/columns    test-columns
               :biff.sqlite/authorize  allow-all}]
      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo #"does not allow changing primary key"
           (biff.sqlite/authorized-write
            ctx
            {:insert-into   :user
             :values        [{:user/id        "u1"
                              :user/name      "Alice2"
                              :user/joined-at (Instant/ofEpochMilli 1700000000000)}]
             :on-conflict   [:user/id]
             :do-update-set [:user/id :user/name]}))))))

(deftest authorized-write-rejects-replace-into-test
  (testing "replace-into throws"
    (let [ctx {:biff.sqlite/read-pool  *read-pool*
               :biff.sqlite/write-conn *write-conn*
               :biff.sqlite/columns    test-columns
               :biff.sqlite/authorize  allow-all}]
      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo #"REPLACE INTO"
           (biff.sqlite/authorized-write
            ctx
            {:replace-into :user
             :values       [{:user/id        "u1"
                             :user/name      "Alice2"
                             :user/joined-at (Instant/ofEpochMilli 1700000000000)}]}))))))

(deftest authorized-write-update-set-must-be-map-test
  (testing "update with non-map :set throws"
    (let [ctx {:biff.sqlite/read-pool  *read-pool*
               :biff.sqlite/write-conn *write-conn*
               :biff.sqlite/columns    test-columns
               :biff.sqlite/authorize  allow-all}]
      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo #"requires :set to be a map"
           (biff.sqlite/authorized-write
            ctx
            {:update :user
             :set    [:user/name]
             :where  [:= :user/id "u1"]}))))))

(deftest authorized-write-denied-test
  (testing "when authorize returns false, transaction is rolled back"
    (let [ctx {:biff.sqlite/read-pool  *read-pool*
               :biff.sqlite/write-conn *write-conn*
               :biff.sqlite/columns    test-columns
               :biff.sqlite/authorize  deny-all}]
      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo #"rejected by authorization"
           (biff.sqlite/authorized-write
            ctx
            {:insert-into :user
             :values      [{:user/id        "u2"
                            :user/name      "Bob"
                            :user/joined-at (Instant/ofEpochMilli 1700000000000)}]})))
      ;; Verify the insert was rolled back
      (let [read-ctx {:biff.sqlite/read-pool  *read-pool*
                      :biff.sqlite/write-conn *write-conn*
                      :biff.sqlite/columns    test-columns}
            results  (biff.sqlite/execute read-ctx {:select :* :from :user})]
        (is (= 1 (count results)))
        (is (= "u1" (:user/id (first results))))))))

(deftest authorized-write-denied-update-rollback-test
  (testing "denied update is rolled back"
    (let [ctx {:biff.sqlite/read-pool  *read-pool*
               :biff.sqlite/write-conn *write-conn*
               :biff.sqlite/columns    test-columns
               :biff.sqlite/authorize  deny-all}]
      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo #"rejected by authorization"
           (biff.sqlite/authorized-write
            ctx
            {:update :user
             :set    {:user/name "Hacker"}
             :where  [:= :user/id "u1"]})))
      ;; Verify the update was rolled back
      (let [read-ctx {:biff.sqlite/read-pool  *read-pool*
                      :biff.sqlite/write-conn *write-conn*
                      :biff.sqlite/columns    test-columns}
            results  (biff.sqlite/execute read-ctx {:select :* :from :user})]
        (is (= "Alice" (:user/name (first results))))))))

(deftest authorized-write-denied-delete-rollback-test
  (testing "denied delete is rolled back"
    (let [ctx {:biff.sqlite/read-pool  *read-pool*
               :biff.sqlite/write-conn *write-conn*
               :biff.sqlite/columns    test-columns
               :biff.sqlite/authorize  deny-all}]
      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo #"rejected by authorization"
           (biff.sqlite/authorized-write
            ctx
            {:delete-from :user
             :where       [:= :user/id "u1"]})))
      ;; Verify the delete was rolled back
      (let [read-ctx {:biff.sqlite/read-pool  *read-pool*
                      :biff.sqlite/write-conn *write-conn*
                      :biff.sqlite/columns    test-columns}
            results  (biff.sqlite/execute read-ctx {:select :* :from :user})]
        (is (= 1 (count results)))))))

(deftest authorized-write-missing-authorize-fn-test
  (testing "throws when :biff.sqlite/authorize is missing from ctx"
    (let [ctx {:biff.sqlite/read-pool  *read-pool*
               :biff.sqlite/write-conn *write-conn*
               :biff.sqlite/columns    test-columns}]
      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo #"requires :biff.sqlite/authorize"
           (biff.sqlite/authorized-write
            ctx
            {:insert-into :user
             :values      [{:user/id        "u2"
                            :user/name      "Bob"
                            :user/joined-at (Instant/ofEpochMilli 1700000000000)}]}))))))

(deftest authorized-write-rejects-select-test
  (testing "throws when given a SELECT statement"
    (let [ctx {:biff.sqlite/read-pool  *read-pool*
               :biff.sqlite/write-conn *write-conn*
               :biff.sqlite/columns    test-columns
               :biff.sqlite/authorize  allow-all}]
      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo #"only accepts INSERT, UPDATE, or DELETE"
           (biff.sqlite/authorized-write
            ctx
            {:select :* :from :user}))))))

(deftest authorized-write-rejects-raw-sql-test
  (testing "throws when given a raw SQL string"
    (let [ctx {:biff.sqlite/read-pool  *read-pool*
               :biff.sqlite/write-conn *write-conn*
               :biff.sqlite/columns    test-columns
               :biff.sqlite/authorize  allow-all}]
      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo #"only accepts HoneySQL maps"
           (biff.sqlite/authorized-write
            ctx
            "DELETE FROM user WHERE id = 'u1'"))))))

(deftest authorized-write-authorize-receives-ctx-test
  (testing "authorize fn receives ctx with before-conn and after-conn"
    (let [received (atom nil)
          ctx      {:biff.sqlite/read-pool  *read-pool*
                    :biff.sqlite/write-conn *write-conn*
                    :biff.sqlite/columns    test-columns
                    :biff.sqlite/authorize  (fn [ctx diff]
                                              (reset! received {:ctx-keys (set (keys ctx))
                                                                :diff     diff})
                                              true)}]
      (biff.sqlite/authorized-write
       ctx
       {:insert-into :user
        :values      [{:user/id        "u2"
                       :user/name      "Bob"
                       :user/joined-at (Instant/ofEpochMilli 1700000000000)}]})
      (is (some? @received))
      (is (contains? (:ctx-keys @received) :biff.sqlite/authorize))
      (is (contains? (:ctx-keys @received) :biff.sqlite/before-conn))
      (is (contains? (:ctx-keys @received) :biff.sqlite/after-conn))
      (is (= 1 (count (:diff @received))))
      (is (= :create (-> @received :diff first :op))))))

;; --- Representative test cases from yakread/budgetswu patterns ---

(deftest authorized-write-yakread-update-settings-test
  (testing "update user settings (yakread pattern)"
    (let [ctx  {:biff.sqlite/read-pool  *read-pool*
                :biff.sqlite/write-conn *write-conn*
                :biff.sqlite/columns    test-columns
                :biff.sqlite/authorize  allow-all}
          diff (biff.sqlite/authorized-write
                ctx
                {:update :user
                 :set    {:user/name "Alice Updated"}
                 :where  [:= :user/id "u1"]})]
      (is (= 1 (count diff)))
      (is (= :update (:op (first diff))))
      (is (= "Alice" (-> diff first :before :user/name)))
      (is (= "Alice Updated" (-> diff first :after :user/name))))))

(deftest authorized-write-budgetswu-delete-multiple-test
  (testing "delete with :in clause (budgetswu pattern)"
    (jdbc/execute! *conn* ["CREATE TABLE item (id TEXT PRIMARY KEY, label TEXT) STRICT"])
    (jdbc/execute! *conn* ["INSERT INTO item (id, label) VALUES (?, ?)" "i1" "One"])
    (jdbc/execute! *conn* ["INSERT INTO item (id, label) VALUES (?, ?)" "i2" "Two"])
    (jdbc/execute! *conn* ["INSERT INTO item (id, label) VALUES (?, ?)" "i3" "Three"])
    (let [columns {:item/id    {:type :text :primary-key true}
                   :item/label {:type :text}}
          ctx     {:biff.sqlite/read-pool  *read-pool*
                   :biff.sqlite/write-conn *write-conn*
                   :biff.sqlite/columns    columns
                   :biff.sqlite/authorize  allow-all}
          diff    (biff.sqlite/authorized-write
                   ctx
                   {:delete-from :item
                    :where       [:in :item/id ["i1" "i2"]]})]
      (is (= 2 (count diff)))
      (is (every? #(= :delete (:op %)) diff))
      (is (= #{"i1" "i2"} (set (map #(-> % :before :item/id) diff)))))))

(deftest authorized-write-budgetswu-insert-batch-test
  (testing "batch insert (budgetswu collection-card pattern)"
    (jdbc/execute! *conn* ["CREATE TABLE card (id TEXT PRIMARY KEY, card_name TEXT NOT NULL, user_id TEXT NOT NULL) STRICT"])
    (let [columns {:card/id        {:type :text :primary-key true}
                   :card/card-name {:type :text :required true}
                   :card/user-id   {:type :text :required true}}
          ctx     {:biff.sqlite/read-pool  *read-pool*
                   :biff.sqlite/write-conn *write-conn*
                   :biff.sqlite/columns    columns
                   :biff.sqlite/authorize  allow-all}
          diff    (biff.sqlite/authorized-write
                   ctx
                   {:insert-into :card
                    :values      [{:card/id        "c1"
                                   :card/card-name "Darth Vader"
                                   :card/user-id   "u1"}
                                  {:card/id        "c2"
                                   :card/card-name "Luke Skywalker"
                                   :card/user-id   "u1"}]})]
      (is (= 2 (count diff)))
      (is (every? #(= :create (:op %)) diff))
      (is (= #{"Darth Vader" "Luke Skywalker"}
             (set (map #(-> % :after :card/card-name) diff)))))))

(deftest authorized-write-update-no-pk-throws-test
  (testing "update on table without primary key throws"
    (jdbc/execute! *conn* ["CREATE TABLE log (message TEXT, level TEXT) STRICT"])
    (jdbc/execute! *conn* ["INSERT INTO log (message, level) VALUES (?, ?)" "hello" "info"])
    (let [columns {:log/message {:type :text}
                   :log/level   {:type :text}}
          ctx     {:biff.sqlite/read-pool  *read-pool*
                   :biff.sqlite/write-conn *write-conn*
                   :biff.sqlite/columns    columns
                   :biff.sqlite/authorize  allow-all}]
      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo #"primary key"
           (biff.sqlite/authorized-write
            ctx
            {:update :log
             :set    {:log/level "warn"}
             :where  [:= :log/message "hello"]}))))))

(deftest authorized-write-conditional-authorize-test
  (testing "authorize fn can inspect diff to make decisions"
    (let [;; Only allow creating users, not deleting them
          authorize-fn (fn [_ctx diff]
                         (every? #(= :create (:op %)) diff))
          ctx          {:biff.sqlite/read-pool  *read-pool*
                        :biff.sqlite/write-conn *write-conn*
                        :biff.sqlite/columns    test-columns
                        :biff.sqlite/authorize  authorize-fn}]
      ;; Insert should succeed
      (let [diff (biff.sqlite/authorized-write
                  ctx
                  {:insert-into :user
                   :values      [{:user/id        "u2"
                                  :user/name      "Bob"
                                  :user/joined-at (Instant/ofEpochMilli 1700000000000)}]})]
        (is (= 1 (count diff))))
      ;; Delete should be rejected
      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo #"rejected by authorization"
           (biff.sqlite/authorized-write
            ctx
            {:delete-from :user
             :where       [:= :user/id "u2"]})))
      ;; Verify u2 still exists (delete was rolled back)
      (let [results (biff.sqlite/execute
                     (dissoc ctx :biff.sqlite/authorize)
                     {:select :* :from :user :where [:= :user/id "u2"]})]
        (is (= 1 (count results)))))))
