(ns com.biffweb.sqlite
  (:require [com.biffweb.core :as biff.core]
            [com.biffweb.sqlite.impl.authorize :as impl.authorize]
            [com.biffweb.sqlite.impl.execute :as impl.execute]
            [com.biffweb.sqlite.impl.litestream :as impl.litestream]
            [com.biffweb.sqlite.impl.pool :as pool]
            [com.biffweb.sqlite.impl.resolver :as impl.resolver]
            [com.biffweb.sqlite.impl.schema :as impl.schema]
            [com.biffweb.sqlite.impl.sqldef :as impl.sqldef]
            [com.biffweb.sqlite.impl.system :as impl.system]))

(def ^:private column-schema
  (let [? {:optional true}]
    [:map
     [:type           [:enum :int :real :text :boolean :inst :uuid :enum :edn :blob]]
     [:primary-key  ? :boolean]
     [:unique       ? :boolean]
     [:unique-with  ? [:sequential :qualified-keyword]]
     [:required     ? :boolean]
     [:ref          ? :qualified-keyword]
     [:index        ? :boolean]
     [:extra-schema ? :any]
     [:enum-values  ? [:map-of :int :qualified-keyword]]]))

(def diff-schema
  [:vector
   [:map {:closed true}
    [:table :keyword]
    [:op [:enum :create :update :delete]]
    [:before [:maybe [:map-of :keyword :any]]]
    [:after [:maybe [:map-of :keyword :any]]]]])

(biff.core/register
 {:biff.sqlite/after-conn                   :any
  :biff.sqlite/authorize                    'ifn?
  :biff.sqlite/authorized-write-statement   impl.authorize/input-schema
  :biff.sqlite/authorized-write-statements  [:sequential :biff.sqlite/authorized-write-statement]
  :biff.sqlite/before-conn                  :any
  :biff.sqlite/bin-dir                      :string
  :biff.sqlite/columns                      [:map-of :qualified-keyword column-schema]
  :biff.sqlite/db-path                      :string
  :biff.sqlite/diff                         diff-schema
  :biff.sqlite/extra-init-sql               [:sequential :string]
  :biff.sqlite/litestream-access-key-id     :string
  :biff.sqlite/litestream-bucket            :string
  :biff.sqlite/litestream-dir               :string
  :biff.sqlite/litestream-endpoint          :string
  :biff.sqlite/litestream-region            :string
  :biff.sqlite/litestream-secret-access-key :biff.core/secret
  :biff.sqlite/litestream-version           :string
  :biff.sqlite/read-pool                    :any
  :biff.sqlite/schema-path                  :string
  :biff.sqlite/sqldef-version               :string
  :biff.sqlite/statement                    [:or
                                             :string
                                             [:cat :string [:* :any]]
                                             map?]
  :biff.sqlite/statements                   [:sequential :biff.sqlite/statement]
  :biff.sqlite/write-conn                   :any})

(defn schema-sql
  "Returns an SQL string for initializing the tables defined by `columns`.

   Used by use-sqldef (and use-sqlite). All tables use STRICT mode."
  {:arglists '([{:biff.sqlite/keys [columns]}])}
  [ctx]
  (impl.schema/schema-sql ctx))

(defn use-sqlite
  "A wrapper component that calls use-litestream, use-sqldef, then use-conn."
  [ctx]
  (impl.system/use-sqlite ctx))

(defn use-litestream
  "Uses litestream to backup/restore the database.

   Only takes effect if litestream-access-key-id is set. If it is, at least
   litestream-secret-access-key and litestream-bucket must also be set.

   If no file yet exists at db-path, calls `litestream restore` to initialize
   the DB from remote object storage. Then runs `litestream replicate` in the
   background to stream local database changes to remote object storage while
   your application runs."
  {:arglists '([{:biff.sqlite/keys [bin-dir
                                    db-path
                                    litestream-access-key-id
                                    litestream-bucket
                                    litestream-dir
                                    litestream-endpoint
                                    litestream-region
                                    litestream-secret-access-key
                                    litestream-version]}])}
  [ctx]
  (impl.litestream/use-litestream ctx))

(defn use-sqldef
  "Generates schema from `columns` and applies it with sqldef.

   Only `columns` is required; other keys have defaults. `extra-init-sql` may be
   used to append arbitrary statements to the SQL generated from `columns`.
   Generated schema is written to `schema-path`.

   sqldef (sqlite3def, specifically) will be installed if the specified version
   isn't available."
  {:arglists '([{:biff.sqlite/keys [bin-dir
                                    columns
                                    db-path
                                    extra-init-sql
                                    schema-path
                                    sqldef-version]}])}
  [ctx]
  (impl.sqldef/use-sqldef ctx))

