(ns com.biffweb.ring
  (:require [com.biffweb.core :as biff.core]
            [com.biffweb.ring.impl.middleware :as impl.middleware]
            [com.biffweb.ring.impl.path :as impl.path]
            [com.biffweb.ring.impl.server :as impl.server]
            [ring.middleware.session.store :as session.store]))

(biff.core/register
 {:biff.ring/api-middleware  [:sequential 'ifn?]
  :biff.ring/api-routes      [:sequential 'ifn?]
  :biff.ring/base-middleware [:sequential 'ifn?]
  :biff.ring/cookie-secret   :biff.core/secret

  :biff.ring/fallback-session-store
  [:fn #(satisfies? session.store/SessionStore %)]

  :biff.ring/handler           'ifn?
  :biff.ring/host              'string?
  :biff.ring/hsts              'boolean?
  :biff.ring/index-files       [:sequential 'string?]
  :biff.ring/on-error          'ifn?
  :biff.ring/port              'integer?
  :biff.ring/root              'string?
  :biff.ring/routes            'sequential?
  :biff.ring/secure            'boolean?
  :biff.ring/session-max-age   'integer?
  :biff.ring/session-same-site 'keyword?

  :biff.ring/session-store
  [:fn #(satisfies? session.store/SessionStore %)]

  :biff.ring/site-middleware [:sequential 'ifn?]
  :biff.ring/ssl-redirect    'boolean?})

(def
  ^{:dynamic true
    :doc     "See `path`. Default false."}
  *testing*
  false)

;;;; routes and paths

(defn make-handler
  "Returns a Reitit Ring handler with default middleware applied.

   Applies wrap-base-defaults, wrap-api-defaults, and wrap-site-defaults to the
   provider Reitit routes. If additional middleware is provided, it is placed on
   the inside of its corresponding default middleware. Each middleware parameter
   is a vector of functions.

   Includes a default handler for 404, 405 and 406 errors. If incoming requests
   have :biff.ring/on-error set, that will be used instead with :status set on
   the request."
  {:arglists '([{:keys [site-routes
                        site-middleware
                        api-routes
                        api-middleware
                        base-middleware]}])}
  [opts]
  (impl.server/make-handler opts))

(defn path
  "Returns a path with given path and query parameters applied.

   The first argument is a Reitit path template like \"/posts/:id\". With no
   additional arguments, returns the template unchanged.

   If the path template includes path parameters, corresponding arguments are
   used as the parameter values. Any UUID path parameters will be base64, URL
   encoded, and they can be decoded with wrap-path-param-uuids. Non-UUID path
   parameters are inserted as-is.

   You may optionally include a map of additional parameters at the end which
   will be encoded via taoensso.nippy/fast-freeze into a single `npy` query
   parameter. They can be decoded with wrap-nippy-params.

     (path \"posts/:id\" 1 {:foo \"bar\"{)
     => \"/posts/1?foo=bar\")

   If *testing* is bound to true, returns a vector containing the path string
   and the unencoded query params."
  [path-template & args]
  (apply impl.path/path path-template args))

(defmacro defpath
  "Convenience macro for (def sym (partial path path-template))

   It's recommended to use this in a dedicated shared namespace to define paths
   that must be referenced from multiple namespaces."
  [sym path-template]
  `(impl.path/defpath ~sym ~path-template))

;;;; middleware

(defn wrap-csrf-protection
  "Prevents CSRF attacks via Sec-Fetch-Site and other headers.

   Follows the algorithm described in https://words.filippo.io/csrf/, but is
   stricter in a few ways:

   - If the Sec-Fetch-Site and Origin headers are both missing, rejects the
     request instead of accepting it.

   - Applies CSRF protection to websocket upgrade requests.

   Set :biff.ring/on-error on incoming requests to override the default 403
   response."
  [handler]
  (impl.middleware/wrap-csrf-protection handler))

(defn wrap-path-param-uuids
  "Updates :path-params on incoming requests, decoding any UUIDs encoded by
   `path`."
  [handler]
  (impl.middleware/wrap-path-param-uuids handler))

(defn wrap-nippy-params
  "Decodes Nippy-encoded params from `path` and merges them into :params."
  [handler]
  (impl.middleware/wrap-nippy-params handler))

(defn wrap-resource
  "Serves files from the classpath.

   The following keys will be checked on incoming Ring requests:

     :biff.ring/root        - default \"public\"
     :biff.ring/index-files - default [\"index.html\"]

   Before calling `handler`, checks the `root` folder on the classpath for a
   file matching the incoming :uri value. If not found, tries again with each of
   the index files used as a basename. If still not found, passes the request on
   to the wrapped handler."
  [handler]
  (impl.middleware/wrap-resource handler))

(defn wrap-internal-error
  "Logs exceptions and returns a default 500 response.

   Set :biff.ring/on-error on incoming requests to override the default
   response."
  [handler]
  (impl.middleware/wrap-internal-error handler))

(defn wrap-log-requests
  "Logs an info message after each request finishes."
  [handler]
  (impl.middleware/wrap-log-requests handler))

(defn wrap-session
  "A wrapper for ring.middleware.session/wrap-session that accepts options at
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

   Sets HttpOnly on the session cookie."
  [handler]
  (impl.middleware/wrap-session handler))

(defn wrap-ssl
  "Wraps ring.middleware.ssl/{wrap-hsts,wrap-ssl-redirect}

   Incoming requests may have the following keys:

   :biff.ring/secure
     If false, other options are ignored and this middleware is a no-op.

   :biff.ring/hsts
     Default true. Enables `wrap-hsts`.

   :biff.ring/ssl-redirect
     Default false. Enables `wrap-ssl-redirect`."
  [handler]
  (impl.middleware/wrap-ssl handler))

(defn wrap-site-defaults
  "A composition of site-relevant middleware.

   Includes:

   - anti forgery (including for websockets)
   - sessions
   - param decoding for use with `path`
   - muuntaja's wrap-params and wrap-format
   - ring.middleware.default's site defaults"
  [handler]
  (impl.middleware/wrap-site-defaults handler))

(defn wrap-api-defaults
  "A composition of API-relevant middleware.

   Includes:

   - Muuntaja's wrap-params and wrap-format
   - ring.middleware.default's API defaults"
  [handler]
  (impl.middleware/wrap-api-defaults handler))

(defn wrap-base-defaults
  "A collection of site- and API-relevant middleware.

   Includes:

   - Static resource serving
   - Internal server error handling
   - HSTS
   - SSL redirect
   - Request logging"
  [handler]
  (impl.middleware/wrap-base-defaults handler))

;;;; biff.core integration

(defn module
  "A biff.core module that sets :biff.ring/handler and
   :biff.ring/fallback-session-store on init and starts Jetty on start.
   Module ID is :biff.ring/module.

   :biff.ring/handler is compiled by collecting the following keys from other
   modules and passing them to `make-handler`:

     :biff.ring/routes (site routes)
     :biff.ring/api-routes
     :biff.ring/site-middleware
     :biff.ring/api-middleware
     :biff.ring/base-middleware

   :biff.ring/fallback-session-store is set to an in-memory store.

   On start, the following keys are used:

     :biff.ring/handler
     :biff.ring/host     (default \"localhost\")
     :biff.ring/port     (default 8080)

   Merges the system map into incoming requests."
  []
  (impl.server/module))
