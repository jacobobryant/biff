# Migrating from Biff 1.0

If you've previously made an app with a Biff version prior to 2.x and you want
to migrate to the latest stuff, the general approach is:

- Migrate to biff.core
- Migrate to biff.config
- Migrate whatever else you feel like.

Because Biff 2 is just a bunch of libraries rather than a single monolithic
dependency, you can pick and choose which libraries to adopt, you can adopt them
one at a time, and you can do it by simply following the directions in each
library's README.

biff.core is needed first because it defines an updated module system used by
the other libraries, and thus adopting it will make using the other libraries
easier (you can use the libraries' modules). biff.config is needed because it
uses a new type for `#biff/secret` values which is required by the other Biff
libs.

For the other libraries:

- biff.fx and biff.graph are entirely optional and you don't need to use them
  for anything else to work.

- Migrating from XTDB 1 to SQLite or XTDB 2  may be difficult since database
  migrations are difficult. However, you can adapt XTDB 1.0 to work with the
  other Biff 2 libraries by adding implementations for these functions in your
  system map: `:biff.core/kv-set`, `:biff.core/kv-get`, `:biff.core/kv-list`,
  `:biff.core/wrap-db-snapshot`. You should also use `xtdb.api/listen` to call
  `(:biff.core/on-tx ctx)` whenever there's a new transaction. See [How to write
  a Biff database adapter](/docs/db-adapters.md). These adapters are needed by
  biff.datastar, biff.authenticate, and biff.admin. If you don't care about
  using those libraries, you can skip writing the adapters.

- If you want to use both biff.graph and XTDB 1, you'll need to [generate
  resolvers](/libs/graph/README.md#defining-resolvers).

- The remaining libraries should be relatively straightforward to adopt. You
  will at least need to rename some keys in `resources/config.edn` / your system
  map / your modules.

- To deploy your app with biff.tasks, it may be easiest to provision a new
  server (with `clj -M:run prod-setup`).

## Migrating to biff.core

See the [biff.core README](/libs/core/).

The Biff v1 starter project comes with a `reduce` call like this:

```clojure
(reduce (fn [system component]
          (log/info "starting:" (str component))
          (component system))
        initial-system
        components)
```

Wrap each component function with `component-shim`, register the returned
modules, and replace the component functions with their module IDs in the start
order vector:

```clojure
(def modules
  [(biff.core/component-shim :com.example/use-config use-config)
   (biff.core/component-shim :com.example/use-jetty use-jetty)
   ...])

(def start-order
  [:com.example/use-config
   :com.example/use-jetty
   ...])

(biff.core/start initial-system #'modules start-order)
```

Then change your `refresh` function from this:

```clojure
(defn refresh []
  (doseq [f (:biff/stop @system)]
    (log/info "stopping:" (str f))
    (f))
  (tn-repl/refresh :after `start)
  :done)
```

to this:

```clojure
(defn refresh []
  (biff.core/stop @system)
  (tn-repl/refresh :after `start)
  :done)
```
