# com.biffweb.xtdb API

### expand-config

[view source](../../src/com/biffweb/xtdb.clj#L45)

```
(expand-config #:biff.xtdb{:keys [config storage log storage-bucket storage-endpoint storage-access-key storage-secret-key disk-cache-max-bytes memory-cache-max-bytes log-bootstrap-servers log-topic log-epoch]})

Returns an XTDB node config map.

If `:biff.xtdb/config` is set, returns it as-is. Otherwise, returns a config
map based on `storage` and `log` plus their related options.

`storage` may be `:memory`, `:local`, or `:remote`. `log` may be `:memory`,
`:local`, or `:kafka`. Both default to `:local`.
```

### q

[view source](../../src/com/biffweb/xtdb.clj#L68)

```
(q {:biff.xtdb/keys [connection-pool node snapshot-token], :as ctx} query)
(q {:biff.xtdb/keys [connection-pool node snapshot-token], :as ctx} query opts)

Wrapper for xtdb.api/q.

If query is a map, formats it with HoneySQL, adding support for qualified
keywords (e.g. :user/email gets converted to :user$email).

Includes snapshot-token in the query opts.
```

### execute-tx

[view source](../../src/com/biffweb/xtdb.clj#L85)

```
(execute-tx ctx tx-ops)
(execute-tx {:biff.xtdb/keys [node connection-pool], :as ctx} tx-ops tx-opts)

Wrapper for xtdb.api/execute-tx.

- Supports Biff's custom transaction operations.
- Enforces Malli schema for :put-docs and :patch-docs operations via
  biff.core/validate.
- Calls :biff.core/on-tx if set.

Returns a map with :tx-id and :system-time.
```

### submit-tx

[view source](../../src/com/biffweb/xtdb.clj#L103)

```
(submit-tx ctx tx-ops)
(submit-tx {:biff.xtdb/keys [node connection-pool], :as ctx} tx-ops tx-opts)

Wrapper for xtdb.api/submit-tx

- Supports Biff's custom transaction operations.
- Enforces Malli schema for :put-docs and :patch-docs operations via
  biff.core/validate.
- Calls :biff.core/on-tx if set.

Returns a map with :tx-id.
```

### authorized-write

[view source](../../src/com/biffweb/xtdb.clj#L120)

```
(authorized-write {:biff.xtdb/keys [authorize node], :as ctx} tx-ops)
(authorized-write {:biff.xtdb/keys [authorize node], :as ctx} tx-ops tx-opts)

Wrapper for xt/submit-tx that enforces authorization rules.

The :biff.xtdb/authorize function must be set. If it doesn't return true,
the transaction is rejected.

Only :put-docs, :patch-docs, :delete-docs, and :erase-docs operations are
accepted.

On success, returns the result of submit-tx with :biff.xtdb/diff added.
```

### prefix-uuid

[view source](../../src/com/biffweb/xtdb.clj#L137)

```
(prefix-uuid uuid-prefix uuid-rest)

Returns a UUID made from the first four characters of `uuid-prefix` and the
rest of `uuid-rest`.
```

### columns->schema

[view source](../../src/com/biffweb/xtdb.clj#L143)

```
(columns->schema columns)

Returns a biff.core schema registry map for `columns`.

For each entry in `columns` that has a `:schema` value, the column keyword is
mapped to that schema. This is useful with `biff.core/register` before
calling execute-tx or submit-tx.
```

### make-resolvers

[view source](../../src/com/biffweb/xtdb.clj#L152)

```
(make-resolvers columns)

Returns a sequence of biff.graph resolvers, one for each table.

Each resolver takes an alias of :xt/id as input, which has the form
:<table>/id (e.g. :user/id). All other keys in `columns` with the same
namespace are included in the output.

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

[view source](../../src/com/biffweb/xtdb.clj#L172)

```
A biff.fx handlers map. Contains :biff.xtdb.fx/execute-tx,
:biff.xtdb.fx/submit-tx, and :biff.xtdb.fx/authorized-write.
```

### module

[view source](../../src/com/biffweb/xtdb.clj#L178)

```
(module)

On start, starts an in-process XTDB node and a connection pool. Include
:biff.xtdb/component in your components.

Passes the system map to `expand-config`. When the system map is passed to
`q`, `execute-tx`, `submit-tx`, or `authorized-write`, those functions:

- Use the connection pool.
- Trigger a call to :biff.core/on-tx, if set.

Adds :biff.xtdb/node and :biff.xtdb/connection-pool to the system map.

Also:

- provides :biff.fx/handlers in the module
- provides key-value store functions in the system map:
  :biff.core/kv-get, :biff.core/kv-set, :biff.core/kv-list.
- provides :biff.core/wrap-db-snapshot in the system map.
```
