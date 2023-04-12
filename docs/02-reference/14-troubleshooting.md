---
title: Troubleshooting
---

## UnsatisfiedLinkError

This is usually a problem with RocksDB. As a quick fix, you can switch to LMDB. Add the LMDB dependency
to `deps.edn` and remove the RocksDB dependency:

```clojure
{:deps {com.biffweb/biff {...
                          :exclusions [com.xtdb/xtdb-rocksdb]}
        com.xtdb/xtdb-lmdb {:mvn/version "1.21.0-beta2"}
...
```

Then update `config.edn`:

```clojure
{:prod {:biff.xtdb/kv-store :lmdb
...
```
