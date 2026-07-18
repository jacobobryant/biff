# Library Schema

### :biff.xtdb/authorize

`(fn [ctx diff]) -> boolean`. This function is defined by the application and
passed to [authorized-write](../api/com.biffweb.xtdb.md#authorized-write),
which uses it to check if the current transaction satisfies the application's
authorization rules.

See `:biff.xtdb/diff`. `ctx` includes the diff under `:biff.xtdb/diff`.

### :biff.xtdb/columns

`{<column keyword> <options map>}`. Options:

```clojure
:schema ; optional Malli schema.
:ref    ; optional qualified keyword. Another column keyword.
```

`:schema` is used by [columns->schema](../api/com.biffweb.xtdb.md). `:ref` is
used by [make-resolvers](../api/com.biffweb.xtdb.md#make-resolvers).

### :biff.xtdb/config

An XTDB node config map. If set, [expand-config](../api/com.biffweb.xtdb.md#expand-config)
returns this value directly and ignores the other storage/log config keys.

### :biff.xtdb/connection-pool

A HikariDataSource connected to `:biff.xtdb/node`. Created by
[use-xtdb](../api/com.biffweb.xtdb.md#use-xtdb).

### :biff.xtdb/disk-cache-max-bytes

Integer, or nil. Default 10 GB.

For remote storage, this sets the disk cache size. Set it to nil if you don't
want `expand-config` to include a disk cache size.

### :biff.xtdb/diff

A vector of maps, each of which has the keys:

```clojure
:table   ; Keyword, e.g. :user
:op      ; :create, :update, :delete, or :erase
:before  ; Map. The value of a database record before a transaction ran.
         ; nil for :create operations.
:after   ; Map. The value of a database record after a transaction ran.
         ; nil for :delete and :erase operations.
```

### :biff.xtdb/latest-system-time

Instant. Passed to `:biff.core/on-tx` after a transaction has been indexed.

### :biff.xtdb/log

Keyword. `:memory`, `:local`, or `:kafka`. Default `:local`.

### :biff.xtdb/log-bootstrap-servers

String. Kafka bootstrap servers. Default `localhost:9092`.

### :biff.xtdb/log-epoch

Integer. Default `0`.

### :biff.xtdb/log-topic

String. Kafka topic name. Default `xtdb-log`.

### :biff.xtdb/hikari-config

A HikariConfig object. Optional. If set, [use-xtdb](../api/com.biffweb.xtdb.md#use-xtdb)
uses this when creating `:biff.xtdb/connection-pool`.

### :biff.xtdb/memory-cache-max-bytes

Integer, or nil. For local and remote storage, this sets the memory cache size.
If not set, XTDB will still create a memory cache using its default size.

### :biff.xtdb/node

An XTDB node.

### :biff.xtdb/poll-now

Internal function used by [submit-tx](../api/com.biffweb.xtdb.md#submit-tx) to
notify `:biff.core/on-tx` after async transactions have been indexed.

### :biff.xtdb/snapshot-token

String. Passed to XTDB queries so multiple queries can view a consistent
database snapshot. Normally set by `:biff.core/wrap-read-tx`, which is provided
by [module](../api/com.biffweb.xtdb.md#module).

### :biff.xtdb/storage

Keyword. `:memory`, `:local`, or `:remote`. Default `:local`.

### :biff.xtdb/storage-access-key

String. An access key for S3-compatible remote storage.

### :biff.xtdb/storage-bucket

String. A bucket for S3-compatible remote storage.

### :biff.xtdb/storage-endpoint

String. An endpoint for S3-compatible remote storage.

### :biff.xtdb/storage-secret-key

A string wrapped with
[`biff.core/secret-delay`](/libs/core/docs/api/com.biffweb.core.md#secret-delay).
A secret key for S3-compatible remote storage.
