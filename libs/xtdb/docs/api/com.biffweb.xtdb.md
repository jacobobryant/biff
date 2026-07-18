# com.biffweb.xtdb API

### expand-config

[view source](../../src/com/biffweb/xtdb.clj#L39)

```
(expand-config #:biff.xtdb{:keys [config storage log storage-bucket storage-endpoint storage-access-key storage-secret-key disk-cache-max-bytes memory-cache-max-bytes log-bootstrap-servers log-topic log-epoch]})

Returns an XTDB node config map.

If `:biff.xtdb/config` is set, returns it as-is. Otherwise, returns a config
map based on `storage` and `log` plus their related options.

`storage` may be `:memory`, `:local`, or `:remote`. `log` may be `:memory`,
`:local`, or `:kafka`. Both default to `:local`.
```

### use-xtdb

[view source](../../src/com/biffweb/xtdb.clj#L62)

```
(use-xtdb ctx)

Starts an XTDB node and adds it to the system map.

Passes ctx to expand-config.

The returned system map includes `:biff.xtdb/node`,
`:biff.xtdb/connection-pool`, and `:biff.xtdb/poll-now`. `poll-now` is an
internal function used by submit-tx to notify `:biff.core/on-tx` after async
transactions have been indexed.

Adds a stop function under `:biff.core/stop` which closes the connection pool
and node.
```

### q

[view source](../../src/com/biffweb/xtdb.clj#L77)

```
(q {:biff.xtdb/keys [connection-pool node snapshot-token], :as ctx} query)
(q {:biff.xtdb/keys [connection-pool node snapshot-token], :as ctx} query opts)

Convenience wrapper for xtdb.api/q.

If query is a map, formats it with HoneySQL, adding support for qualified
keywords (e.g. :user/email gets converted to :user$email).

Includes snapshot-token in the query opts.
```

### execute-tx

[view source](../../src/com/biffweb/xtdb.clj#L91)

```
(execute-tx ctx tx-ops)
(execute-tx ctx tx-ops tx-opts)

Executes a transaction and waits for it to be indexed.

Expands Biff's custom transaction ops, validates docs in `:put-docs` and
`:patch-docs` operations with `biff.core/validate`, executes the transaction
with XTDB, then calls `:biff.core/on-tx` if set. Uses
`:biff.xtdb/connection-pool` if set.

Returns XTDB's transaction key, including `:tx-id` and `:system-time`.
```

### submit-tx

[view source](../../src/com/biffweb/xtdb.clj#L107)

```
(submit-tx ctx tx-ops)
(submit-tx ctx tx-ops tx-opts)

Submits a transaction without waiting for it to be indexed.

Expands Biff's custom transaction ops and validates docs in `:put-docs` and
`:patch-docs` operations with `biff.core/validate`, like execute-tx.

Returns `{:tx-id ...}`. If `use-xtdb` has been run and `:biff.core/on-tx`
is set, on-tx will be called in the background after the transaction has
been indexed. Uses `:biff.xtdb/connection-pool` if set.
```

### authorized-write

[view source](../../src/com/biffweb/xtdb.clj#L123)

```
(authorized-write {:biff.xtdb/keys [authorize node], :as ctx} tx-ops)
(authorized-write {:biff.xtdb/keys [authorize node], :as ctx} tx-ops tx-opts)

Submits a transaction only if it passes the application's authorization rules.

`:biff.xtdb/authorize` must be set to `(fn [ctx diff])`. The diff is also
added to `ctx` under `:biff.xtdb/diff` before authorize is called.

Only transaction operations with known diff semantics are accepted:
`:put-docs`, `:patch-docs`, `:delete-docs`, and `:erase-docs`. Custom ops
are expanded before this check; the built-in custom ops are not accepted
because they expand to SQL assertions. If authorize returns a falsey value,
throws an exception and does not submit the transaction.

On success, returns the result of submit-tx with `:biff.xtdb/diff` added.
```

### prefix-uuid

[view source](../../src/com/biffweb/xtdb.clj#L143)

```
(prefix-uuid uuid-prefix uuid-rest)

Returns a UUID made from the first four characters of `uuid-prefix` and the
rest of `uuid-rest`.
```

### columns->schema

[view source](../../src/com/biffweb/xtdb.clj#L149)

```
(columns->schema columns)

Returns a biff.core schema registry map for `columns`.

For each entry in `columns` that has a `:schema` value, the column keyword is
mapped to that schema. This is useful with `biff.core/register` before
calling execute-tx or submit-tx.
```

### make-resolvers

[view source](../../src/com/biffweb/xtdb.clj#L158)

```
(make-resolvers #:biff.xtdb{:keys [columns]})

Returns a sequence of biff.graph resolvers, one for each table.

Each resolver takes the table's primary key as input and returns all the
other columns as output. The primary key for table `:user` is assumed to be
`:user/id`, corresponding to XTDB's `:xt/id`.

Columns with `:ref` are returned as joins. If a ref column ends in `-id`,
that column is returned as a regular non-join attribute and an additional
join attribute without the `-id` suffix is also returned:

  :user/pet-id {:ref :pet/id, ...}
  ;; ->
  :output [:user/pet-id
           {:user/pet [:pet/id]}]

All resolvers have `:batch true`.
```

### fx-handlers

[view source](../../src/com/biffweb/xtdb.clj#L179)

```
A biff.fx handlers map. Contains :biff.xtdb.fx/execute-tx,
:biff.xtdb.fx/submit-tx, and :biff.xtdb.fx/authorized-write.
```

### module

[view source](../../src/com/biffweb/xtdb.clj#L185)

```
(module)

Returns a biff.core module.

- provides :biff.fx/handlers in the module
- provides key-value store functions in the system map:
  :biff.core/kv-get, :biff.core/kv-set, :biff.core/kv-list.
- provides :biff.core/wrap-read-tx in the system map.
```
