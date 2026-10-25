(ns com.biffweb.sqlite
  (:require [com.biffweb.core :as biff.core]
            [com.biffweb.sqlite.impl.authorize :as impl.authorize]
            [com.biffweb.sqlite.impl.execute :as impl.execute]
            [com.biffweb.sqlite.impl.resolver :as impl.resolver]
            [com.biffweb.sqlite.impl.schema :as impl.schema]
            [com.biffweb.sqlite.impl.system :as impl.system]))

(def ^:private column-schema
  (let [? {:optional true}]
    [:map
     [:type           [:enum :int :real :text :boolean :inst
                       :uuid :enum :edn :blob]]
     [:primary-key  ? :boolean]
     [:unique       ? :boolean]
     [:unique-with  ? [:sequential :qualified-keyword]]
     [:required     ? :boolean]
     [:default      ? :any]
     [:ref          ? :qualified-keyword]
     [:backref      ? :qualified-keyword]
     [:backref-many ? :qualified-keyword]
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
 {:biff.sqlite/after-conn                 :any
  :biff.sqlite/authorize                  'ifn?
  :biff.sqlite/authorized-write-statement impl.authorize/input-schema

  :biff.sqlite/authorized-write-statements
  [:sequential :biff.sqlite/authorized-write-statement]

  :biff.sqlite/before-conn :any

  :biff.sqlite/columns
  [:map-of :qualified-keyword column-schema]

  :biff.sqlite/db-path :string
  :biff.sqlite/diff    diff-schema

  :biff.sqlite/extra-init-sql               [:sequential :string]
  :biff.sqlite/litestream-access-key-id     :string
  :biff.sqlite/litestream-bucket            :string
  :biff.sqlite/litestream-dir               :string
  :biff.sqlite/litestream-endpoint          :string
  :biff.sqlite/litestream-replica-path      :string
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

   Used by `sqldef-module` during startup. All tables use STRICT mode."
  {:arglists '([{:biff.sqlite/keys [columns]}])}
  [ctx]
  (impl.schema/schema-sql ctx))

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

(defn execute-one
  "Calls `execute` and returns the first row (or nil)."
  [ctx statement]
  (first (impl.execute/execute ctx statement)))

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
  ^{:doc "A biff.fx handlers map. Contains :biff.sqlite.fx/execute,
          :biff.sqlite.fx/execute-one, :biff.sqlite.fx/execute-tx,
          :biff.sqlite.fx/authorized-write, and
          :biff.sqlite.fx/authorized-write-tx."}
  fx-handlers
  impl.system/fx-handlers)

(defn module
  "Returns a biff.core module. Module ID is :biff.sqlite/module.

   Combines config-module, litestream-module, sqldef-module, and conn-module."
  {:arglists '([]
               [{:biff.sqlite/keys [extra-init-sql authorize columns]}])}
  [& args]
  (apply impl.system/module args))

(defn config-module
  "Returns a biff.core module.

   - collects :biff.sqlite/columns from other modules and merges with the given
     `columns`.
   - provides :biff.fx/handlers and :biff.graph/resolvers in the module.
   - provides `authorize` and `extra-init-sql` in the system map.
   - provides some DB-related biff.core functions in the system map:
     :biff.core/kv-get, :biff.core/kv-set, :biff.core/kv-list,
     :biff.core/wrap-db-snapshot"
  {:arglists '([]
               [{:biff.sqlite/keys [extra-init-sql authorize columns]}])}
  [& args]
  (apply impl.system/config-module args))

(defn litestream-module
  "On start, uses litestream to backup/restore the database. Module ID is
   :biff.sqlite/litestream-module.

   Uses these keys from the system map:

     :biff.sqlite/db-path
     :biff.sqlite/litestream-access-key-id
     :biff.sqlite/litestream-bucket
     :biff.sqlite/litestream-dir
     :biff.sqlite/litestream-endpoint
     :biff.sqlite/litestream-replica-path
     :biff.sqlite/litestream-region
     :biff.sqlite/litestream-secret-access-key
     :biff.sqlite/litestream-version

   Only takes effect if litestream-access-key-id is set. If it is, at least
   litestream-secret-access-key and litestream-bucket must also be set.

   If no file yet exists at db-path, calls `litestream restore` to initialize
   the DB from remote object storage. Then runs `litestream replicate` in the
   background to stream local database changes to remote object storage while
   your application runs."
  []
  (impl.system/litestream-module))

(defn sqldef-module
  "On start, generates schema from `columns` and applies it with sqldef.
   Module ID is :biff.sqlite/sqldef-module.

   Uses these keys from the system map:

     :biff.sqlite/columns
     :biff.sqlite/db-path
     :biff.sqlite/extra-init-sql
     :biff.sqlite/schema-path
     :biff.sqlite/sqldef-version

   Only `columns` is required; other keys have defaults. `extra-init-sql` may be
   used to append arbitrary statements to the SQL generated from `columns`.
   Generated schema is written to `schema-path`.

   sqldef (sqlite3def, specifically) will be installed if the specified version
   isn't available."
  []
  (impl.system/sqldef-module))

(defn conn-module
  "On startup, adds read/write database connections to the system map. Module ID
   is :biff.sqlite/conn-module.

   Uses :biff.sqlite/db-path from the system map.

   The returned system map includes :biff.sqlite/read-pool and
   :biff.sqlite/write-conn. The read pool is a hikari connection pool with the
   default options.

   The following PRAGMAs are set on each connection:

   - journal_mode = WAL
   - busy_timeout = 5000
   - foreign_keys = ON
   - synchronous  = NORMAL

   Also sets these keys on the system map:

     :biff.core/kv-get
     :biff.core/kv-set
     :biff.core/kv-list
     :biff.core/wrap-db-snapshot"
  []
  (impl.system/conn-module))

(defn make-resolvers
  "Returns biff.graph resolvers for primary keys, unique keys, and
   backreferences.

   Each table gets a resolver that takes the primary key as input and returns
   all the other columns as output. Columns with :ref are returned as joins. If
   a ref column ends in `-id`, that column is returned as a regular non-join
   attribute and an additional join attribute without the `-id` suffix is also
   returned:

     :user/pet-id {:ref :pet/id, ...}
     ;; ->
     :output [:user/pet-id
              {:user/pet [:pet/id]}]

   EDN columns whose :extra-schema describes a map or collection of maps are
   returned as joins with a `[:*]` subquery.

   A column with :unique or :unique-with gets a resolver that looks up its
   table's primary key given a value(s) for the unique key(s).

   :ref columns that have :backref or :backref-many set get a resolver that
   goes from the entity targeted by :ref back to the current column's entity:

     ;; given this column:
     :user/pet {:type :uuid, :ref :pet/id, :backref :pet/user}
     ;; you get a resolver with this:
     :input [:pet/id], :output [{:pet/user [:user/id]}]

   All resolvers have `:batch true`.

   When using `module` or `conn-module`, biff.graph queries will run inside a
   read transaction and thus the resolvers will all see a consistent view of the
   database (see :biff.core/wrap-db-snapshot)."
  [columns]
  (impl.resolver/make-resolvers columns))
