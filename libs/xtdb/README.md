# biff.xtdb

A convenience wrapper for using [XTDB v2](https://xtdb.com) in Biff/Clojure
applications.

- Start up an in-process node with high-level config defaults.
- Custom `:biff/upsert` and `:biff/assert-unique` transaction operations.
- Optionally enforce Malli schemas on write.
- Define centralized authorization rules for validating transactions.
- Integration with biff.core, biff.fx, and biff.graph.

### Dependency

```clojure
com.biffweb/xtdb {:mvn/version "2.0.0-rc18"}
```

biff.xtdb depends on XTDB 2.1.0 which requires Java <25. Per the XTDB docs,
you'll need to add these Java arguments to your deps.edn file:

```clojure
:jvm-opts ["--add-opens=java.base/java.nio=ALL-UNNAMED"
           "--enable-native-access=ALL-UNNAMED"
           "-Dio.netty.tryReflectionSetAccessible=true"]
```

### Status

This library will be a release candidate until all [the other Biff 2
libraries](/README.md) have been released. Until then there could be breaking
changes, but I don't anticipate any.

## Reference

- [Transaction Ops](docs/reference/transaction-ops.md)
- [Library Schema](docs/reference/library-schema.md)
- [API](docs/api/com.biffweb.xtdb.md)

## Usage

### System start up

You can start an XTDB node with `use-xtdb`. If you're using `biff.core`,
add `use-xtdb` to your components and add `(module)` to your modules.

```clojure
(require '[com.biffweb.xtdb :as biff.xtdb])

(def modules
  [(biff.xtdb/module)
   ...])

(def components
  [...
   biff.xtdb/use-xtdb
   ...])
```

If you're not using biff.core, you can wire things up manually:

```clojure
(comment
  (def ctx (biff.xtdb/use-xtdb
            {:biff.core/stop    []
             :biff.xtdb/log     :memory
             :biff.xtdb/storage :memory}))

  ;; close the XTDB node
  (let [[stop-fn] (:biff.core/stop ctx)]
    (stop-fn)))
```

By default, `use-xtdb` uses local disk storage under `storage/xtdb2/`. Set
`:biff.xtdb/storage` and `:biff.xtdb/log` to `:memory` for tests. See
[`expand-config`](docs/api/com.biffweb.xtdb.md#expand-config) and
[Library Schema](docs/reference/library-schema.md).

### Queries

Use `q` to run queries formatted with HoneySQL:

```clojure
(biff.xtdb/q ctx
  {:select [:xt/id :user/email]
   :from   [:user]
   :where  [:= :user/email "ada@example.com"]})
```

### Transactions

There are `submit-tx` and `execute-tx` wrappers which apply schema validation
via `biff.core/validate` (see [Schema](#schema)). They also support two custom
operations, `:biff/upsert` and `:biff/assert-unique`:

```clojure
(biff.xtdb/execute-tx
  ctx
  [[:biff/upsert :user [:user/email]
    {:user/email     "ada@example.com"
     :user/score     1
     :biff/on-update {:user/score 2}}]])
```

See [Transaction Ops](docs/reference/transaction-ops.md).

### Authorization rules

The `authorized-write` function takes transaction ops and passes a "diff" value
to an `authorize` function which you define:

```clojure
(defn can-update-user? [ctx before after]
  (let [user-id       (get-in ctx [:session :uid])
        editable-keys [:user/pet-id]]
    (and (= user-id (:xt/id before))
         (= (apply dissoc before editable-keys)
            (apply dissoc after editable-keys)))))

(defn authorize [ctx diff]
  (every? (fn [{:keys [table op before after]}]
            (case [table op]
              [:user :update] (can-update-user? ctx before after)
              false))
          diff))

;; With biff.core:
(def module
  {:biff.core/init (fn [_] {:biff.xtdb/authorize #'authorize})})
;; Without:
(def ctx {:biff.xtdb/authorize #'authorize, ...})

(biff.xtdb/authorized-write
  ctx
  [[:patch-docs :user
    {:xt/id       (get-in ctx [:session :uid])
     :user/pet-id ...}]])
```

See [`authorized-write`](docs/api/com.biffweb.xtdb.md#authorized-write) and
[`:biff.xtdb/authorize`](docs/reference/library-schema.md#biff-xtdb-authorize).

### biff.graph resolvers

Use `biff.xtdb/make-resolvers` to generate a biff.graph resolver for each of
your XTDB tables. First define a "columns map" including all the non-`:xt/id`
attributes:

```clojure
(def columns
  {:user/email  {}
   :user/score  {}
   :user/pet-id {:ref :pet/id}

   :pet/species {}})
```

The value of `:ref` is an `:xt/id` alias of the form `:<table>/id`. biff.graph
queries use these aliases instead of `:xt/id`.

```clojure
;; with biff.core:
(def module
  {:biff.graph/resolvers (biff.xtdb/make-resolvers
                          {:biff.xtdb/columns columns})})

;; without:
(def resolvers (concat
                (biff.xtdb/make-resolvers
                 {:biff.xtdb/columns columns})
                [...]))
(def ctx (merge (biff.graph/new-ctx resolvers) ...))


;; Then you can query XTDB via biff.graph:
(biff.graph/query
  ctx
  {:user/id ...}
  [:user/email
   :user/pet-id
   {:user/pet [:pet/id
               :pet/species]}])
```

See [`make-resolvers`](docs/api/com.biffweb.xtdb.md#make-resolvers).

### Schema

You can also use the columns map to define Malli schema which will be enforced
by `submit-tx`, `execute-tx`, and `authorized-write` (via `biff.core/validate`):

```clojure
(def columns
  {:user/email  {:schema :string}
   :user/score  {:schema :int}
   :user/pet-id {:schema :uuid
                 :ref    :pet/id}

   :pet/species {:schema [:enum :pet.species/iguana
                                :pet.species/tardigrade
                                :pet.species/rock]}})

(biff.core/register (biff.xtdb/columns->schema columns))
```

Schema enforcement happens on a best-effort basis; only `:put-docs` and
`:patch-docs` operations are validated.

The `:schema` values aren't used for XTDB schema or migrations; once registered,
they're used by `execute-tx` and `submit-tx` to validate docs before they're
submitted.

If you're using `biff.core`, you can register the Malli schemas and add the
columns map to your system with a module:

```clojure
(require '[com.biffweb.core :as biff.core])

(def module
  {:biff.core/init
   (fn [_]
     (biff.core/register (biff.xtdb/columns->schema columns))
     {:biff.xtdb/columns columns})})
```

If you're not using `biff.core`, call `biff.core/register` yourself and include
`:biff.xtdb/columns` in the ctx maps you pass to functions that need it:

```clojure
(biff.core/register (biff.xtdb/columns->schema columns))

(def ctx
  {:biff.xtdb/columns columns
   ...})
```

### biff.fx integration

`module` provides a `:biff.fx/handlers` map that includes:

- `:biff.xtdb.fx/execute-tx`
- `:biff.xtdb.fx/submit-tx`
- `:biff.xtdb.fx/authorized-write`

## Tips

- `authorized-write` should be used in the context of a particular user, i.e.
  when you're asking the question "is the current user allowed to make this
  change." When you're executing a write "as the system," e.g. in a background
  job, you should typically just use `execute-tx` or `submit-tx`.

- biff.xtdb does not currently support authorization rules for reading data. I
  rely on biff.graph for this instead. e.g. I define "params" resolvers that
  extract entity IDs from path/query params and then return the parsed entity ID
  only if the current user is authorized to read that entity.

- biff.xtdb/module inserts some KV-store functions into your system map:
  `:biff.core/kv-get`, `:biff.core/kv-set`, and `:biff.core/kv-list`. These KV
  functions are used by a few not-yet-released Biff libs for persisting data
  without depending on a particular database.
