(ns com.biffweb.datastar
  (:require [com.biffweb.core :as biff.core]
            [com.biffweb.datastar.impl :as impl])
  (:import
   (java.util.concurrent.locks Condition ReentrantLock)))

(biff.core/register
 {:biff.datastar/buffer-size   :int
  :biff.datastar/condition     [:fn #(instance? Condition %)]
  :biff.datastar/epoch         [:fn #(instance? clojure.lang.IAtom %)]
  :biff.datastar/get-user-id   'ifn?
  :biff.datastar/lock          [:fn #(instance? ReentrantLock %)]
  :biff.datastar/quality       :int
  :biff.datastar/rate-limit-ms [:and :int pos?]
  :biff.datastar/signals       'map?
  :biff.datastar/sse-request   :boolean
  :biff.datastar/tab-id        :uuid
  :biff.datastar/window-size   :int})

(defn init-opts
  "Returns a map of Datastar options for a hiccup element.

   On page load, sends an SSE request to the current URL. Also creates a
   :biff.datastar/client-tab-id signal. See `wrap-sse-render`.

   If :anti-forgery-token is passed in, sets a :biff.datastar/anti-forgery-token
   signal. See `wrap-signals`."
  {:arglists '([] [{:keys [anti-forgery-token]}])}
  ([] (impl/init-opts))
  ([opts] (impl/init-opts opts)))

(defn new-lock
  "Returns a map of parameters needed by `refresh` and `wrap-sse-render`.

   Includes:
   - :biff.datastar/lock
   - :biff.datastar/condition
   - :biff.datastar/epoch"
  []
  (impl/new-lock))

(defn refresh
  "Signals to `wrap-sse-render` that backend state has changed and thus a new
   payload should be rendered for connected clients.

   Typically called whenever a database transaction has been committed."
  {:arglists '([{:biff.datastar/keys [lock condition epoch]}])}
  [ctx]
  (impl/refresh ctx))

(defn wrap-sse-render
  "Parses signals and starts long-lived SSE connections when requested.

   First, parses Datastar signals using the same logic as `wrap-signals`.

   Then, if the request was triggered by `init-opts`, sets
   `:biff.datastar/sse-request true` on the Ring request and starts a long-lived
   SSE connection. `wrap-sse-render` will then call the wrapped handler (i.e.
   the handler which used `init-opts`) repeatedly whenever `refresh` has been
   called, pushing the response to the client in a datastar-patch-elements
   event. Responses are compressed with Brotli.

   Handlers which use `init-opts` must check the :biff.datastar/sse-request key.
   When it is set to true, the handler must return only the part of the page
   HTML that is meant to be patched into the DOM by Datastar. (Thus the response
   body's top-level element must have `id` set.)

     ;; Example handler response when :biff.datastar/sse-request is not set:
     {:status 200
      :body   (render-hiccup
               [:html
                [:head ...]
                [:body (biff.datastar/init-opts)
                 [:div {:id \"content\"}
                  ...]]])
      ...}

     ;; Example handler response when `:biff.datastar/sse-request true` is set:
     {:status 200
      :body   (render-hiccup
               [:div {:id \"content\"}
                ...])
      ...}

   The incoming Ring request must include the keys returned by `new-lock`. The
   same instances of those keys' values must be used when calling `refresh`. The
   request may also include:

   - :biff.datastar/rate-limit-ms
   - :biff.datastar/buffer-size
   - :biff.datastar/quality
   - :biff.datastar/window-size

   See the schema reference."
  [handler]
  (impl/wrap-sse-render handler))

(defn wrap-signals
  "Parses Datastar signals and sets them on :biff.datastar/signals.

   For Datastar requests (GET, POST, and all other methods), sets a
   :biff.datastar/signals map on the incoming request containing the parsed
   signals. Underscores are used as a keyword segment separator so that the
   signals map can contain namespaced keywords; see `signals-json`.

   Reads the :biff.datastar/client-tab-id signal and turns it into a UUID v5
   scoped by :biff.datastar/get-user-id which defaults to (:uid session).
   The new UUID is set on :biff.datastar/tab-id on the request and can safely
   be used as a primary key for backend tab state.

   If a :biff.datastar/anti-forgery-token signal is set, the x-csrf-token
   request header is set to its value."
  [handler]
  (impl/wrap-signals handler))

(defn module
  "Returns a biff.core module including:

   - `:biff.ring/site-middleware [wrap-sse-render]`
   - `:biff.core/on-tx refresh`
   - A :biff.core/init function that returns `(new-lock)`"
  []
  (impl/module))

(defn signals-json
  "Returns a JSON string that preserves namespaced keywords.

   Takes a (possibly nested) map of signals, e.g. {::my-signal 123}. Signal
   keywords are encoded used `_` as a separator, e.g. :foo.bar/baz becomes
   \"foo_bar_baz\". Signal keywords are not allowed to contain underscores prior
   to conversion, and they may not contain periods in the name.

   `wrap-signals` and `wrap-sse-render` convert these signals back to keywords."
  [signals]
  (impl/signals-json signals))

(defn signal-name
  "Converts a signal keyword/vector to a Datastar signal string.

   Uses the same conversion format as `signals-json`. You can reference a nested
   signal by passing in a vector:

     {:data-bind (signal-name [:foo.bar/baz :quux])}
     => data-bind=\"foo_bar_baz.quux\""
  [k]
  (impl/signal-name k))

(defn patch-signals
  "Returns a 200 Ring response containing a datastar-patch-signals event.

  Signals are encoded with `signals-json`."
  [signals]
  (impl/patch-signals signals))
