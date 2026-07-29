# biff.ring

A convenience library with helpers for Ring, Reitit, and Jetty.

Features:

- Define Ring handlers with a collection of default middleware.
- Ergonomically construct URLs for Reitit routes without hardcoding strings
  everywhere.
- Integrate with biff.core and biff.fx.

### Dependency

```clojure
com.biffweb/ring {:mvn/version "2.0.0-rc10"}
```

### Status

This library will be a release candidate until all [the other Biff 2
libraries](/README.md) have been released. Until then there could be breaking
changes.

## Reference

- [Schema](docs/reference/schema.md)
- [API](docs/api/com.biffweb.ring.md)

## Usage

### Define routes and handlers

[`make-handler`](docs/api/com.biffweb.ring.md#make-handler) takes some Reitit
routes and Ring middleware, combines them with some default middleware, and
returns a Ring handler. If you're using biff.core you can set the routes and
middleware on your Biff modules (see
[`module`](docs/api/com.biffweb.ring.md#module) for the list of supported keys),
and then `:biff.ring/handler` will be set on the system map:

```clojure
(def module
  {:biff.ring/routes [["/foo" {...}]]})

(def modules
  [(biff.ring/module)
   ...])
```

If you're not using biff.core, you can instead call `make-handler` directly:
`(make-handler {:site-routes [...], ...})`.

You can pass additional middleware to `make-handler`. If you want to omit some
of the default middleware, you'll need to construct the handler yourself without
using `make-handler`. See the [API reference](docs/api/com.biffweb.ring.md) for
a list of the default middleware, all of which can be used individually.

[`defroute`](docs/api/com.biffweb.ring.md#defroute) can be used to define
Reitit routes that are backed by [biff.fx machines](/libs/fx/):

```clojure
(defroute my-route \"/posts/:id\"
  [:example.fx/query ...]

  :get
  (fn [ctx query-result]
    ...)

  :next
  (fn [ctx]
    [:div \"hello\"]))

(def module
  {:biff.ring/routes [my-route
                      ...]})
```

### Start a webserver

[`use-jetty`](docs/api/com.biffweb.ring.md#use-jetty) is a biff.core component
that starts a Jetty webserver using `:biff.ring/handler` from the system map.

```clojure
(def components
  [...
   biff.ring/use-jetty
   ...])
```

### Construct URLs/paths

[`path`](docs/api/com.biffweb.ring.md#path) and
[`defpath`](docs/api/com.biffweb.ring.md#defpath) can be used to ergonomically
construct `:href` / `:action` values without duplicating your route paths
throughout your codebase. e.g. instead of this:

```clojure
(def my-route
  ["/foo/:id" {:get ...}])

(def my-handler [request]
  ...
  [:a {:href (str "/foo/" 123)}]
  ...)
```

You can do this:

```clojure
(require '[com.biffweb.ring :refer [path]])

(def my-route
  ["/foo/:id" {:get ...}])

(def my-handler [request]
  ...
  [:a {:href (path my-route 123)}]
  ...)
```

`path` is intended for referencing routes defined in the current namespace.
Instead of using `path` with routes from other namespaces, it's recommended to
create a single `routes.clj` file and define route paths in it with `defpath`:

```clojure
(defpath home-page    "/")
(defpath another-page "/another-page")
(defpath post-page    "/posts/:id")
...

(require '[example.routes :as routes])
(routes/post-page 123)
=> "/posts/123"
```

## Tips

- The initial effect descriptor passed to `defroute` is intended primarily to be
  used with biff.graph: `(defroute my-route "/foo" [:biff.graph.fx/query
  [{:session/user [:user/email ...]}]] ...)`. e.g. many GET routes can have just
  that and then a single `:get` state function.

- Since routes in a web app are often mutually dependent (e.g. a parent page
  links to a child page and the child page links to the parent), it's often
  useful to `declare` routes so that they can be used with `path`.
