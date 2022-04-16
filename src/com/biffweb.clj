(ns com.biffweb
  (:require [clojure.string :as str]
            [com.biffweb.impl.middleware :as middle]
            [com.biffweb.impl.misc :as misc]
            [com.biffweb.impl.rum :as brum]
            [com.biffweb.impl.time :as time]
            [com.biffweb.impl.util :as util]
            [com.biffweb.impl.util.ns :as ns]
            [com.biffweb.impl.util.reload :as reload]
            [com.biffweb.impl.xtdb :as bxt]))

;;;; util

(defn pprint
  "Alias of clojure.pprint/pprint"
  [& args]
  (apply util/pprint args))

(defonce system (atom nil))

(defn start-system
  "Starts a system from an initial system map.

  Stores the system in the com.biffweb/system atom. See
  https://biffweb.com/docs/#system-composition"
  [init]
  (util/start-system system init))

(defn refresh
  "Stops the system, refreshes source files, and restarts the system.

  The system is stopped by calling all the functions in (:biff/stop
  @com.biffweb/system). (:biff/after-refresh @system) is a fully-qualified
  symbol which will be resolved and called after refreshing. See
  https://biffweb.com/docs/#system-composition"
  []
  (util/refresh @system))

(defn use-config
  "Reads config from (:biff/config sys), and edn file, and merges into sys.

  The config file's contents should be a map from environments to config keys
  and values, for example:

  {:prod {:host \"example.com\"
          :port 8080}
   :dev {:merge [:prod]
         :host \"localhost\"}}

  The current environment should be stored in the BIFF_ENV environment variable.
  The default value is `prod`. To inherit config from other environments, set
  :merge to a sequence of environment keys."
  [sys]
  (merge sys (util/read-config (:biff/config sys))))

(defn sh
  "Runs a shell command.

  Returns the output if successful; otherwise, throws an exception."
  [& args]
  (apply util/sh args))

(defn safe-merge
  "Like merge, but throws an exception if any maps share keys."
  [& ms]
  (apply util/safe-merge ms))

(defn normalize-email
  "Normalizes an email address to make future lookups easier.

  Trims leading and trailing whitespace and converts to lower case. Returns nil
  if the email is empty after trimming."
  [email]
  (some-> email str/trim str/lower-case not-empty))

(defn use-when
  "Passes the system map to components only if (f system) is true.

  See https://biffweb.com/docs/#system-composition"
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

;;;; misc

(defn use-hawk
  "A Biff component that runs code when files are changed, via Hawk.

  See (https://github.com/wkf/hawk).

  on-save:  A single-argument function to call whenever a file is saved.
            Receives the system map as a parameter. The function is called no
            more than once every 500 milliseconds.
  paths:    A collection of root directories to monitor for file changes.
  exts:     If exts is non-empty, files that don't end in one of the extensions
            will be ignored."
  [{:biff.hawk/keys [on-save exts paths]
    :or {paths ["src" "resources"]}
    :as sys}]
  (misc/use-hawk sys))

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
    :as sys}]
  (misc/use-jetty sys))

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
  "A Biff component for running scheduled tasks with Chime.

  See https://github.com/jarohen/chime. tasks is a collection of maps, for
  example:

  [{:task (fn [system] (println \"hello there\"))
    :schedule (iterate #(biff/add-seconds % 60) (java.util.Date.))}]

  This value of tasks would print \"hello there\" every 60 seconds. task is a
  single-argument function that receives the system map. schedule is a
  zero-argument function that returns a (possibly infinite) sequence of times
  at which to run the task function."
  [{:biff.chime/keys [tasks] :as sys}]
  (misc/use-chime sys))

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
           mailersend/defaults] :as sys}
   opts]
  (misc/mailersend sys opts))

(defn generate-secret
  "Generates a random byte array and returns it as a base64 string.

  The bytes are generated with buddy.core.nonce/random-bytes, which uses a
  secure random number generator."
  [length]
  (misc/generate-secret length))

(defn use-random-default-secrets
  "A Biff component that merges temporary secrets into the system map if needed.

  Sets :biff.middleware/cookie-secret and :biff/jwt-secret if they are nil. The
  secrets will not persist if the system is restarted. Can be useful in
  development. e.g. a config.edn.TEMPLATE can be checked into a project's
  repository without secrets set. Contributers can run the project by copying
  the file to config.edn, without needing to modify it.

  This component should not be relied upon in production; instead you should
  save secrets in config.edn. This is done automatically at project setup time
  for new Biff projects."
  [sys]
  (misc/use-random-default-secrets sys))

;;;; middleware

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
  handler."
  [handler {:biff.middleware/keys [root index-files]
            :or {root "public"
                 index-files ["index.html"]}
            :as opts}]
  (middle/wrap-resource handler opts))

