(ns com.biffweb.sqlite
  "SQLite integration for Biff: connection pooling, schema migrations, query execution,
   and a built-in key/value store.

   Public API:
   - `module`              — Biff module exposing SQLite fx handlers.
   - `use-sqlite`          — Biff component for schema migrations, connection pooling, and litestream replication.
   - `execute`             — Execute SQL queries/statements with automatic type coercion and validation.
   - `authorized-write`    — Execute INSERT/UPDATE/DELETE with authorization checks.
   - `use-litestream`      — Biff component for litestream replication (called by use-sqlite automatically).
   - `generate-schema-sql` — Generate the complete schema SQL string from column definitions.
   - `make-resolvers`      — Create biff.inject resolvers for SQLite tables from column definitions."
  (:require
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [com.biffweb.core :as biff.core]
   [com.biffweb.graph :as biff.graph]
   [com.biffweb.sqlite.impl.authorize :as authorize]
   [com.biffweb.sqlite.impl.execute :as exec]
   [com.biffweb.sqlite.impl.litestream :as litestream]
   [com.biffweb.sqlite.impl.pool :as pool]
   [com.biffweb.sqlite.impl.schema :as schema]
   [com.biffweb.sqlite.impl.sqlite3def :as sqlite3def]
   [com.biffweb.sqlite.impl.util :as util]
   [taoensso.nippy :as nippy]))

