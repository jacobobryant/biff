# com.biffweb.sqlite API

### schema-sql

[view source](../../src/com/biffweb/sqlite.clj#L68)

```
(schema-sql #:biff.sqlite{:keys [columns]})

Returns an SQL string for initializing the tables defined by `columns`.

Used by use-sqldef (and use-sqlite). All tables use STRICT mode.
```

### use-sqlite

[view source](../../src/com/biffweb/sqlite.clj#L76)

```
(use-sqlite ctx)

A wrapper component that calls use-litestream, use-sqldef, then use-conn.
```

### use-litestream

[view source](../../src/com/biffweb/sqlite.clj#L81)

```
(use-litestream #:biff.sqlite{:keys [db-path litestream-access-key-id litestream-bucket litestream-dir litestream-endpoint litestream-region litestream-secret-access-key litestream-version]})

Uses litestream to backup/restore the database.

Only takes effect if litestream-access-key-id is set. If it is, at least
litestream-secret-access-key and litestream-bucket must also be set.

If no file yet exists at db-path, calls `litestream restore` to initialize
the DB from remote object storage. Then runs `litestream replicate` in the
background to stream local database changes to remote object storage while
your application runs.
```

### use-sqldef

[view source](../../src/com/biffweb/sqlite.clj#L102)

```
(use-sqldef #:biff.sqlite{:keys [columns db-path extra-init-sql schema-path sqldef-version]})

Generates schema from `columns` and applies it with sqldef.

Only `columns` is required; other keys have defaults. `extra-init-sql` may be
used to append arbitrary statements to the SQL generated from `columns`.
Generated schema is written to `schema-path`.

sqldef (sqlite3def, specifically) will be installed if the specified version
isn't available.
```

### use-conn

[view source](../../src/com/biffweb/sqlite.clj#L119)

```
(use-conn #:biff.sqlite{:keys [db-path]})

Adds read/write database connections to the system map.

The returned system map includes :biff.sqlite/read-pool and
:biff.sqlite/write-conn. The read pool is a hikari connection pool with the
default options.

The following PRAGMAs are set on each connection:

- journal_mode = WAL
- busy_timeout = 5000
- foreign_keys = ON
- synchronous  = NORMAL
```

### execute

[view source](../../src/com/biffweb/sqlite.clj#L136)

```
(execute {:biff.sqlite/keys [columns read-pool write-conn], :biff.core/keys [on-tx], :as ctx} statement)

Executes a sqlite statement, applying type coercion and validation.

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
set. on-tx receives `ctx` as it was passed to this function.
```

### execute-tx

[view source](../../src/com/biffweb/sqlite.clj#L171)

```
(execute-tx ctx statements)

Like execute, but takes a sequence of statements and runs them in a
transaction. Returns a vector of the results.
```

### authorized-write

[view source](../../src/com/biffweb/sqlite.clj#L177)

```
(authorized-write {:biff.sqlite/keys [authorize columns write-conn read-pool], :biff.core/keys [on-tx], :as ctx} statement)

Executes a write sqlite statement, rejecting statements that violate the
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

On success, calls `on-tx` and then returns the diff.
```

### authorized-write-tx

[view source](../../src/com/biffweb/sqlite.clj#L206)

```
(authorized-write-tx ctx statements)

Like authorized-write, but takes a sequence of statements. Returns the diff.
```

### fx-handlers

[view source](../../src/com/biffweb/sqlite.clj#L211)

```
A biff.fx handlers map. Contains :biff.sqlite.fx/execute and
:biff.sqlite.fx/authorized-write.
```

### module

[view source](../../src/com/biffweb/sqlite.clj#L217)

```
(module)

Returns a biff.core module.

- provides :biff.fx/handlers in the module
- collects :biff.sqlite/columns from other modules
- provides some key-value store functions in the system map:
  :biff.core/kv-get, :biff.core/kv-set, :biff.core/kv-list.
- provides :biff.core/wrap-db-snapshot in the system map.
```

### make-resolvers

[view source](../../src/com/biffweb/sqlite.clj#L228)

```
(make-resolvers columns)

Returns a sequence of biff.graph resolvers, one for each table.

Each resolver takes the primary key as input and returns all the other
columns as output. Columns with :ref are returned as joins. If a ref column
ends in `-id`, that column is returned as a regular non-join attribute and an
additional join attribute without the `-id` suffix is also returned:

  :user/pet-id {:ref :pet/id, ...}
  ;; ->
  :output [:user/pet-id
           {:user/pet [:pet/id]}]

All resolvers have `:batch true`.

Since `module` provides :biff.core/wrap-db-snapshot, if you use `module`,
biff.graph queries will run inside a read transaction and thus the resolvers
will all see a consistent view of the database.
```
