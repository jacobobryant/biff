# biff.sqlite

A convenience wrapper for using SQLite in Biff/Clojure applications.

This library streamlines the process of using SQLite in a Biff application
and provides some helpful SQLite-related functionality in general.
Features:

- Sane defaults like WAL mode, STRICT tables, etc.
- Backup/restore via [Litestream](https://litestream.io).
- Migrations via [sqldef](https://github.com/sqldef/sqldef).
- Rich schema types: define columns as e.g. booleans, instants, nested maps,
  etc; and biff.sqlite converts them to/from SQLite's supported types
  (ints, blobs, etc).
- Validate transactions based on centralized authorization rules you define
  (helps to keep LLM code secure).
- Generate [biff.graph](/libs/graph) resolvers for your tables.
- Implements [biff.core](/libs/core)'s generic KV-store interface, which allows
  other Biff libs to persist data without needing to know which database you're
  using.
- Some other glue code for apps that use biff.core and/or [biff.fx](/libs/fx).

You can also use this library standalone if you want to use certain parts like
rich schema or Litestream/sqldef integration but aren't using any other Biff
libs.

This library wraps [next.jdbc](https://github.com/seancorfield/next-jdbc) and
[HoneySQL](https://github.com/seancorfield/honeysql).

### Dependency

```clojure
com.biffweb/sqlite {:mvn/version "2.0.0-rc21"}
```

### Status

This library will be a release candidate until all [the other Biff 2
libraries](/README.md) have been released. Until then there could be breaking
changes, but I don't anticipate any.

## Reference

- [Database Schema](docs/reference/db-schema.md)
- [Library Schema](docs/reference/library-schema.md)
- [API](docs/api/com.biffweb.sqlite.md)

## Usage

### Schema

First you need to define your application's schema as a "columns map" (see
[Database Schema](docs/reference/db-schema.md):

```clojure
(def columns
  {:user/id        {:type :uuid :primary-key true}
   :user/email     {:type :string :unique true}
   :user/joined-at {:type :inst}
   :user/pet-id    {:type :uuid :ref :pet/id}

   :pet/id      {:type :uuid :primary-key true}
   :pet/species {:type        :enum
                 :enum-values {0 :pet.species/iguana
                               1 :pet.species/tardigrade
                               2 :pet.species/rock}}})
```

### System start up

Then you can initialize your schema and start some database connections with
`use-sqlite`. If you're using `biff.core`, you can add `use-sqlite` to your
components, add `(module)` to your components, and add another module in your
application with `:biff.sqlite/colums columns`:

```clojure
(require '[com.biffweb.sqlite :as biff.sqlite])

(def columns ...)

(def schema-module {:biff.sqlite/columns columns})

(def modules
  [schema-module
   (biff.sqlite/module)
   ...])

(def components
  [...
   biff.sqlite/use-sqlite
   ...])
```

If you're not using biff.core, you can wire things up manually:

```clojure
(def columns ...)

(comment
  (def config {:biff.sqlite/columns columns})
  (def ctx (biff.sqlite/use-sqlite config))

  ;; close database connections
  (let [[stop-fn] (:biff.core/stop ctx)]
    (stop-fn)))
```

Litestream will only be used if you pass in some config for remote object
storage; see [`use-litestream`](docs/api/com.biffweb.sqlite.md#use-litestream).

### Migrations

sqldef does schema migrations idempotently/declaratively, so you don't need to
define explicit migrations. On system startup, sqldef will compare your declared
schema (generated from `columns`) to what's currently in your database and run
migrations as needed. It will only run non-destructive migrations. If you make a
destructive change to `columns` (e.g. removing a column), any other migrations
will still be applied, but the destructive migrations will be skipped.

You can apply destructive migrations by running `sqlite3def` directly after your
`resources/schema.sql` file has been updated by `use-sqlite`:

```
target/bin/sqlite3def storage/sqlite/main.db \
  --apply --enable-drop -f resources/schema.sql
```

### Queries

Then you can run execute statements with `execute`:

```clojure
(biff.sqlite/execute ctx
  ["SELECT * FROM pet WHERE species = ?"
   :pet.species/iguana])

;; biff.sqlite wraps HoneySQL, so you can pass statements as maps:
(biff.sqlite/execute ctx
  {:select :*
   :from   :pet
   :where  [:= :pet/species [:lift :pet.species/iguana]]})
```

When reading query results, values are converted only if their associated column
is contained in your `columns` map. biff.sqlite adds support for using qualified
keywords as column aliases in HoneySQL which is necessary to get type coercion
for e.g. aggregated columns:

```clojure
(biff.sqlite/execute ctx
  {:select [[[:max :user/joined-at] :user/joined-at]]
   :from   :user})
```

### Authorization rules

The `authorized-write` function can also be used to execute write statements,
and it will pass a "diff" value to an `authorize` function which you define:

```clojure
(defn can-update-user? [ctx before after]
  (let [user-id       (get-in ctx [:session :uid])
        editable-keys [:user/pet-id]]
    (and ;; Users can only update their own user record
         (= user-id (:user/id before))
         ;; And they can only change :user/pet-id
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
  {:biff.core/init (fn [_] {:biff.sqlite/authorize #'authorize})})
;; Without:
(def ctx {:biff.sqlite/authorize #'authorize, ...})

(let [user-id (get-in ctx [:session :uid])]
  (biff.sqlite/authorized-write ctx
    {:update :user
     :set    {:user/pet-id ...}
     :where  [:= :user/id user-id]}))
```

See [`authorized-write`](docs/api/com.biffweb.sqlite.md#authorized-write) and
[`:biff.sqlite/authorize`](docs/reference/library-schema.md#biffsqliteauthorize).

### Transactions

In addition to `execute` and `authorized-write`, there are companion functions
`execute-tx` and `authorized-write-tx` which take a sequence of statements and
run them all in a transaction.

These functions are suitable for running a "batch-style" transaction. If you
want to run an interactive transaction, you can update the
`:biff.sqlite/read-pool` and/or `:biff.sqlite/write-conn` keys to be
transactions:

```clojure
(jdbc/with-transaction [tx (:biff.sqlite/write-conn ctx)]
  (let [ctx (assoc ctx :biff.sqlite/write-conn tx)]
    ...))
```

### biff.graph resolvers

Use `biff.sqlite/make-resolvers` to generate a biff.graph resolver for each of
your SQLite tables:

```clojure
;; with biff.core:
(def module
  {:biff.graph/resolvers (biff.sqlite/make-resolvers
                          {:biff.sqlite/columns columns})})

;; without:
(def resolvers (concat
                (biff.sqlite/make-resolvers
                 {:biff.sqlite/columns columns})
                [...]))
(def ctx (merge (biff.graph/new-ctx resolvers) ...))


;; Then you can query sqlite via biff.graph:
(biff.graph/query
  ctx
  {:user/id ...}
  [:user/email
   :user/pet-id
   {:user/pet [:pet/id
               :pet/species]}])
```

See [`make-resolvers`](docs/api/com.biffweb.sqlite.md#make-resolvers).

### biff.fx integration

`module` provides a `:biff.fx/handlers` map that includes:

- `:biff.sqlite.fx/execute`
- `:biff.sqlite.fx/execute-tx`
- `:biff.sqlite.fx/authorized-write`
- `:biff.sqlite.fx/authorized-write-tx`

## Tips

- `authorized-write` should be used in the context of a particular user, i.e.
  when you're asking the question "is the current user allowed to make this
  change." This is particular helpful for making sure that LLM code isn't
  missing an authorization check somewhere as you generate gobs of features for
  your web app. When you're executing a write "as the system," e.g. in a
  background job, you should typically just use `execute`.

- biff.sqlite does not currently support authorization rules for reading data. I
  rely on biff.graph for this instead. e.g. I define "params" resolvers that
  extract entity IDs from path/query params and then return the parsed entity ID
  (as a join, like `{:params/thing {:thing/id #uuid "..."}}`) only if the
  current user is authorized to read that entity.

- `biff.sqlite/module` inserts some KV-store functions into your system map:
  `:biff.core/kv-get`, `:biff.core/kv-set`, and `:biff.core/kv-list`. The module
  also includes the associated table schema which you will see in the generated
  `resources/schema.sql` file after running `use-sqlite`. These KV functions are
  used by a few not-yet-released Biff libs (e.g. the authentication module) for
  persisting data without depending on a particular database.