(biff.core/register
 {:biff.sqlite/after-conn                   'some?
  :biff.sqlite/authorize                    'ifn?
  :biff.sqlite/before-conn                  'some?
  :biff.sqlite/columns                      [:map-of :qualified-keyword :map]
  :biff.sqlite/db-path                      :string
  :biff.sqlite/extra-init-sql               [:sequential :string]
  :biff.sqlite/litestream-access-key-id     :string
  :biff.sqlite/litestream-bucket            :string
  :biff.sqlite/litestream-endpoint          :string
  :biff.sqlite/litestream-path              :string
  :biff.sqlite/litestream-region            :string
  :biff.sqlite/litestream-secret-access-key 'ifn?
  :biff.sqlite/litestream-version           :string
  :biff.sqlite/read-pool                    'some?
  :biff.sqlite/sqlite3def-version           :string
  :biff.sqlite/write-conn                   'some?})

(def ^:private kv-columns
  {:biff-sqlite-kv/id        {:type :uuid :primary-key true}
   :biff-sqlite-kv/namespace {:type        :text
                              :required    true
                              :unique-with [:biff-sqlite-kv/key-]}
   :biff-sqlite-kv/key-      {:type :text :required true}
   :biff-sqlite-kv/value-    {:type :blob :required true}})

(defn- validate-kv-args [namespace key]
  (assert (qualified-keyword? namespace) "namespace must be a qualified keyword")
  (assert (string? key) "key must be a string"))

(defn- validate-kv-prefix-args [namespace key-prefix]
  (assert (qualified-keyword? namespace) "namespace must be a qualified keyword")
  (assert (or (nil? key-prefix) (string? key-prefix))
          "key-prefix must be nil or a string"))

(defn- set-kv-value [ctx namespace key value]
  (validate-kv-args namespace key)
  (if (nil? value)
    (do
      (exec/execute ctx ["DELETE FROM biff_sqlite_kv WHERE namespace = ? AND key_ = ?"
                         (str namespace)
                         key])
      nil)
    (let [value* (nippy/fast-freeze value)]
      (exec/execute ctx {:insert-into   :biff-sqlite-kv
                         :values        [{:biff-sqlite-kv/id        (random-uuid)
                                          :biff-sqlite-kv/namespace (str namespace)
                                          :biff-sqlite-kv/key-      key
                                          :biff-sqlite-kv/value-    value*}]
                         :on-conflict   [:biff-sqlite-kv/namespace :biff-sqlite-kv/key-]
                         :do-update-set {:biff-sqlite-kv/value- value*}})
      nil)))

(defn- get-kv-value [ctx namespace key]
  (validate-kv-args namespace key)
  (some-> (first (exec/execute ctx {:select [:biff-sqlite-kv/value-]
                                    :from   :biff-sqlite-kv
                                    :where  [:and
                                             [:= :biff-sqlite-kv/namespace (str namespace)]
                                             [:= :biff-sqlite-kv/key- key]]}))
          :biff-sqlite-kv/value-
          nippy/fast-thaw))

(defn- list-kv-values [ctx namespace key-prefix]
  (validate-kv-prefix-args namespace key-prefix)
  (->> (exec/execute
        ctx
        (cond-> {:select   [:biff-sqlite-kv/key-]
                 :from     :biff-sqlite-kv
                 :where    [:= :biff-sqlite-kv/namespace (str namespace)]
                 :order-by [[:biff-sqlite-kv/key- :asc]]}
          key-prefix
          (update :where
                  (fn [where]
                    [:and
                     where
                     [:like :biff-sqlite-kv/key- (str key-prefix "%")]]))))
       (mapv :biff-sqlite-kv/key-)))

(defn- run-on-tx! [ctx result]
  (when-let [on-tx (:biff.db/on-tx ctx)]
    (on-tx ctx))
  result)

(defn generate-schema-sql  "Generate the complete schema SQL string from column definitions.
   Takes a map with :biff.sqlite/columns (map of column-id → column-props)
   and optional :biff.sqlite/extra-init-sql (vector of SQL strings).
   Returns the full SQL string."
  [{:biff.sqlite/keys [columns extra-init-sql]}]
  (schema/generate-schema-sql (util/normalize-columns (or columns {}))
                              (or extra-init-sql [])))

(defn execute
  "Execute a SQL query/statement. Input can be a HoneySQL map, a raw SQL string,
   or a JDBC-style [sql & params] vector.

   Returns results as qualified kebab-case maps with read coercions applied
   automatically. Write coercions are applied to parameters.

   ctx is the system map, from which `:biff.sqlite/read-pool`,
   `:biff.sqlite/write-conn`, and `:biff.sqlite/columns` are used.
   Write statements are executed on write-conn under a lock.

   For INSERT/UPDATE HoneySQL maps, literal values in `:set` and `:values` are
   validated against column schemas before execution."
  [ctx input]
  (exec/execute ctx input))

(defn authorized-write
  "Execute an INSERT, UPDATE, or DELETE statement with authorization checks.

   Like `execute`, but:
   1. Only accepts HoneySQL maps for INSERT, UPDATE, DELETE, or INSERT...ON CONFLICT.
   2. Generates a diff describing the changes (vector of maps with :table, :op,
      :before, :after).
   3. Calls the `:biff.sqlite/authorize` function from ctx with `(authorize ctx diff)`.
      The ctx passed to authorize includes `:biff.sqlite/before-conn` (a read
      transaction opened before the write) and `:biff.sqlite/after-conn` (the write
      transaction connection, which can see uncommitted changes). The authorize
      function can query these via `(execute (set/rename-keys ctx
      {:biff.sqlite/before-conn :biff.sqlite/read-pool}) ...)`.
   4. If authorize returns falsy, aborts the transaction and throws an exception.

   Uses a separate read transaction on `:biff.sqlite/read-pool` to get a consistent
   snapshot of the database before the write. For UPDATE and upsert statements, the
   write is executed first with RETURNING *, then the read transaction is queried for
   the before-values using the returned primary keys.

   The diff is a vector of maps:
     {:table  :user        ; table keyword
      :op     :create      ; :create, :update, or :delete
      :before {...}        ; nil for :create
      :after  {...}}       ; nil for :delete

   Returns the diff vector on success."
  [ctx input]
  (when-not (:biff.sqlite/authorize ctx)
    (throw (ex-info "authorized-write requires :biff.sqlite/authorize in ctx."
                    {})))
  (locking exec/write-lock
    (run-on-tx! ctx (authorize/authorized-write! ctx input))))

(def fx-handlers
  {:biff.sqlite.fx/execute          execute
   :biff.sqlite.fx/authorized-write authorized-write})

(defn use-litestream
  "Biff component for litestream replication. Downloads the litestream binary
   automatically and starts continuous replication to S3.

   This is called by `use-sqlite` already — it's only exposed in case you want
   to use litestream replication without the full `use-sqlite` component."
  [ctx]
  (litestream/use-litestream ctx))

(defn use-sqlite
  "Biff component that runs schema migrations and starts a connection pool.
   Adds `:biff.sqlite/read-pool` (HikariCP pool) and `:biff.sqlite/write-conn`
   (single long-lived connection) to the system context.

   Looks for `:biff.sqlite/columns` (map of column-id → column-props) and
   `:biff.sqlite/extra-init-sql` (vector of SQL strings) in the system map.

   If litestream config is present, automatically handles binary download, DB
   restore from S3 (if no local DB exists), and starts continuous replication.

   Both sqlite3def and litestream binaries are auto-installed if not found globally.

   Column format (map of qualified keyword → property map):
     {:user/id {:type :uuid :primary-key true}
      :user/email {:type :text :unique true :required true}}

   Column properties:
     :type         - (required) :int, :real, :text, :boolean, :inst, :uuid, :enum, :edn, :blob
     :primary-key  - boolean, adds PRIMARY KEY (implies :required)
     :unique       - boolean, adds UNIQUE constraint
     :unique-with  - vector of column keywords, adds compound UNIQUE constraint
     :required     - boolean, adds NOT NULL
     :ref          - column keyword, adds FOREIGN KEY
     :index        - boolean, adds CREATE INDEX
     :schema       - malli schema for validation
     :enum-values  - map of int → keyword (required for :enum type)"
  [{:biff.sqlite/keys [db-path columns extra-init-sql sqlite3def-version]
    :or               {db-path "storage/sqlite/main.db"}
    :as               ctx}]
  (let [ctx             (litestream/use-litestream ctx)
        columns         (merge (or columns {}) kv-columns)
        cols            (util/normalize-columns columns)
        sqlite3def-path (sqlite3def/resolve-bin!
                         (or sqlite3def-version sqlite3def/default-version))
        _               (schema/apply-schema! db-path "resources/schema.sql"
                                              cols (or extra-init-sql []) sqlite3def-path)
        read-pool       (pool/start-read-pool db-path)
        write-conn      (pool/start-write-conn db-path)]
    (log/info "SQLite connection pool started at" db-path)
    (-> ctx
        (assoc :biff.sqlite/columns columns
               :biff.sqlite/read-pool read-pool
               :biff.sqlite/write-conn write-conn
               :biff.db/get-kv get-kv-value
               :biff.db/list-kv list-kv-values
               :biff.db/set-kv set-kv-value
               :biff.kv/get-value get-kv-value
               :biff.kv/set-value set-kv-value)
        (update :biff.core/stop conj (fn []
                                       (.close write-conn)
                                       (.close read-pool))))))

(defn module
  []
  {:biff.fx/handlers fx-handlers
   :biff.core/init
   (fn [modules-var]
     {:biff.db/get-kv  get-kv-value
      :biff.db/list-kv list-kv-values
      :biff.db/set-kv  set-kv-value
      :biff.db/on-tx
      (fn [ctx]
        (doseq [on-tx (keep :biff.db/on-tx @modules-var)]
          (on-tx ctx)))})})

(defn- strip-id-suffix
  "Remove -id suffix from a keyword name: :post/author-id -> :post/author"
  [k]
  (keyword (namespace k) (subs (name k) 0 (- (count (name k)) 3))))

(defn make-resolvers
  "Create biff.inject resolvers for SQLite tables from column definitions.

   Takes a map with :biff.sqlite/columns (map of column-keyword → property-map).
   Returns a vector of resolver maps compatible with com.biffweb.inject/build-index.

   Each resolver:
   - Takes the table's primary key as input
   - Returns all other columns as output (batch resolver)
   - For ref columns ending in -id, adds a join key without the -id suffix
      e.g. :post/author-id #uuid \"...\" → :post/author {:user/id #uuid \"...\"}"
  [{:biff.sqlite/keys [columns]}]
  (let [columns  (or columns {})
        by-table (group-by (comp keyword namespace key) columns)]
    (vec
     (for [[table-key table-cols] by-table
           :let                   [table-cols-map (into {} table-cols)
                                   pk-entry (first (filter (fn [[_ props]] (:primary-key props)) table-cols-map))
                                   _ (when-not pk-entry
                                       (throw (ex-info (str "No primary key found for table " table-key)
                                                       {:table table-key})))
                                   pk-key (key pk-entry)
                                   non-pk-cols (dissoc table-cols-map pk-key)
                    ;; Ref columns whose name ends in -id: {col-key ref-target-key}
                                   ref-cols (into {}
                                                  (keep (fn [[col-key props]]
                                                          (when (and (:ref props)
                                                                     (str/ends-with? (name col-key) "-id"))
                                                            [col-key (:ref props)])))
                                                  non-pk-cols)
                   ;; Precomputed join mappings: [{:join-key, :col-key, :ref-key}]
                                   join-mappings (mapv (fn [[col-key ref-key]]
                                                         {:join-key (strip-id-suffix col-key)
                                                          :col-key  col-key
                                                          :ref-key  ref-key})
                                                       ref-cols)
                                   output (vec (concat (keys non-pk-cols)
                                                       (map (fn [{:keys [join-key ref-key]}]
                                                              {join-key [ref-key]})
                                                            join-mappings)))
                                   resolver-id (keyword "com.biffweb.sqlite"
                                                        (str (name table-key) "-resolver"))]]
       (biff.graph/resolver
        {:id         resolver-id
         :input      [pk-key]
         :output     output
         :batch      true
         :resolve-fn (fn [ctx inputs]
                       (let [ids         (mapv pk-key inputs)
                             results     (execute ctx {:select :*
                                                       :from   table-key
                                                       :where  [:in pk-key ids]})
                             process-row (fn [row]
                                           (let [row (dissoc row pk-key)
                                                 row (reduce
                                                      (fn [row {:keys [join-key col-key ref-key]}]
                                                        (let [ref-val (get row col-key)]
                                                          (cond-> row
                                                            (some? ref-val) (assoc join-key {ref-key ref-val}))))
                                                      row
                                                      join-mappings)]
                                             (into {} (filter (fn [[_ v]] (some? v))) row)))
                             id->result  (into {} (map (juxt pk-key process-row)) results)]
                         (mapv (fn [input]
                                 (get id->result (get input pk-key) {}))
                               inputs)))})))))
