# Schema

### :biff.ring/api-middleware

`[:vector fn?]`. Ring middleware for Reitit.

### :biff.ring/api-routes

A Reitit routes vector.

### :biff.ring/base-middleware

`[:vector fn?]`. Ring middleware for Reitit.

### :biff.ring/base-url

String. A url like `https://example.com` that your site is served from.

### :biff.ring/cookie-secret

String. A base64 string which will be decoded to a 16-byte array. May be wrapped
with `biff.core/secret-delay`.

### :biff.ring/fallback-session-store

An implementation of `ring.middleware.session.store/SessionStore`.

### :biff.ring/handler

A Ring handler function.

### :biff.ring/host

String.

### :biff.ring/hsts

Boolean.

### :biff.ring/index-files

`[:vector :string]`.

### :biff.ring/on-error

A Ring handler. The incoming request will have `:status` set to an HTTP 4xx or
5xx status.

### :biff.ring/port

Int.

### :biff.ring/root

String.

### :biff.ring/routes

A Reitit routes vector.

### :biff.ring/secure

Boolean.

### :biff.ring/session-max-age

Int.

### :biff.ring/session-same-site

One of `:lax`, `:strict`, or `:none`.

### :biff.ring/session-store

An implementation of `ring.middleware.session.store/SessionStore`.

### :biff.ring/site-middleware

`[:vector fn?]`. Ring middleware for Reitit.

### :biff.ring/ssl-redirect

Boolean.