(defn use-conn
  "Adds read/write database connections to the system map.

   The returned system map includes :biff.sqlite/read-pool and
   :biff.sqlite/write-conn. The read pool is a hikari connection pool with the
   default options.

   The following PRAGMAs are set on each connection:

   - journal_mode = WAL
   - busy_timeout = 5000
   - foreign_keys = ON
   - synchronous  = NORMAL"
  {:arglists '([{:biff.sqlite/keys [db-path]}])}
  [ctx]
  (pool/use-conn ctx))

(defn execute
  "Executes a sqlite statement, applying type coercion and validation.

   read-pool and write-conn are required.

   If `statement` is a HoneySQL map, first applies best-effort Malli validation
   to :set / :values. Malli schema is generated from `columns` -> :type and
   :extra-schema.

   Statement parameters are converted from rich types to underlying sqlite types
   based on the parameter values (e.g. booleans are always converted to 0 or 1,
   etc). Keywords are assumed to be enum values and must be defined in
   `columns`.

   After the statement is executed, query results are converted back to rich
   types by matching the returned column names to keys in `columns`. No type
   coercion will be applied for columns not in `columns`. You may use qualified
   keywords as column aliases to get type coercion to apply:

     {:select [[[:max :user/joined-at] :user/joined-at]], ...}

   (`execute` applies pre- and post-processing to make qualified keywords work
   as column aliases since that doesn't work when using plain/default HoneySQL +
   next.jdbc.)

   Write statements (inferred from the SQL string) are executed while holding a
   ReentrantLock to avoid contention. Afterward, :biff.core/on-tx is called if
   set. on-tx receives `ctx` as it was passed to this function."
  {:arglists '([{:biff.sqlite/keys [columns read-pool write-conn]
                 :biff.core/keys   [on-tx]
                 :as               ctx}
                statement])}
  [ctx statement]
  (impl.execute/execute ctx statement))

(defn execute-tx
  "Like execute, but takes a sequence of statements and runs them in a
   transaction. Returns a vector of the results."
  [ctx statements]
  (impl.execute/execute-tx ctx statements))

(defn authorized-write
  "Executes a write sqlite statement, rejecting statements that violate the
   application's authorization rules.

   Similar to execute, but only accepts write statements which must be formatted
   as HoneySQL maps (see :biff.sqlite/authorized-write-statement).

   Establishes a read transaction first, then executes the statement inside a
   separate write transaction. Generates a diff data structure which includes
   the values of each affected record before and after the write (see
   :biff.sqlite/diff). Calls `(authorize ctx diff)` (see
   :biff.sqlite/authorize). The `ctx` map passed to `authorize` also includes
   the read and write transactions (under :biff.sqlite/before-conn and
   :biff.sqlite/after-conn). If `authorize` doesn't return a truthy value,
   aborts the transaction and throws an exception.

   `authorize` must be defined by the application.

   On success, calls `on-tx` and then returns the diff."
  {:arglists '([{:biff.sqlite/keys [authorize
                                    columns
                                    write-conn
                                    read-pool]
                 :biff.core/keys   [on-tx]
                 :as               ctx}
                statement])}
  [ctx statement]
  (impl.authorize/authorized-write ctx statement))

(defn authorized-write-tx
  "Like authorized-write, but takes a sequence of statements. Returns the diff."
  [ctx statements]
  (impl.authorize/authorized-write-tx ctx statements))

(def
  ^{:doc "A biff.fx handlers map. Contains :biff.sqlite.fx/execute and
          :biff.sqlite.fx/authorized-write."}
  fx-handlers
  impl.system/fx-handlers)

(defn module
  "Returns a biff.core module.

   - provides :biff.fx/handlers in the module
   - collects :biff.sqlite/columns from other modules
   - provides some key-value store functions in the system map:
     :biff.core/kv-get, :biff.core/kv-set, :biff.core/kv-list.
   - provides :biff.core/wrap-read-tx in the system map."
  []
  (impl.system/module))

(defn make-resolvers
  "Returns a sequence of biff.graph resolvers, one for each table.

   Each resolver takes the primary key as input and returns all the other
   columns as output. Columns with :ref are returned as joins. If a ref column
   ends in `-id`, that column is returned as a regular non-join attribute and an
   additional join attribute without the `-id` suffix is also returned:

     :user/pet-id {:ref :pet/id, ...}
     ;; ->
     :output [:user/pet-id
              {:user/pet [:pet/id]}]

   All resolvers have `:batch true`.

   Since `module` provides :biff.core/wrap-read-tx, if you use `module`,
   biff.graph queries will run inside a read transaction and thus the resolvers
   will all see a consistent view of the database."
  {:arglists '([{:biff.sqlite/keys [columns]}])}
  [ctx]
  (impl.resolver/make-resolvers ctx))
