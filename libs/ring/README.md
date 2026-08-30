# biff.ring

A convenience library with helpers for Ring, Reitit, and Jetty.

Features:

- Define Ring handlers with a collection of default middleware.
- Ergonomically construct URLs for Reitit routes without hardcoding strings
  everywhere.
- Integrate with biff.core.

### Dependency

```clojure
com.biffweb/ring {:mvn/version "2.0.0-rc24-SNAPSHOT"}
```

If you don't want to use Jetty, you can do `com.biffweb/ring {:mvn/version ...,
:exclusions [ring/ring-jetty-adapter]}`.

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

### Start a webserver

Register the module to start Jetty using `:biff.ring/handler` from the system
map:

```clojure
(def modules
  [(biff.ring/module)
   ...])

(def start-order
  [...
   :biff.ring/module
   ...])
```

### Construct URLs/paths

[`path`](docs/api/com.biffweb.ring.md#path) and
[`defpath`](docs/api/com.biffweb.ring.md#defpath) can be used to ergonomically
construct `:href` / `:action` values without duplicating your route paths
throughout your codebase. e.g. instead of this:

```clojure
(def my-handler [request]
  ...
  [:a {:href (str "/foo/" 123)}]
  ...)

(def routes
  [["/foo/:id" {:get ...}]])
```

You can do this:

```clojure
(require '[com.biffweb.ring :refer [defpath]])

(defpath my-path "/foo/:id")

(def my-handler [request]
  ...
  [:a {:href (my-path 123)}]
  ...)

(def routes
  [[(my-path) {:get ...}]])
```

Paths that need to be used in multiple namespaces can be defined in a single
shared namespace:

```clojure
(ns example.routes
  (:require [com.biffweb.ring :refer [defpath]]))

(defpath home-page    "/")
(defpath another-page "/another-page")
(defpath post-page    "/posts/:id")
...

(require '[example.routes :as routes])
(routes/post-page 123)
=> "/posts/123"
```

Calling a path function with no arguments returns its path template. This is
useful for defining the Reitit route.
