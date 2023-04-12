(ns com.biffweb
  (:require [clojure.java.io :as io]
            [clojure.stacktrace :as st]
            [clojure.string :as str]
            [com.biffweb.impl.auth :as auth]
            [com.biffweb.impl.middleware :as middle]
            [com.biffweb.impl.misc :as misc]
            [com.biffweb.impl.queues :as q]
            [com.biffweb.impl.rum :as brum]
            [com.biffweb.impl.time :as time]
            [com.biffweb.impl.util :as util]
            [com.biffweb.impl.util.ns :as ns]
            [com.biffweb.impl.util.reload :as reload]
            [com.biffweb.impl.xtdb :as bxt]))

;;;; Util

(defn pprint
  "Alias of clojure.pprint/pprint"
  [& args]
  (apply util/pprint args))

(defonce system (atom nil))

(defn start-system
  "Deprecated. See Biff v0.7.3 release notes.

  Starts a system from an initial system map.

  Stores the system in the com.biffweb/system atom. Returns the contents of the
  atom. See https://biffweb.com/docs/reference/system-composition"
  [init]
  (util/start-system system init))

(defn refresh
  "Deprecated. See Biff v0.7.3 release notes.

  Stops the system, refreshes source files, and restarts the system.

  The system is stopped by calling all the functions in (:biff/stop
  @com.biffweb/system). (:biff/after-refresh @system) is a fully-qualified
  symbol which will be resolved and called after refreshing. See
  https://biffweb.com/docs/reference/system-composition"
  []
  (util/refresh @system))