(defn wrap-internal-error
  "Catches exceptions from handler, prints a stack trace, and returns a 500 response.

  You may optionally provide on-error, a single-argument function that receives
  the request map with the :status key set to 500. The default implementation
  returns a plain Internal Server Error message."
  [handler {:biff.middleware/keys [on-error]}]
  middle/wrap-internal-error)

(defn wrap-log-requests
  "Prints execution time, status, request method, uri and query params for each request."
  [handler]
  (middle/wrap-log-requests handler))

(defn wrap-ring-defaults
  "Wraps handler with ring.middleware.defaults/wrap-defaults.

  secure:          if true, uses ring.middleware.defaults/secure-site-defaults,
                   else uses site-defaults.
  cookie-secret:   if provided, session-store is set with
                   ring.middleware.session.cookie/cookie-store
  session-store:   passed to wrap-defaults under the [:session :store] path.
  sesion-max-age:  the number of seconds after which a session should expire.

  Disables CSRF checks. You must wrap non-API routes with
  ring.middleware.anti-forgery. The Biff project template does this by default.
  Disables SSL redirects under the assumption that this is handled by e.g.
  NGINX. Also sets SameSite=Lax explicitly on the session cookie."
  [handler {:biff.middleware/keys [session-store
                                   cookie-secret
                                   secure
                                   session-max-age]
            :or {session-max-age (* 60 60 24 60)
                 secure true}
            :as opts}]
  (middle/wrap-ring-defaults handler opts))

(defn wrap-env
  "Merges the system map with incoming requests and sets :biff/db.

  See assoc-db."
  [handler system]
  (middle/wrap-env handler system))

(defn wrap-inner-defaults
  "Wraps handler with various middleware which don't depend on the system map.

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
  "A Biff component that wraps :biff/handler with middleware that depends on the system map.

  Includes wrap-ring-defaults and wrap-env."
  [sys]
  (update sys :biff/handler middle/wrap-outer-defaults sys))

;;;; xtdb

(defn start-node
  "A higher-level version of xtdb.api/start-node.

  Calls xtdb.api/sync before returning the node.

  topology:   One of #{:standalone :jdbc}.
  dir:        A path to store RocksDB instances in.
  jdbc-spec,
  pool-opts:  Used only when topology is :jdbc. Passed in as
              {:xtdb.jdbc/connection-pool
               {:db-spec jdbc-spec :pool-opts pool-opts ...}}.
  opts:       Additional options to pass to xtdb.api/start-node."
  [opts]
  (bxt/start-node opts))

(defn use-xt
  "A Biff component that starts an XTDB node.

  Sets :biff.xtdb/node on the system map. topology, dir and opts are passed to
  start-node. Any keys matching :biff.xtdb.jdbc/* or :biff.xtdb.jdbc-pool/* are
  passed in as jdbc-spec and pool-opts, respectively."
  [{:biff.xtdb/keys [topology dir opts]
    :as sys}]
  (bxt/use-xt sys))

(defn use-tx-listener
  "If on-tx is provided, starts an XTDB transaction listener.

  Calls on-tx whenever a new transaction is successfully indexed. on-tx
  receives the system map and the transaction, i.e. (on-tx system tx). tx is
  the transaction as returned by (xtdb.api/open-tx-log node tx-id true). on-tx
  will not be called concurrently: if a second transaction is indexed while
  on-tx is still running, use-tx-listener will wait until it finishes."
  [{:keys [biff.xtdb/on-tx biff.xtdb/node] :as sys}]
  (bxt/use-tx-listener sys))

(defn assoc-db
  "Sets :biff/db on the system map to (xt/db node)"
  [{:keys [biff.xtdb/node] :as sys}]
  (bxt/assoc-db sys))

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
  (apply bxt/lazy-q args))

(defn lookup
  "Returns the first document found with the given key and value.

  For example:
  (lookup db :user/email \"hello@example.com\")
  => {:xt/id #uuid \"...\", :user/email \"hello@example.com\"}"
  [db k v]
  (bxt/lookup db k v))

(defn lookup-id
  "Returns the ID of the first document found with the given key and value.

  For example:
  (lookup db :user/email \"hello@example.com\")
  => #uuid \"...\""
  [db k v]
  (bxt/lookup-id db k v))

(defn submit-tx
  "High-level wrapper over xtdb.api/submit-tx.

  See https://biffweb.com/docs/#transactions."
  [{:keys [biff.xtdb/node] :as sys}
   biff-tx]
  (bxt/submit-tx sys biff-tx))

;;;; rum

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

;;;; time

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
