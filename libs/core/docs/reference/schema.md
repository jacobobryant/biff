# Schema

Not all schema keys listed here are actually used in biff.core. some of them
(like the `:biff.core/kv-*` keys) are only defined here as a common interface
for other libraries.

### :biff.core/init

`(fn [modules-var]) => {...}`. See [start](../api/com.biffweb.core.md#start).

### :biff.core/stop

`(fn []) => nil`. See [start](../api/com.biffweb.core.md#start) and
[stop](../api/com.biffweb.core.md#stop).

### :biff.core/secret

A `Delay`-like value returned by
[secret-delay](../api/com.biffweb.core.md#secret-delay). Meant to be used as
a schema value:

```clojure
(biff.core/register {:com.example/api-key :biff.core/secret})
```

### :biff.core/kv-set

`(fn [ctx namespace key value]) => nil`

Sets the given key in `namespace` to `value`. A nil value should delete the
key, i.e. a call to `:biff.core/kv-list` should only return keys with non-nil
values.

- `namespace`: qualified keyword, e.g. `:com.example/things`. The keyword's
  namespace should be owned by whatever library or application defines the
  keyword.
- `key`: string.
- `value`: any Clojure value that can be round-tripped through `pr-str` ->
`clojure.edn/read-string` without custom options.

Implementers may use the :biff.core/kv-namespace and :biff.core/kv-key
schemas for validation.

### :biff.core/kv-get

`(fn [ctx namespace key]) => value`

Returns the value for the given key in `namespace.` If `key` is unset,
returns nil. See `:biff.core/kv-set`.

Implementers may use the :biff.core/kv-namespace and :biff.core/kv-key
schemas for validation.

### :biff.core/kv-list

```
(fn [ctx namespace])
(fn [ctx namespace key-prefix]), => [key1, key2, ...]
```

Returns a sequence of sorted keys in the given namespace. See
`:biff.core/kv-set`.

- `key-prefix`: string. If set, returns only the keys beginning with this
prefix.

Implementers may use the :biff.core/kv-namespace and :biff.core/kv-prefix
schemas for validation.

### :biff.core/on-tx

`(fn [ctx])`

A function to be called after a database transaction has been submitted.
There is no specification that `ctx` include any data about the transaction.
See com.biffweb.core/module.
