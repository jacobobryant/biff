# com.biffweb.ring API

### *testing*

[view source](../../src/com/biffweb/ring.clj#L31)

```
See `path`. Default false.
```

### make-handler

[view source](../../src/com/biffweb/ring.clj#L39)

```
(make-handler {:keys [site-routes site-middleware api-routes api-middleware base-middleware]})

Returns a Reitit Ring handler with default middleware applied.

Applies wrap-base-defaults, wrap-api-defaults, and wrap-site-defaults to the
provider Reitit routes. If additional middleware is provided, it is placed on
the inside of its corresponding default middleware. Each middleware parameter
is a vector of functions.

Includes a default handler for 404, 405 and 406 errors. If incoming requests
have :biff.ring/on-error set, that will be used instead with :status set on
the request.
```

### path

[view source](../../src/com/biffweb/ring.clj#L58)

```
(path path-template-or-route & args)

Returns a path with given path and query parameters applied.

The first argument can be either a Reitit path template like "/posts/:id"
or a Reitit route, in which case the path template will be extracted from
it.

If the path template includes path parameters, corresponding arguments must
be provided for them. Any UUID path parameters will be base64, URL encoded,
and they can be decoded with wrap-path-param-uuids. Non-UUID path parameters
are inserted as-is.

You may optionally include a map of additional parameters at the end which
will be encoded via taoensso.nippy/fast-freeze into a single `npy` query
parameter. They can be decoded with wrap-nippy-params.

  (path "posts/:id" 1 {:foo "bar"{)
  => "/posts/1?foo=bar")

If *testing* is bound to true, returns a vector containing the path string
and the unencoded query params.
```

### defpath

[view source](../../src/com/biffweb/ring.clj#L82)

```
(defpath sym path-template-or-route)

Convenience macro for (def sym (partial path path-template-or-route))

It's recommended to use this in a dedicated shared namespace to define paths
that must be referenced from multiple namespaces.
```

### defroute

[view source](../../src/com/biffweb/ring.clj#L90)

```
(defroute sym & args)

Defines a Reitit route backed by a biff.fx machine.

  (defroute my-route "/posts/:id"
    [:example.fx/query ...]  ; effect descriptor

    :get
    (fn [ctx query-result]
      {:biff.fx/next :next
       ...})

    :next
    (fn [ctx]
      [:div ...]))
  => [#'com.example/my-route-impl
      #'com.example/my-route]

  my-route
  => ["/posts/:id" {:name ..., :get ...}]

A Reitit path template string may be provided after the var name. If it
is omitted, a path of the form "/_biff/api/<namespace>/<symbol>" will be
used by default.

After that, you may provide an optional biff.fx effect descriptor (a vector).
Its result will be passed as the second argument to any state functions that
have HTTP method keywords (:get, :post, etc). If other state functions need
to use the result, it is also available via (:biff.ring/fx-result ctx).

The machine has a default :start state function which simply transitions to
the state named by the request method (:get, :post, etc).

If a state function returns a vector whose first element is a keyword, it
will be rendered as hiccup (via lambdaisland.hiccup) and returned as a 200
response. You may also return a response map with :body set to a hiccup
vector.

The var is defined as a Reitit route, i.e. a vector containing the path
template string and an options map. The fully-qualified var name is used as
the route :name (converted to a keyword). The handler function is set to
whatever HTTP methods the machine has states for. The machine may contain
multiple HTTP method states.

The handler function is also defined as a var with an -impl suffix, which
helps with testing and REPL-driven development (late binding).
```

### wrap-anti-forgery-websockets

[view source](../../src/com/biffweb/ring.clj#L140)

```
(wrap-anti-forgery-websockets handler)

Provides CSRF protection for websocket requests.

:biff.ring/base-url must be set, and incoming websocket requests must have
that value in the Origin header. If :biff.ring/base-url isn't set, all
websocket requests will be rejected.

Returns a default 403 response which can be overridden by setting a
:biff.ring/on-error handler on the incoming Ring request.
```

### wrap-path-param-uuids

[view source](../../src/com/biffweb/ring.clj#L152)

```
(wrap-path-param-uuids handler)

Updates :path-params on incoming requests, decoding any UUIDs encoded by
`path`.
```

### wrap-nippy-params

[view source](../../src/com/biffweb/ring.clj#L158)

```
(wrap-nippy-params handler)

Decodes Nippy-encoded params from `path` and merges them into :params.
```

### wrap-resource

[view source](../../src/com/biffweb/ring.clj#L163)

```
(wrap-resource handler)

Serves files from the classpath.

The following keys will be checked on incoming Ring requests:

  :biff.ring/root        - default "public"
  :biff.ring/index-files - default ["index.html"]

Before calling `handler`, checks the `root` folder on the classpath for a
file matching the incoming :uri value. If not found, tries again with each of
the index files used as a basename. If still not found, passes the request on
to the wrapped handler.
```

### wrap-internal-error

[view source](../../src/com/biffweb/ring.clj#L178)

```
(wrap-internal-error handler)

Logs exceptions and returns a default 500 response.

Set :biff.ring/on-error on incoming requests to override the default
response.
```

### wrap-log-requests

[view source](../../src/com/biffweb/ring.clj#L186)

```
(wrap-log-requests handler)

Logs an info message after each request finishes.
```

### wrap-session

[view source](../../src/com/biffweb/ring.clj#L191)

```
(wrap-session handler)

A wrapper for ring.middleware.session/wrap-session that accepts options at
request time and sets some opinionated defaults.

Incoming requests may have the following keys:

:biff.ring/secure
  Default true. Sets the `Secure` cookie attribute.

:biff.ring/session-store
  Optional. An implementation of ring.middleware.session.store/SessionStore.
  Takes precedence over cookie-secret and fallback-session-store.

:biff.ring/cookie-secret
  Optional. A base64-encoded :key value for
  ring.middleware.session.cookie/cookie-store. If set, uses encrypted cookies
  for session storage. Takes precedence over fallback-session-store.

:biff.ring/fallback-session-store
  Optional. A default session store to use if session-store and cookie-secret
  aren't set. This key is set by `module`.

:biff.ring/session-max-age
  Default 60 days. The number of seconds after which the session cookie
  expires.

:biff.ring/session-same-site
  Default :lax. Sets `SameSite` on the session cookie.

Sets HttpOnly on the session cookie.
```

### wrap-ssl

[view source](../../src/com/biffweb/ring.clj#L224)

```
(wrap-ssl handler)

Wraps ring.middleware.ssl/{wrap-hsts,wrap-ssl-redirect}

Incoming requests may have the following keys:

:biff.ring/secure
  If false, other options are ignored and this middleware is a no-op.

:biff.ring/hsts
  Default true. Enables `wrap-hsts`.

:biff.ring/ssl-redirect
  Default false. Enables `wrap-ssl-redirect`.
```

### wrap-site-defaults

[view source](../../src/com/biffweb/ring.clj#L240)

```
(wrap-site-defaults handler)

A composition of site-relevant middleware.

Includes:

- anti forgery (including for websockets)
- sessions
- param decoding for use with `path`
- muuntaja's wrap-params and wrap-format
- ring.middleware.default's site defaults
```

### wrap-api-defaults

[view source](../../src/com/biffweb/ring.clj#L253)

```
(wrap-api-defaults handler)

A composition of API-relevant middleware.

Includes:

- Muuntaja's wrap-params and wrap-format
- ring.middleware.default's API defaults
```

### wrap-base-defaults

[view source](../../src/com/biffweb/ring.clj#L263)

```
(wrap-base-defaults handler)

A collection of site- and API-relevant middleware.

Includes:

- Static resource serving
- Internal server error handling
- HSTS
- SSL redirect
- Request logging
```

### use-jetty

[view source](../../src/com/biffweb/ring.clj#L278)

```
(use-jetty {:biff.ring/keys [host port handler], :or {host "localhost", port 8080}})

A biff.core component that starts a Jetty webserver.

Merges ctx into incoming requests.
```

### module

[view source](../../src/com/biffweb/ring.clj#L288)

```
(module)

A biff.core module that sets :biff.ring/handler and
:biff.ring/fallback-session-store on the system map.

:biff.ring/handler is compiled by collecting the following keys from other
modules and passing them to `make-handler`:

  :biff.ring/routes (site routes)
  :biff.ring/api-routes
  :biff.ring/site-middleware
  :biff.ring/api-middleware
  :biff.ring/base-middleware

:biff.ring/fallback-session-store is set to an in-memory store.
```