(defn use-config
  "Reads config from an edn file and merges into ctx.

  The config file's contents should be a map from environments to config keys
  and values, for example:

  {:prod {:host \"example.com\"
          :port 8080}
   :dev {:merge [:prod]
         :host \"localhost\"}}

  The current environment should be stored in the BIFF_ENV environment variable.
  The default value is `prod`. To inherit config from other environments, set
  :merge to a sequence of environment keys."
  [{:keys [biff/config] :or {config "config.edn"} :as ctx}]
  (merge ctx (util/read-config config)))

(defn sh
  "Runs a shell command.

  Returns the output if successful; otherwise, throws an exception."
  [& args]
  (apply util/sh args))

(defn assoc-some
  "Like assoc, but skips kv pairs where the value is nil."
  [m & kvs]
  (apply util/assoc-some m kvs))

(defn pred->
  "Convenience fn for (cond-> x (pred x) f)"
  [x pred f]
  (cond-> x (pred x) f))

(defn join
  "Returns a sequence where the elements of coll are separated by sep."
  [sep xs]
  (util/join sep xs))

(defn safe-merge
  "Like merge, but throws an exception if any maps share keys."
  [& ms]
  (apply util/safe-merge ms))

(defn normalize-email
  "Normalizes an email address to make future lookups easier.

  Trims leading and trailing whitespace and converts to lower case. Returns nil
  if the email is empty after trimming."
  [email]
  (util/normalize-email email))

(defn use-when
  "Passes the system map to components only if (f system) is true.

  See https://biffweb.com/docs/reference/system-composition"
  [f & components]
  (apply util/use-when f components))

(defn sha256
  "Returns the SHA256 hash of string."
  [string]
  (util/sha256 string))

(defn base64-encode
  "Converts a byte array to a base64 string."
  [bytes]
  (util/base64-encode bytes))

(defn base64-decode
  "Converts a base64 string to a byte array."
  [string]
  (util/base64-decode string))

(defn anomaly?
  "Returns true if x is an anomaly.

  See https://github.com/cognitect-labs/anomalies"
  [x]
  (util/anomaly? x))

(defn anom
  "Constructs an anomaly.

  Example: (anom :incorrect
                 \"Invalid parameter\"
                 {:info \"x should be an integer\"})

  See https://github.com/cognitect-labs/anomalies"
  [category & [message & [opts]]]
  (util/anom category message opts))

(defn select-ns-as
  "Selects and renames keys from m based on the namespace.

  Examples:

  (select-ns-as {:foo/a 1, :foo.bar/b 2, :baz/c 3} 'foo 'quux)
  => {:quux/a 1, :quux.bar/b 2}

  (select-ns-as {:foo/a 1, :foo.bar/b 2, :baz/c 3} 'foo nil)
  => {:a 1, :bar/b 2}"
  [m ns-from ns-to]
  (ns/select-ns-as m ns-from ns-to))

(defmacro catchall
  "Wraps body in (try ... (catch Exception _ nil))"
  [& body]
  `(try ~@body (catch Exception ~'_ nil)))

(defmacro catchall-verbose
  "Like catchall, but prints exceptions."
  [& body]
  `(try
     ~@body
     (catch Exception e#
       (st/print-stack-trace e#))))

(defmacro letd
  "Like let, but transparently wraps all bindings with delay.

  Examples:

  (macroexpand-1 '(letd [a 1]
                    a))
  => (let [a (delay 1)]
       @a)

  (letd [a (do (println \"a evaluated\")
               1)
         {:keys [b]} (do (println \"b evaluated\")
                         {:b 2})
         [_ _ c] (do (println \"c evaluated\")
                     [1 2 3])]
    (if (even? b)
      a
      c))
  =>
  (out) b evaluated
  (out) a evaluated
  1"
  [bindings & body]
  (apply util/letd* bindings body))

(defmacro fix-print
  "Ensures that print output doesn't get swallowed by e.g. an editor nrepl plugin.

  Binds *out*, *err* and *flush-on-newline* to their root values.

  (fix-print
    (println \"hello\"))"
  [& body]
  (apply util/fix-print* body))

(defn eval-files!
  "Evaluates any modified files and their dependents via clojure.tools.namespace."
  [{:keys [biff/eval-paths]
    :or {eval-paths ["src"]}}]
  (swap! reload/global-tracker reload/refresh eval-paths)
  nil)

(defn add-libs
  "Loads new dependencies in deps.edn via tools.deps.alpha.

  Ensures that a DynamicClassLoader is available so that this works even when
  not evaluated from the repl. See
  https://ask.clojure.org/index.php/10761/clj-behaves-different-in-the-repl-as-opposed-to-from-a-file"
  []
  (util/add-libs))

(defn delete-old-files
  "Deletes files descended from the given directory that are older than a given threshold.

  dir:          A path to a directory.
  age-seconds:  Files will only be deleted if it's been at least this number of seconds since they
                were last modified. Defaults to 30 seconds.
  exts:         An optional collection of filename extentions. If provided, files will only be
                deleted if they end with one of the extentions.

  For example:
  (delete-old-files {:dir \"target/resources/public\"
                     :exts [\".html\"]})"
  [{:keys [dir exts age-seconds]
    :or {age-seconds 30} :as opts}]
  (util/delete-old-files opts))

;;;; Middleware

(defn wrap-anti-forgery-websockets
  "Ensure that websocket upgrade requests pass a CSRF check.

  If the client requests a websocket upgrade, the Origin header must be the
  same as the :biff/base-url key in the request map. Otherwise a 403
  response is given."
  [handler]
  (middle/wrap-anti-forgery-websockets handler))

(defn wrap-render-rum
  "If handler returns a vector, pass it to rum.core/render-static-markup and return a 200 response."
  [handler]
  (middle/wrap-render-rum handler))

(defn wrap-index-files
  "If handler returns nil, try again with each index file appended to the URI in turn."
  [handler {:keys [index-files]
            :or {index-files ["index.html"]} :as opts}]
  (middle/wrap-index-files handler opts))

(defn wrap-resource
  "Serves static resources with ring.middleware.resource/wrap-resource-request.

  root:         The resource root from which static files should be served.
  index-files:  See wrap-index-files.

  Checks for a static resource first. If none is found, passes the request to
  handler.

  The single-arity version is preferred. In that case, options can be set on
  the incoming Ring request."
  ([handler {:biff.middleware/keys [root index-files]
             :or {root "public"
                  index-files ["index.html"]}
             :as ctx}]
   (middle/wrap-resource handler ctx))
  ([handler]
   (middle/wrap-resource handler)))

(defn wrap-internal-error
  "Catches exceptions from handler, prints a stack trace, and returns a 500 response.

  You may optionally provide on-error, a single-argument function that receives
  the request map with the :status key set to 500. The default implementation
  returns a plain Internal Server Error message.

  The single-arity version is preferred. In that case, options can be set on
  the incoming Ring request."
  ([handler {:biff.middleware/keys [on-error] :as ctx}]
   (middle/wrap-internal-error handler ctx))
  ([handler]
   (middle/wrap-internal-error handler)))

(defn wrap-log-requests
  "Prints execution time, status, request method, uri and query params for each request."
  [handler]
  (middle/wrap-log-requests handler))

(defn wrap-https-scheme
  "Sets the :scheme key to :https on incoming Ring requests.

  This is necessary when using a reverse proxy (e.g. Nginx), otherwise
  wrap-absolute-redirects will set the redirect scheme to http.

  The following options can be set on incoming Ring requests:

   - :biff.middleware/secure (default: true)
     If false, this middleware will be disabled."
  [handler]
  (middle/wrap-https-scheme handler))

(defn wrap-session
  "A wrapper for ring.middleware.session/wrap-session.

  The following options can be set on incoming Ring requests:

   - :biff.middleware/cookie-secret
     A 16-bit base64-encoded secret. If set, sessions will be backed by
     encrypted cookies. Takes precedence over session-store.

   - :biff.middleware/session-store
     A session store for use with ring.middleware.session/wrap-session.
     Required if cookie-secret isn't set.

   - :biff.middleware/secure (default: true)
     Sets the session cookie to https-only.

   - :biff.middleware/session-max-age (default: (* 60 60 24 60))
     The session cookie's max age, in seconds.

   - :biff.middleware/session-same-site (default: :lax)
     The value of the Same-Site header for the session cookie."
  [handler]
  (middle/wrap-session handler))

(defn wrap-ssl
  "A wrapper for ring.middleware.ssl/{wrap-hsts,wrap-ssl-redirect}.

  The following options can be set on incoming Ring requests:

   - :biff.middleware/secure (default: true)
     If false, this middleware will be disabled.

   - :biff.middleware/hsts (default: true)
     If true, include wrap-hsts.

   - :biff.middleware/ssl-redirect (default: false)
     If true, include wrap-ssl-redirect. Don't enable this if you're using a
     reverse proxy (like Nginx)."
  [handler]
  (middle/wrap-ssl handler))

(defn wrap-site-defaults
  "A collection of middleware for website routes."
  [handler]
  (middle/wrap-site-defaults handler))

(defn wrap-api-defaults
  "A collection of middleware for API routes."
  [handler]
  (middle/wrap-api-defaults handler))

(defn wrap-base-defaults
  "A collection of middleware for website and API routes."
  [handler]
  (middle/wrap-base-defaults handler))

(defn use-wrap-ctx
  "Deprecated. biff/use-jetty does this now.

  A Biff component that merges the context map into incoming Ring requests."
  [{:keys [biff/handler] :as ctx}]
  (middle/use-wrap-ctx ctx))

(defn wrap-ring-defaults
  "Deprecated. Use wrap-base-defaults, wrap-site-defaults, and wrap-api-defaults instead.

  Wraps handler with ring.middleware.defaults/wrap-defaults.

  secure:          if true, uses ring.middleware.defaults/secure-site-defaults,
                   else uses site-defaults.
  session-store:   passed to wrap-defaults under the [:session :store] path.
  sesion-max-age:  the number of seconds after which a session should expire.

  If secret is set and (secret :biff.middleware/cookie-secret) returns non-nil,
  session-store is set with ring.middleware.session.cookie/cookie-store.

  Disables CSRF checks. You must wrap non-API routes with
  ring.middleware.anti-forgery. The Biff project template does this by default.
  Disables SSL redirects under the assumption that this is handled by e.g.
  NGINX. Also sets SameSite=Lax explicitly on the session cookie."
  [handler {:keys [biff/secret]
            :biff.middleware/keys [session-store
                                   secure
                                   session-max-age]
            :or {session-max-age (* 60 60 24 60)
                 secure true}
            :as ctx}]
  (middle/wrap-ring-defaults handler ctx))

(defn wrap-env
  "Deprecated. use-jetty handles this now.

  Merges (merge-context system) with incoming requests."
  [handler system]
  (middle/wrap-env handler system))

(defn wrap-inner-defaults
  "Deprecated. Use wrap-base-defaults, wrap-site-defaults, and wrap-api-defaults instead.

  Wraps handler with various middleware which don't depend on the system map.

  Includes wrap-log-requests, wrap-internal-error, wrap-resource, and
  Muuntaja's wrap-params and wrap-format (see https://github.com/metosin/muuntaja).
  opts is passed to wrap-resource and wrap-internal-error.

  This function can wrap a Ring handler outside of a call to biff/start-system,
  For example:

  (def handler (wrap-inner-defaults ... {}))

  (defn start []
    (biff/start-system
      {:biff/handler #'handler
       ...}))

  This way, handler and its middleware can be redefined from the repl without
  needing to restart the system."
  [handler opts]
  (middle/wrap-inner-defaults handler opts))

(defn use-outer-default-middleware
  "Deprecated. Use wrap-base-defaults, wrap-site-defaults, and wrap-api-defaults instead.

  A Biff component that wraps :biff/handler with middleware that depends on the system map.

  Includes wrap-ring-defaults and wrap-env."
  [ctx]
  (update ctx :biff/handler middle/wrap-outer-defaults ctx))

;;;; XTDB

(defn start-node
  "A higher-level version of xtdb.api/start-node.

  Calls xtdb.api/sync before returning the node.

  topology:   One of #{:standalone :jdbc}.
  kv-store:   One of #{:rocksdb :lmdb}. Default :rocksdb
  dir:        A path to store RocksDB instances in.
  jdbc-spec,
  pool-opts:  Used only when topology is :jdbc. Passed in as
              {:xtdb.jdbc/connection-pool
               {:db-spec jdbc-spec :pool-opts pool-opts ...}}.
  opts:       Additional options to pass to xtdb.api/start-node.
  tx-fns:     A map of transaction functions to be saved after indexing
              finishes. See save-tx-fns!"
  [{:keys [topology dir opts jdbc-spec pool-opts kv-store tx-fns]
    :or {kv-store :rocksdb}
    :as options}]
  (bxt/start-node options))

(defn use-xt
  "A Biff component that starts an XTDB node.

  Sets :biff.xtdb/node on the system map. topology, kv-store, dir, opts and
  tx-fns are passed to start-node. Any keys matching :biff.xtdb.jdbc/* or
  :biff.xtdb.jdbc-pool/* are passed in as jdbc-spec and pool-opts,
  respectively.

  If :biff/secret is set, the value of :biff.xtdb.jdbc/password will be replaced
  with (secret :biff.xtdb.jdbc/password)."
  [{:keys [biff/secret]
    :biff.xtdb/keys [topology kv-store dir opts tx-fns]
    :as ctx}]
  (bxt/use-xt ctx))

(defn use-tx-listener
  "If on-tx or plugins is provided, starts an XTDB transaction listener.

  plugins:  A var containing a collection of plugin maps. Each plugin map may
            contain an :on-tx key.
  features: Deprecated. Alias for plugins. plugins takes precedence.
  on-tx:    Deprecated. Use plugins instead. If set, takes precedence over
            plugins.

  Calls each on-tx function in plugins whenever a new transaction is
  successfully indexed. on-tx receives the system map and the transaction, i.e.
  (on-tx ctx tx). tx is the transaction as returned by (xtdb.api/open-tx-log
  node tx-id true). on-tx will not be called concurrently: if a second
  transaction is indexed while on-tx is still running, use-tx-listener will
  wait until it finishes before passing the second transaction."
  [{:keys [biff/plugins biff/features biff.xtdb/on-tx biff.xtdb/node] :as ctx}]
  (bxt/use-tx-listener ctx))

(defn assoc-db
  "Sets :biff/db on the context map to (xt/db node)"
  [{:keys [biff.xtdb/node] :as ctx}]
  (bxt/assoc-db ctx))

(defn q
  "Convenience wrapper for xtdb.api/q.

  If the :find value is not a vector, results will be passed through
  (map first ...). Also throws an exception if (count args) doesn't match
  (count (:in query))."
  [db query & args]
  (apply bxt/q db query args))

(defn lazy-q
  "Calls xtdb.api/open-q and passes a lazy seq of the results to a function.

  Accepts the same arguments as xtdb.api/open-q, except the last argument is a
  function which must process the results eagerly. Also includes the same
  functionality as biff/q."
  [db query & args]
  (apply bxt/lazy-q db query args))

(defn lookup
  "Returns the first document found with the given key(s) and value(s).

  For example:
  (lookup db :user/email \"hello@example.com\")
  => {:xt/id #uuid \"...\", :user/email \"hello@example.com\"}

  You can pass in a custom pull expression if needed (default is '[*]):
  (lookup db '[:xt/id {:msg/_user [:msg/text]}] :user/email \"hello@example.com\")
  => {:xt/id #uuid \"...\"
      :msg/_user ({:msg/text \"hello\"}
                  {:msg/text \"how do you do\"})}"
  [db & args]
  (apply bxt/lookup db args))

(defn lookup-all
  "Like lookup, but returns multiple documents."
  [db & args]
  (apply bxt/lookup-all db args))

(defn lookup-id
  "Returns the ID of the first document found with the given key(s) and value(s).

  For example:
  (lookup-id db :user/email \"hello@example.com\")
  => #uuid \"...\""
  [db & kvs]
  (apply bxt/lookup-id db kvs))

(defn lookup-id-all
  "Like lookup-id, but returns multiple documents."
  [db & kvs]
  (apply bxt/lookup-id-all db kvs))

(def ^:nodoc tx-xform-tmp-ids bxt/tx-xform-tmp-ids)
(def ^:nodoc tx-xform-upsert bxt/tx-xform-upsert)
(def ^:nodoc tx-xform-unique bxt/tx-xform-unique)
(def ^:nodoc tx-xform-main bxt/tx-xform-main)
(def ^:nodoc default-tx-transformers bxt/default-tx-transformers)

(defn biff-tx->xt
  "Converts the given Biff transaction into an XT transaction.

  The elements of biff-tx may be maps (in which case they are treated as Biff
  operations) or vectors (in which case they are treated as XT operations). For
  example:

  [{:db/doc-type :user
    :xt/id #uuid \"...\"
    :user/name \"example\"}
   [:xtdb.api/put {:xt/id #uuid \"...\"}]]

  biff-tx may optionally be a function which takes ctx and returns a Biff
  transaction.

  See https://biffweb.com/docs/reference/transactions."
  [{:keys [biff/now biff/db biff/malli-opts] :as ctx} biff-tx]
  (bxt/biff-tx->xt ctx biff-tx))

(defn submit-with-retries
  "Submits an XT transaction, retrying up to three times if there is contention.
  Blocks until the transaction is indexed or aborted.

  make-tx is a one-argument function that takes the context map and returns an
  XT transaction. The :biff/db and :biff/now keys in ctx will be updated before
  each time make-tx is called."
  [ctx make-tx]
  (bxt/submit-with-retries ctx make-tx))

(defn submit-tx
  "High-level wrapper over xtdb.api/submit-tx. See biff-tx->xt.

  If retry is true, the transaction will be passed to submit-with-retries."
  [{:keys [biff.xtdb/retry biff.xtdb/node]
    :or {retry true}
    :as ctx}
   biff-tx]
  (bxt/submit-tx ctx biff-tx))

(defn save-tx-fns!
  "Saves (or updates) the given transaction functions.

  For example, given the map {:foo '(fn ...)}, a transaction of the form
  [:xtdb.api/put {:xt/id :foo :xt/fn '(fn ...)}] will be submitted.

  If all the given functions are already in the database and haven't been
  changed, then no transaction will be submitted."
  [node tx-fns]
  (bxt/save-tx-fns! node tx-fns))

(def tx-fns
  "A map of Biff-provided transaction functions. See use-xt and save-tx-fns!

  Includes the following tx fns:

  :biff/ensure-unique - Aborts the transaction if more than one document
  contains the given key-value pairs. For example:

  (biff/submit-tx ctx
    [{:db/doc-type :user
      :db/op :update
      :xt/id user-id
      :user/name \"example\"}
     [:xtdb.api/fn :biff/ensure-unique {:user/name \"example\"}]])"
  bxt/tx-fns)

;;;; Rum

(defn render
  "Renders body with rum/render-static-markup and returns a 200 response."
  [body]
  (brum/render body))

(defn unsafe
  "Returns {:dangerouslySetInnerHTML {:__html html}}, for use with Rum."
  [html]
  (brum/unsafe html))

(def emdash
  "A Rum data structure for an em dash."
  brum/emdash)

(def endash
  "A Rum data structure for an en dash."
  brum/endash)

(def nbsp
  "A Rum data structure for a non-breaking space."
  brum/nbsp)

(defn g-fonts
  "Returns a link element for requesting families from Google fonts.

  For example:
  (g-fonts [\"Nunito+Sans:wght@900\"])
  => [:link {:rel \"stylesheet\", :href ...}]"
  [families]
  (brum/g-fonts families))

(defn base-html
  "Wraps contents in an :html and :body element with various metadata set.

  font-families:  A collection of families to request from Google fonts (see g-fonts).
  head:           Additional Rum elements to include inside the head."
  [{:base/keys [title
                description
                lang
                image
                icon
                url
                canonical
                font-families
                head]
    :as opts}
   & contents]
  (apply brum/base-html opts contents))

(defn form
  "Returns a [:form ...] element.

  hidden:  A map from names to values, which will be converted to
           [:input {:type \"hidden\" ...}] fields.
  opts:    Options for the :form element (with hidden removed).

  Sets :method to \"post\" by default, and includes a CSRF token (via
  ring.middleware.anti-forgery/*anti-forgery-token*)."
  [{:keys [hidden] :as opts} & body]
  (apply brum/form opts body))

(defn export-rum
  "Generate HTML files and write them to a directory.

  pages:  A map from paths to Rum data structures, e.g.
          {\"/\" [:div \"hello\"]}. Paths that end in / will have index.html
          appended to them.
  dir:    A path to the root directory where the files should be saved, e.g.
          \"target/resources/public\"."
  [pages dir]
  (brum/export-rum pages dir))

;;;; Time

(defn now
  "Same as (java.util.Date.)"
  []
  (java.util.Date.))

(def rfc3339
  "Same as \"yyyy-MM-dd'T'HH:mm:ss.SSSXXX\", for use with parse-date and format-date."
  time/rfc3339)

(defn parse-date
  "Parses date (a string) using java.text.SimpleDateFormat.

  If format isn't provided, uses rfc3339."
  [date & [format]]
  (time/parse-date date format))

(defn format-date
  "Formats date using java.text.SimpleDateFormat.

  If format isn't provided, uses rfc3339."
  [date & [format]]
  (time/format-date date format))

(defn crop-date
  "Passes date through format-date and parse-date, in order to remove any
  information not captured in the format.

  For example:
  (crop-date #inst \"2022-03-27T09:13:34.182-00:00\" \"yyyy\")
  => #inst \"2022-01-01T08:00:00.000-00:00\""
  [date format]
  (time/crop-date date format))

(defn crop-day
  "Same as (crop-date \"yyyy-MM-dd\")"
  [date]
  (time/crop-day date))

(defn elapsed?
  "Returns true if t2 occurs at least x units after t1.

  unit can be :seconds, :minutes, :hours, :days, or :weeks."
  [t1 t2 x unit]
  (time/elapsed? t1 t2 x unit))

(defn between-hours?
  "Returns true if t is between the hours of h1 and h2 UTC.

  For example:
  (between-hours? #inst \"2022-03-27T14:18:34.360-00:00\" 14 16)
  => true"
  [t h1 h2]
  (time/between-hours? t h1 h2))

(defn add-seconds
  "Returns a new java.util.Date with the given number of seconds added."
  [date seconds]
  (time/add-seconds date seconds))

;;;; Misc

(defn use-hawk
  "Deprecated. Use use-beholder instead.

  use-beholder is a drop-in replacement for use-hawk, except that keys must be
  prefixed with :biff.beholder/ instead of :biff.hawk/"
  [{:biff.hawk/keys [on-save exts paths]
    :or {paths ["src" "resources"]}
    :as ctx}]
  (misc/use-hawk ctx))

(defn use-beholder
  "A Biff component that runs code when files are changed, via Beholder.

  See https://github.com/nextjournal/beholder.

  enabled:  If false, this component is a no-op. Default true.
  on-save:  A single-argument function to call whenever a file is saved.
            Receives the system map as a parameter. Subsequent file saves
            that occur within one second are ignored.
  paths:    A collection of root directories to monitor for file changes.
  exts:     If exts is non-empty, files that don't end in one of the extensions
            will be ignored."
  [{:biff.beholder/keys [on-save exts paths enabled]
    :or {paths ["src" "resources"]
         enabled true}
    :as ctx}]
  (misc/use-beholder ctx))

(defn reitit-handler
  "Convenience wrapper for reitit.ring/ring-handler.

  Only one of router or routes needs to be given. If you pass in routes, it
  will be wrapped with (reitit.ring/router routes). on-error is an optional
  Ring handler. The request map passed to it will include a :status key (either
  404, 405, or 406).

  Includes reitit.ring/redirect-trailing-slash-handler."
  [{:keys [router routes on-error] :as opts}]
  (misc/reitit-handler opts))

(defn use-jetty
  "A Biff component that starts a Jetty web server."
  [{:biff/keys [host port handler]
    :or {host "localhost"
         port 8080}
    :as ctx}]
  (misc/use-jetty ctx))

(defn jwt-encrypt
  "Convenience wrapper for buddy.sign.jwt/encrypt.

  Returns a string token. secret is a base64-encoded string used to encrypt the
  token. A secret can be generated with (com.biffweb/generate-secret 32).
  exp-in is the number of seconds in the future at which the token should
  expire. claims is passed to buddy.sign.jwt/encrypt as-is, except that :exp is
  set based on exp-in."
  [{:keys [exp-in] :as claims} secret]
  (misc/jwt-encrypt claims secret))

(defn jwt-decrypt
  "Convenience wrapper for buddy.sign.jwt/decrypt.

  token is a string as returned by jwt-encrypt. secret is a base64-encoded
  string that was used to encrypt token. Returns the claims passed to
  jwt-encrypt. Returns nil if the token is invalid or expired."
  [token secret]
  (misc/jwt-decrypt token secret))

(defn use-chime
  "A Biff component for running scheduled tasks with Chime (https://github.com/jarohen/chime)

  plugins:  A var containing a collection of plugin maps. Each plugin map may
            contain a :tasks key, which contains a collection of task maps.
  features: Deprecated. Alias for plugins. plugins takes precedence.
  tasks:    Deprecated. Use plugins instead. If set, takes precedence over
            plugins

  For example:
  (def plugin
    {:tasks [{:task (fn [ctx] (println \"hello there\"))
              :schedule (iterate #(biff/add-seconds % 60) (java.util.Date.))}]})"
  [{:keys [biff/plugins biff/features biff.chime/tasks] :as ctx}]
  (misc/use-chime ctx))

(defn mailersend
  "Sends an email with MailerSend.

  See https://developers.mailersend.com/api/v1/email.html#send-an-email. Does a
  POST request on the /v1/email endpoint and returns the X-Message-Id response
  header on success. On failure, prints an error message and returns false.

  opts is a map which will be converted to JSON and included as the body of the
  request. defaults is a map from paths to default values. It will be combined
  with opts. For example:

  (mailersend {:mailersend/api-key \"...\"
               :mailersend/defaults {[:from :email] \"hello@mail.example.com\"
                                     [:from :name] \"My Application\"
                                     [:reply_to :email] \"hello@example.com\"
                                     [:reply_to :name] \"My Application\"}}
              {:to [{:email \"recipient@example.com\"}]
               :subject \"Some subject\"
               :text \"Some text\"
               :from {:name \"This will override the default value of 'My Application'\"}})"
  [{:keys [mailersend/api-key
           mailersend/defaults] :as ctx}
   opts]
  (misc/mailersend ctx opts))

(defn generate-secret
  "Generates a random byte array and returns it as a base64 string.

  The bytes are generated with buddy.core.nonce/random-bytes, which uses a
  secure random number generator."
  [length]
  (misc/generate-secret length))

(defn use-random-default-secrets
  "Deprecated. If secrets aren't set in secrets.env, they can be generated with
  `bb generate-secrets`.

  A Biff component that merges temporary secrets into the system map if needed.

  Sets :biff.middleware/cookie-secret and :biff/jwt-secret if they are nil. The
  secrets will not persist if the system is restarted. Can be useful in
  development. e.g. a config.edn.TEMPLATE can be checked into a project's
  repository without secrets set. Contributers can run the project by copying
  the file to config.edn, without needing to modify it.

  This component should not be relied upon in production; instead you should
  save secrets in config.edn. This is done automatically at project setup time
  for new Biff projects."
  [ctx]
  (misc/use-random-default-secrets ctx))

(defn use-secrets
  "Sets :biff/secret to a function which will return the value for a given secret.
  Also ensures that secrets for :biff.middleware/cookie-secret and
  :biff/jwt-secret are set. If they aren't, exits with an error message.

  For example, if the environment variable SOME_API_KEY is set to FOO and
  the :some-service/api-key config option is set to \"SOME_API_KEY\", then
  you can get the API key like so:

  (defn handler [{:keys [biff/secret]}]]
    (let [api-key (secret :some-service/api-key)]
      ...))

  You could also just call (System/getenv \"SOME_API_KEY\"), which is fine in
  application code. But Biff's internal code uses :biff/secret so that you can
  override it if you want to store secrets somewhere else."
  [ctx]
  (misc/use-secrets ctx))

(defn merge-context
  "Returns the context map with additional data merged in.

  Calls (merge-context-fn ctx). By default, adds an XT database object to
  :biff/db."
  [{:keys [biff/merge-context-fn]
    :or {merge-context-fn assoc-db}
    :as ctx}]
  (merge-context-fn ctx))

(defn doc-schema
  "Returns a [:map ...] schema for use with Malli.

  Example:

  (doc-schema
   {:required [:user/name
               [:user/email :string]]
    :optional [[:user/phone :string]]})
  =>
  [:map
   {:closed true}
   :user/name
   [:user/email :string]
   [:user/phone {:optional true} :string]]

  wildcards is map from namespace symbols to predicate functions. Any key-value
  pairs in the map with a matching key namespace will be valid iff the value
  passes the predicate function. For example:

  (doc-schema
   {:required [[:user/email :string]]
    :wildcards {'user.signup-params string?}})

  ;; Valid:
  {:user/email \"hello@example.com\"
   :user.signup-params/utm_source \"twitter\"}"
  [{:keys [required optional closed wildcards]
    :or {closed true}
    :as opts}]
  (misc/doc-schema opts))

;;;; Queues

(defn use-queues
  "A Biff component that creates in-memory queues and thread pools to consume them.

  plugins       A var containing a collection of plugin maps. Each plugin map
                may contain a :queues key, which contains a collection of queue
                config maps. See below. Required.
  features:     Deprecated. Alias for plugins. plugins takes precedence.
  enabled-ids:  An optional set of queue IDs. If non-nil, only queues in the
                set will be created.
  stop-timeout: When shutting down, the number of milliseconds to wait before
                killing any running job consumers. Default 10000.

  Adds a :biff/queues key to the system map which contains a map of queue IDs
  to `java.util.concurrent.BlockingQueue`s. Use (.add queue {...}) to submit a
  job. Jobs are arbitrary maps. Each queue will be consumed by a fixed-size
  thread pool.

  Queue config maps may have the following keys:

  id:        Used as a key in the :biff/queues map. Required.
  consumer:  A one-argument function that will receive a job whenever one is
             available. Receives the system map with :biff/job and :biff/queue
             keys set. biff/merge-context will be called on the system map
             before the consumer is called. Required.
  n-threads: The number of worker threads in the consumer thread pool for this
             queue. Default 1.
  queue-fn:  A zero-arg function that returns a BlockingQueue instance. By
             default, a PriorityBlockingQueue with a custom comparator will be
             used: jobs may include a :biff/priority key, which defaults to 10.
             Lower numbers have higher priority.

  Example:

  (defn echo-consumer [{:keys [biff/job] :as ctx}]
    (prn :echo job)
    (when-some [callback (:biff/callback job)]
      (callback job)))

  (def plugin
    {:queues [{:id :echo
               :consumer #'echo-consumer}]})

  (biff/submit-job ctx :echo {:foo \"bar\"})
  =>
  (out) :echo {:foo \"bar\"}
  true

  @(biff/submit-job-for-result ctx :echo {:foo \"bar\"})
  =>
  (out) :echo {:foo \"bar\", :biff/callback #function[...]}
  {:foo \"bar\", :biff/callback #function[...]}"
  [{:keys [biff/plugins
           biff/features
           biff.queues/enabled-ids
           biff.queues/stop-timeout]
    :as ctx}]
  (q/use-queues ctx))

(defn submit-job
  "Convenience function which calls (.add (get-in ctx [:biff/queues queue-id]) job)"
  [ctx queue-id job]
  (q/submit-job ctx queue-id job))

(defn submit-job-for-result
  "Like submit-job, but returns a promise which will contain the result of the
  queue operation.

  A :biff/callback key, containing a one-argument function, will be added to
  the job. The consumer function must pass a result to this callback. If the
  result is an Exception object, it will be re-thrown on the current thread. An
  exception will also be thrown if the callback function is not called within
  the number of milliseconds specified by result-timeout."
  [{:keys [biff.queues/result-timeout]
    :or {result-timeout 20000}
    :as ctx}
   queue-id
   job]
  (q/submit-job-for-result ctx queue-id job))

;;;; Authentication

(defn authentication-plugin
  "A Biff plugin that includes backend routes for passwordless authentication.

  Returns a plugin map that can be included with the rest of your app's
  plugins.

  There are routes for sending signin links and six-digit signin codes. Either
  method may be used for signing in or signing up. If a new user tries to sign
  in, a user document will be created after they click the link or enter the
  code.

  Signin links usually have a bit less friction, while signin codes are
  resilient to embedded browsers and PWAs on mobile devices. As a default
  recommendation, you can use links for your signup form and codes for your
  signin form.


  INSTRUCTIONS
  ============

  You should provide forms that POST to /auth/send-link and/or /auth/send-code.
  You should also provide pages at the following URLS: /link-sent,
  /verify-link, and /verify-code. See ROUTES for details.

  Your :biff/send-email function must accept the following templates:
   - :signin-link, with parameters :to, :url, and :user-exists.
   - :signin-code, with parameters :to, :code, and :user-exists.


  SCHEMA
  ======

  :biff.auth/code documents are used to store users' signin codes:

  {:biff.auth.code/id :uuid
   :biff.auth/code [:map {:closed true}
                    [:xt/id :biff.auth.code/id]
                    [:biff.auth.code/email :string]
                    [:biff.auth.code/code :string]
                    [:biff.auth.code/created-at inst?]
                    [:biff.auth.code/failed-attempts integer?]]}


  OPTIONS
  =======

  :biff.auth/app-path
  -------------------
  Default: \"/app\"

  Users will be redirected here after they sign in successfully.


  :biff.auth/invalid-link-path
  ----------------------------
  Default: \"/signin\"

  Users will be redirected here if they click on a signin link that is invalid
  or expired.


  :biff.auth/check-state
  ----------------------
  Default: true

  If true and the user opens the signin link on a different device or browser
  from the one the link was requested on, then the user will have to re-enter
  their email address before being authenticated. This helps to prevent people
  from being tricked into signing into someone else's account.


  :biff.auth/new-user-tx
  ----------------------
  Default:

    (fn [ctx email]
      [{:db/doc-type :user
        :db.op/upsert {:user/email email}
        :user/joined-at :db/now}])

  A function that returns a transaction for creating a new user.


  :biff.auth/get-user-id
  ----------------------
  Default:

    (fn [db email]
      (biff/lookup-id db :user/email email)))

  A function that returns the document ID for the user with the given email, or
  nil if there is no such user.


  :biff.auth/single-opt-in
  ------------------------
  Default: false

  If true, the user's account will be created immediately after they submit
  their email address. Otherwise, their account will only be created after they
  verify their address by clicking the link/entering the code.


  :biff.auth/email-validator
  --------------------------
  Default:

    (fn [ctx email]
      (and email (re-matches #\".+@.+\\..+\" email)))

  A predicate function that checks if a given email address is valid. For extra
  protection, you can supply a function that calls out to an email validation
  API, like Mailgun's.


  ROUTES
  ======

  All routes that are protected by Recaptcha take a required
  g-recaptcha-response form parameter, which should be set automatically by the
  Recaptcha client library.


  POST /auth/send-link
  --------------------

  Sends a signin link to the user's email address. The link goes to
  /auth/verify-link/:token.

  Form parameters:
   - email:    The user's email address.
   - on-error: A path to redirect to in case of error. Default /.

  Redirects:
   - /sent-link?email={email}:       Success.
   - {on-error}?error=recaptcha:     The recaptcha test failed.
   - {on-error}?error=invalid-email: The :biff.auth/email-validator function
                                     returned false.
   - {on-error}?error=send-failed:   The :biff/send-email function returned
                                     false.

  Protected by Recaptcha.


  GET /auth/verify-link/:token
  ----------------------------

  A link to this endpoint is generated and sent to the user by the
  /auth/send-link endpoint. On success, assigns the :uid parameter in the
  user's session to their user ID. If the user doesn't exist yet, creates a new
  account.

  Path parameters:
   - token: A JWT generated by the /auth/send-link endpoint. Expires after one
            hour.

  Redirects:
   - {:biff.auth/app-path}:          Success.
   - {:biff.auth/invalid-link-path}: The token was invalid or expired.
   - /verify-link?token={token}:     The user needs to provide their email
                                     address again so we can ensure it matches
                                     the address contained in the token.


  POST /auth/verify-link
  ----------------------

  Verifies that the user's email address matches one contained in the token. On
  success, assigns the :uid parameter in the user's session to their user ID.
  If the user doesn't exist yet, creates a new account.

  Form parameters:
   - token: A JWT generated by the /auth/send-link endpoint. Expires after one
            hour.
   - email: The user's email address.

  Redirects:
   - {:biff.auth/app-path}
     Success.

   - {:biff.auth/invalid-link-path}
     The token was invalid or expired.

   - /verify-link?token={token}&error=incorrect-email
     The email address provided didn't match the one in the token.


  POST /auth/send-code
  --------------------

  Sends a six-digit (numeric) signin code to the user's email address. The code
  expires after three minutes or three failed verification attempts.

  Form parameters:
   - email:    The user's email address.
   - on-error: A path to redirect to in case of error. Default /.

  Redirects:
   - /verify-code?email={email}:     Success.
   - {on-error}?error=recaptcha:     The recaptcha test failed.
   - {on-error}?error=invalid-email: The :biff.auth/email-validator function
                                     returned false.
   - {on-error}?error=send-failed:   The :biff/send-email function returned
                                     false.

  Protected by Recaptcha.


  POST /auth/verify-code
  ----------------------

  Verifies that the code provided by the user matches the one they were sent.
  On success, assigns the :uid parameter in the user's session to their user
  ID.

  Form parameters:
   - email: The user's email address.
   - code:  A six-digit code.

  Redirects:
   - {:biff.auth/app-path}
     Success.

   - /verify-code?error=invalid-code&email={email}
     The given code was incorrect or expired.

  Protected by Recaptcha.


  POST /auth/signout
  ------------------

  Removes the :uid from the user's session. Redirects to /."
  [options]
  (auth/plugin options))

(def recaptcha-disclosure
  "A [:div ...] element that contains a disclosure statement, which should be
  shown on pages that include a Recaptcha test."
  auth/recaptcha-disclosure)

(defn recaptcha-callback
  "Returns a [:script ...] element which defines a Javascript function, for use
  as a callback with Recaptcha. The callback function submits the form with the
  given ID."
  [fn-name form-id]
  (auth/recaptcha-callback fn-name form-id))

(defn- write-doc-data [dest]
  (let [sections (->> (with-open [r (io/reader (io/resource "com/biffweb.clj"))]
                        (doall (line-seq r)))
                      (map-indexed (fn [i line]
                                     (when-some [title (second (re-find #";;;; (.*)" line))]
                                       {:line i
                                        :title title})))
                      (filter some?)
                      reverse)
        metadata (->> (ns-publics 'com.biffweb)
                      vals
                      (keep (fn [v]
                              (let [m (meta v)
                                    section-title (->> sections
                                                       (filter #(< (:line %) (:line m)))
                                                       first
                                                       :title)]
                                (when-not (:nodoc m)
                                  (-> m
                                      (select-keys [:arglists :doc :line :name])
                                      (assoc :section section-title)))))))]
    (spit dest (with-out-str
                 (pprint metadata)))))

(comment
  (let [resources-dir (io/file "/home/jacob/dev/platypub/themes/biffweb2/resources/com/biffweb/theme")]
    (write-doc-data (str (io/file resources-dir "api.edn")))
    (io/copy (io/file "new-project.clj")
             (io/file resources-dir "new-project.clj_"))
    (print (sh "rsync" "-av" "--delete" "docs/" (str (io/file resources-dir "docs") "/")))))
