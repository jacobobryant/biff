# biff.sqlite schema

### :biff.sqlite/after-conn

A database connection (transaction) that includes writes made by
[authorized-write](../api/com.biffweb.sqlite.md#authorized-write). Passed to the
`:biff.sqlite/authorize` function.

### :biff.sqlite/aeuthorize

`(fn [ctx diff]) -> boolean`. This function is defined by the application and
passed to [authorized-write](../api/com.biffweb.sqlite.md#authorized-write),
which uses it to check if the current transaction satisfies the application's
authorization rules.

See `:biff.sqlite/diff`. `ctx` includes `:biff.sqlite/after-conn` and
`:biff.sqlite/before-conn`.

### :biff.sqlite/authorized-write-statement

A HoneySQL map with several restrictions:

- `REPLACE INTO` is not allowed.
- Primary key changes are not allowed in `UPDATE` statements.

These restrictions make it easier to generate a `:biff.sqlite/diff` value.

### :biff.sqlite/before-conn

A database connection (transaction) that _does not_ include writes made by
[authorized-write](../api/com.biffweb.sqlite.md#authorized-write). Passed to the
`:biff.sqlite/authorize` function.

### :biff.sqlite/bin-dir

String. Default `target/bin`. The directory in which to store binaries for
litestream and sqldef.

### :biff.sqlite/columns

`{<column keyword> <options map>}`. See [database schema](db-schema.md).

### :biff.sqlite/db-path

String. Default `storage/sqlite/main.db`. The path to store the sqlite database.

### :biff.sqlite/diff

A vector of maps, each of which has the keys:

```clojure
:table   ; Keyword, e.g. :my-table, corresponding
:op      ; :create, :update, or :delete
:before  ; Map. The value of a database record before a transaction ran.
         ; nil for :create operations.
:after   ; Map. The value of a database record after a transaction ran.
         ; nil for :delete operations.
```

### :biff.sqlite/extra-init-sql

A sequence of strings containing arbitrary SQL. Passed to sqldef 

### :biff.sqlite/litestream-access-key-id

String. An access key for an S3-compatible service.

### :biff.sqlite/litestream-bucket

String. A bucket for an S3-compatible service.

### :biff.sqlite/litestream-dir

String. Default `storage/litestream`. The directory in which to store runtime
files, such as the auto-generated litestream config file.

### :biff.sqlite/litestream-endpoint

String. An endpoint for an S3-compatible service.

### :biff.sqlite/litestream-region

String. A region for an S3-compatible service.

### :biff.sqlite/litestream-secret-access-key

A string wrapped with
[`biff.core/secret-delay`](/libs/core/docs/api/com.biffweb.core.md#secret-delay).
A secret access key for an S3-compatible service.

### :biff.sqlite/litestream-version

String. The version of litestream to install and use. Default `0.5.9`.

### :biff.sqlite/read-pool

A database connection pool that's used for read statements.

### :biff.sqlite/schema-path

String. Default `resources/schema.sql`. The path at which sqlite schema
(generated from `:biff.sqlite/columns` and passed to sqldef) will be stored. It
is intended to be checked into source.

### :biff.sqlite/sqldef-version

String. The version of sqlite3def to install and use. Default `3.10.1`.

### :biff.sqlite/statement

A string (SQL), vector (SQL with params, as accepted by next.jdbc), or map
(HoneySQL).

Maps passed to HoneySQL are preprocessed (and the query results are
post-processed) so that namespaced keywords may be used as column aliases in
`SELECT` statements, e.g. `{:select [[... :my-table/color]], ...}`. This aids
with type coercion since coercion is only applied to columns that are defined in
`:biff.sqlite/columns`.

### :biff.sqlite/write-conn

A database connection that's used for write statements.
