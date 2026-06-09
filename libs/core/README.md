# biff.core

biff.core defines the interfaces and code that connect all the other Biff libs.

Mostly biff.core is a lightweight "system composition" tool that overlaps with
Component/Integrant/Mount. It defines some patterns for separating your codebase
into small independent chunks, and it has code to turn those chunks into a
running system.

biff.core also contains:

- Malli-based validation helper functions.

- A few custom data types/function specifications: anything that multiple Biff
  libs need to know about without either of those libs "owning" the type (e.g.
  so that you can swap out the libraries).

### Dependency

```clojure
com.biffweb/core {:mvn/version "2.0.0-rc5"}
```

### Status

This library will be a release candidate until all [the other Biff 2
libraries](/README.md) have been released. Until then there could be breaking
changes, but I don't anticipate any.

### Table of contents

- [Get started](#get-started)
- [API Reference](#api-reference)
- [Concepts](#concepts)
- [Usage](#usage)
- [Tips](#tips)

## Get started

Try out [the demo app](demo/com/example.clj):

```
$ git clone https://github.com/jacobobryant/biff
$ cd biff
$ git checkout v2.x
$ cd libs/core/
$ clj -A:demo
user=> (require 'com.example)
nil
user=> (in-ns 'com.example)
#object[clojure.lang.Namespace 0x318a81a0 "com.example"]
com.example=> (start)
[main] INFO com.biffweb.core.impl - starting: com.example.lib.config$use_config
[main] INFO com.biffweb.core.impl - starting: com.example.lib.ring$use_webserver
[main] INFO com.example.lib.ring - Web server started on http://localhost:8080
```

## API Reference

[com.biffweb.core](/docs/api/com.biffweb.core.md)

The namespace docstring contains specifications for all `:biff.core/*` keys.
Some of those keys aren't actually used in this library and are only provided as
interfaces for other Biff libraries. e.g. the `:biff.core/kv-*` key-value store
functions are meant to be implemented for whatever database you're using so that
other Biff libs can do simple data persistence without requiring a particular
database.

## Concepts

biff.core's system composition code is designed to be:

- easy to understand (the implementation is [~60 lines of
  code](src/com/biffweb/core/impl/system.clj) and is built with plain functions
  and maps)

- convenient for pulling functionality out into external libraries (so that
  other Biff libraries can provide things like web server initialization,
  authentication flows, etc)

- repl-friendly: restarting stateful components shouldn't need to be a regular
  part of your workflow.

### Components

Your application code is organized into "components" and "modules." A component
is a function that takes the "system map" (a flat, namespaced map that defines
your application) and modifies it. Components can start stateful resources (such
as web servers and database connections) and do other initialization (like
reading config and inserting it into the system map).

```clojure
(defn use-webserver [{:com.example/keys [handler port]
                      :or {port 8080}
                      :as ctx}]
  (let [server (jetty/run-jetty handler
                                {:host  "localhost"
                                 :port  port
                                 :join? false})]
    (update ctx :biff.core/stop conj #(.stop server))))
```

By convention, component names start with `use-`. The system map is typically
referred to as `ctx` (context) because it's often merged with other things (like
Ring request maps), so `system` would be too specific in some situations.

There is very intentionally no mechanism for automatically discovering
components or wiring them up in dependency order: you wire them up manually like
Ring middleware.

```clojure
(def components
  [use-config
   use-database
   use-webserver])
```

### Modules

Each module is a map that contains a chunk of application functionality. For
example, each page in your web application can have a module containing the HTTP
routes for that page.

```clojure
(def module
  {:com.example/routes [""
                        ["/" {:get landing-page}]
                        ...]})
```

Typically you have one module per namespace, then a `modules.clj` file
aggregates all the modules into a vector.

```clojure
(def modules
  [landing-page/module
   settings/module
   ...])
```

### Init functions

Modules can also include a special `:biff.core/init` function which is what
connects modules to components. Each init function takes the entire `modules`
vector and returns a map that will be merged into the system map; after an
initial system map is constructed via the init functions, it gets passed through
the component functions.

```clojure
(def module
  {:biff.core/init
   (fn [modules-var]
     (let [all-routes (keep :com.example/routes @modules-var)
           handler    (make-handler all-routes)]
       {:com.example/handler handler}))})
```

The init functions actually receive the `modules` _var_ (`#'modules`) rather
than receiving the `modules` value directly. This allows init functions to do
late-binding. For example, the `:com.example/handler` function could be a
wrapper that rebuilds an underlying handler function whenever it detects that
the modules have changed. See [the demo app](demo/com/example/lib/ring.clj) for
an example.

## Usage

### System composition

The `com.biffweb.core/start` function takes your modules and components and
starts your application, returning the final system map. To stop the
application, pass the system map to `com.biffweb.core/stop`.

```clojure
(defonce system (atom {}))

(reset! system (biff.core/start #'modules components))

(biff.core/stop @system)
```

You'll typically wrap these calls in your own `start`, `stop`, and `refresh`
functions for repl-driven development.

If you want to override any values set by an init function or add additional
keys to the system map, you can pass an additional `initial-system` argument:

```clojure
(def initial-system {...}

(biff.core/start initial-system #'modules components)
```

### Validation

biff.core maintains a global Malli schema registry, to which you can add schema
via `com.biffweb.core/register`. The `com.biffweb.core/validate` macro takes a
map and checks any keys with registered schemas to ensure their values match the
schemas.

```clojure
=> (biff.core/register {:person/age :int})
=> (biff.core/validate {:person/age "three"})
Execution error (AssertionError) at com.biffweb.core.impl/assertion-error (impl.clj:20).
`:person/age "three"` is invalid: should be an integer
```

`start` calls `validate` on modules and on the system map. You can also call
`validate` wherever else it makes sense to do schema validation. `validate`
calls compile to no-ops when `*assert*` is false.

Biff libs call register for all the keys they own (e.g. biff.core registers
a handful of `:biff.core/*` keys), and it's recommended to register any keys
that your application defines (e.g. config keys).

### Secrets

The system map is meant to contain all your app's configuration, including
secrets. To prevent secrets from accidentally being exposed (e.g. in logs), Biff
libs expect secrets to be wrapped with the `com.biffweb.core/secret-delay`
function:

```clojure
(def my-api-key (secret-delay "my-api-key"))
(str my-api-key)
=> "#<SecretDelay: redacted>"

(force my-api-key)
=> "my-api-key"
```

### Migrating from Biff v1

Using `biff.core/start` in your Biff v1 app will enable you to use other Biff v2
libraries as they're released, many of which define modules with
`:biff.core/init` functions.

The Biff v1 starter project comes with a `reduce` call like this:

```clojure
(reduce (fn [system component]
          (log/info "starting:" (str component))
          (component system))
        initial-system
        components)
```

Change it to this:

```clojure
(biff.core/start initial-system #'modules components)
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

## Tips

- Application namespaces that contain modules shouldn't depend on each other;
  they should instead expose all their functionality via a single `module` map.
  Shared functions should be kept elsewhere, e.g. inside a `lib/` folder.

- Application config should be read in by the first component and inserted into
  the system map. Other parts of the codebase should always get their config
  from the system map instead of e.g. reading env vars directly. Config schema
  should be registered. Config secrets should be wrapped with
  `biff.core/secret-delay` so that they aren't accidentally serialized. Prefer
  flat, namespaced keys over nested config.

- Although the system map is typically stored in a global atom, that's only
  meant to be used via the repl. Application code should always receive the
  system map as a parameter instead of accessing the global atom. Try to keep
  the system map at the edges of your code rather than passing it deeply down
  the call stack (functional core, imperative shell). biff.fx and biff.graph
  help with that.
