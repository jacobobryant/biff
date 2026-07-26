(ns com.biffweb.ring
  (:require [com.biffweb.core :as biff.core]
            [com.biffweb.ring.impl.middleware :as impl.middleware]
            [com.biffweb.ring.impl.path :as impl.path]
            [com.biffweb.ring.impl.route :as impl.route]
            [com.biffweb.ring.impl.server :as impl.server]))

(biff.core/register
 {:biff.ring/api-middleware         [:sequential 'ifn?]
  :biff.ring/api-routes             [:sequential 'ifn?]
  :biff.ring/base-middleware        [:sequential 'ifn?]
  :biff.ring/base-url               'string?
  :biff.ring/cookie-secret          :biff.core/secret
  :biff.ring/fallback-session-store 'some?
  :biff.ring/handler                'ifn?
  :biff.ring/host                   'string?
  :biff.ring/hsts                   'boolean?
  :biff.ring/index-files            [:sequential 'string?]
  :biff.ring/on-error               'ifn?
  :biff.ring/port                   'integer?
  :biff.ring/root                   'string?
  :biff.ring/routes                 'sequential?
  :biff.ring/secure                 'boolean?
  :biff.ring/session-max-age        'integer?
  :biff.ring/session-same-site      'keyword?
  :biff.ring/site-middleware        [:sequential 'ifn]
  :biff.ring/ssl-redirect           'boolean?})

(def
  ^{:dynamic true
    :doc     "When true, path returns `[path query-params]` instead of encoding
              query params. Intended for tests."}
  *testing*
  false)

;;;; routes and paths

(defn make-handler [opts]
  (impl.server/make-handler opts))

(defn path
  "Returns a path string with substituted path and query parameters.

   `path-or-route` may be a path string or a Reitit route vector whose first
   element is a path string. Each `:parameter` segment consumes one argument.
   UUID arguments are encoded as compact URL-safe strings.

   An optional final map is Nippy-encoded in the `npy` query parameter. When
   *testing* is true, returns `[path query-params]` instead."
  [path-or-route & args]
  (apply impl.path/path path-or-route args))

(defmacro defpath
  "Defines a path function.

   `path-or-route` may be a path string or a Reitit route vector. The resulting
   function passes its arguments to path."
  [sym path-or-route]
  `(impl.path/defpath ~sym ~path-or-route))

(defmacro defroute
  "Defines a Reitit route backed by a biff.fx machine.

   The optional first argument is a URI string. If omitted, the URI is generated
   from the current namespace and `sym`. The optional next argument is an
   initial biff.fx effects vector. Remaining key-value pairs map request methods
   to handler functions.

   Handlers may return Ring responses, Hiccup forms, or Ring responses with a
   Hiccup body. When an initial effects vector is provided, handlers receive the
   effects result as a second argument."
  [sym & args]
  `(impl.route/defroute ~sym ~@args))

;;;; middleware

(defn wrap-anti-forgery-websockets
  "Rejects cross-origin WebSocket requests.

   WebSocket requests are accepted only when the `Origin` header equals
   :biff.ring/base-url. Requests are rejected when the base URL is absent.
   Non-WebSocket requests are passed through unchanged."
  [handler]
  (impl.middleware/wrap-anti-forgery-websockets handler))

(defn wrap-path-param-uuids
  "Decodes compact UUID values in the request's :path-params map.

   Other path parameter values are left unchanged. This reverses the UUID
   encoding performed by path."
  [handler]
  (impl.middleware/wrap-path-param-uuids handler))

(defn wrap-nippy-params
  "Decodes the `npy` request parameter and merges its value into :params.

   The decoded value is merged only when it is a map. Missing, malformed, and
   non-map values are ignored."
  [handler]
  (impl.middleware/wrap-nippy-params handler))

(defn wrap-resource
  "Serves classpath resources before calling `handler`.

   Resources are loaded from :biff.ring/root, which defaults to `public`. Also
   tries each value in :biff.ring/index-files after the request URI; this
   defaults to `[\"index.html\"]`."
  [handler]
  (impl.middleware/wrap-resource handler))

(defn wrap-internal-error
  "Catches exceptions, logs them, and returns an error response.

   Calls :biff.ring/on-error with the request plus `:status 500` and `:ex`. Uses
   a minimal HTML response when :biff.ring/on-error is absent."
  [handler]
  (impl.middleware/wrap-internal-error handler))

(defn wrap-log-requests
  "Logs the duration, response status, method, and URI of each request."
  [handler]
  (impl.middleware/wrap-log-requests handler))

(defn wrap-session
  "Adds Ring session middleware.

   Uses an encrypted cookie store when :biff.ring/cookie-secret is set. The
   secret may be a base64-encoded string or a zero-argument delayed secret. If
   absent, uses :biff.ring/fallback-session-store or a new in-memory store.

   Session cookies are HTTP-only. Their maximum age and SameSite value default
   to 60 days and `:lax`, respectively, and can be set with
   :biff.ring/session-max-age and :biff.ring/session-same-site."
  [handler]
  (impl.middleware/wrap-session handler))

(defn wrap-ssl
  "Adds HSTS and optional HTTPS redirect middleware.

   SSL behavior is enabled unless :biff.ring/secure is false. HSTS defaults to
   true and can be disabled with :biff.ring/hsts. Redirects default to false and
   can be enabled with :biff.ring/ssl-redirect."
  [handler]
  (impl.middleware/wrap-ssl handler))

(defn wrap-site-defaults
  "Adds middleware defaults for browser routes.

   Includes Ring site defaults, Muuntaja parameters and formats, Nippy query
   parameters, compact UUID path parameters, sessions, anti-forgery protection,
   and WebSocket origin checks. Static resources and sessions from Ring
   defaults are disabled in favor of wrap-resource and wrap-session."
  [handler]
  (impl.middleware/wrap-site-defaults handler))

(defn wrap-api-defaults
  "Adds Ring API defaults and Muuntaja parameters and formats."
  [handler]
  (impl.middleware/wrap-api-defaults handler))

(defn wrap-base-defaults
  "Adds middleware shared by browser and API routes.

   Includes HTTPS scheme adjustment, static resources, internal error handling,
   SSL behavior, and request logging."
  [handler]
  (impl.middleware/wrap-base-defaults handler))

;;;; biff.core integration

(defn use-jetty
  "Starts Jetty with :biff.ring/handler and adds a stop function to `ctx`.

   Merges `ctx` into each Ring request. :biff.ring/host and :biff.ring/port
   default to `localhost` and `8080`. Throws if :biff.ring/handler is absent."
  [ctx]
  (impl.server/use-jetty ctx))

(defn module
  "Returns a biff.core module that assembles a Ring handler.

   Collects :biff.ring/routes, :biff.ring/api-routes, and their middleware from
   other modules. Its init function provides :biff.ring/handler and an in-memory
   :biff.ring/fallback-session-store. The handler is rebuilt when the modules
   var's value changes."
  []
  (impl.server/module))
