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
com.biffweb/core {:mvn/version "2.0.0-rc24-SNAPSHOT"}
```

### Status

This library will be a release candidate until all [the other Biff 2
libraries](/README.md) have been released. Until then there could be breaking
changes, but I don't anticipate any.

## Example

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

- [Schema](docs/reference/schema.md)
- [API](docs/api/com.biffweb.core.md)

## Concepts

biff.core's system composition code is designed to be:

- easy to understand (the implementation is [~100 lines of
  code](src/com/biffweb/core/impl/system.clj) and is built with plain functions
  and maps)

- convenient for pulling functionality out into external libraries (so that
  other Biff libraries can provide things like web server initialization,
  authentication flows, etc)

- repl-friendly: restarting stateful resources shouldn't need to be a regular
  part of your workflow.

### System map

Biff models your application state and configuration via a single "system map"
which typically has flat, namespaced keys. On startup, the system map starts
empty and then is built up by your modules.

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

Modules can include a `:biff.core/init` lifecycle function. Init functions run
at system startup in an unspecified order, before any start functions run. Each
init function takes the entire `modules` vector and returns a map. The maps from
all the init functions are merged together into an initial system map.

So modules can both define a chunk of application functionality, and they can
also aggregate those chunks from other modules into the system map.

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

### Start/stop functions

Modules can define a `:biff.core/start` function (and a `:biff.core/stop`
function if needed). Start functions take a system map, start stateful resources
or do other initialization, then return an updated system map. Stop functions
receive the map returned by their associated start function, and they shut down
any stateful resources as needed.

```clojure
(def module
  {:biff.core/id :com.example/webserver

   :biff.core/start
   (fn [{:com.example/keys [handler port]
         :or {port 8080}
         :as ctx}]
     (assoc ctx ::server
            (jetty/run-jetty handler
                             {:host "localhost" :port port :join? false})))
   :biff.core/stop
   (fn [{::keys [server]}]
     (.stop server))})
```

You specify the module start order by providing a vector of module IDs
(the `:biff.core/id` value).

```clojure
(def start-order
  [:com.example/config
   :com.example/database
   :com.example/webserver])
```

## Usage

### System composition

The `com.biffweb.core/start` function takes your modules and their start order
and starts your application, returning the final system map. To stop the
application, pass the system map to `com.biffweb.core/stop`.

```clojure
(defonce system (atom {}))

(reset! system (biff.core/start #'modules start-order))

(biff.core/stop @system)
```

You'll typically wrap these calls in your own `start`, `stop`, and `refresh`
functions for repl-driven development.

If you want to override any values set by an init function or add additional
keys to the system map, you can pass an additional `initial-system` argument:

```clojure
(def initial-system {...}

(biff.core/start initial-system #'modules start-order)
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

## Tips

- Application namespaces that contain modules shouldn't depend on each other;
  they should instead expose all their functionality via a single `module` map.
  Shared functions should be kept elsewhere, e.g. inside a `lib/` folder.

- Application config should be read in by the first module in the start order
  and inserted into the system map. Other parts of the codebase should always
  get their config from the system map instead of e.g. reading env vars
  directly. Config schema should be registered. Config secrets should be
  wrapped with `biff.core/secret-delay` so that they aren't accidentally
  serialized. Prefer flat, namespaced keys over nested config.

- Although the system map is typically stored in a global atom, that's only
  meant to be used via the repl. Application code should always receive the
  system map as a parameter instead of accessing the global atom. Try to keep
  the system map at the edges of your code rather than passing it deeply down
  the call stack (functional core, imperative shell). biff.fx and biff.graph
  help with that.
