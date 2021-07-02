(ns biff.misc
  "Functions that don't have enough siblings for their own library."
  (:require [biff.util :as bu]
            [biff.util.protocols :as proto]
            [buddy.core.nonce :as nonce]
            [buddy.sign.jwt :as jwt]
            [chime.core :as chime]
            [clj-http.client :as http]
            [clojure.core.async :as async]
            [clojure.edn :as edn]
            [clojure.spec.alpha :as s]
            [clojure.stacktrace :as st]
            [clojure.walk :refer [postwalk]]
            [lambdaisland.uri :as uri]
            [malli.core :as malc]
            [malli.error :as male]
            [malli.registry :as malr]
            [nrepl.server :as nrepl]
            [reitit.core :as reitit]
            [reitit.ring :as reitit-ring]
            [ring.adapter.jetty9 :as jetty]
            [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.jetty9 :as sente-jetty]))

(defn use-nrepl
  "Starts an nREPL server.

  no-op if port is nil/false."
  [{:keys [biff.nrepl/port
           biff.nrepl/quiet]
    :as sys}]
  (when port
    (nrepl/start-server :port port)
    (spit ".nrepl-port" (str port))
    (when-not quiet
      (println "nrepl running on port" port)))
  sys)

(defn use-reitit
  "Sets :biff/handler from Reitit routes.

  Also sets :biff.reitit/get-router to a function that returns the router.

  routes,
  default-handlers: Passed to Reitit.
  on-error:         A handler that takes a request with :status set to one of
                    #{404 405 406}. Added to default-handlers.
  mode:             If set to :dev, the routes will be re-compiled on each
                    request. routes is passed to biff.util/realize, so it can
                    be a var or a zero-arg function."
  [{:biff.reitit/keys [routes default-handlers mode]
    :keys [biff/on-error]
    :as sys}]
  (let [default-handlers (when on-error
                           (concat default-handlers
                                   [(reitit-ring/create-default-handler
                                      (->> [[:not-found 404]
                                            [:method-not-allowed 405]
                                            [:not-acceptable 406]]
                                           (map (fn [[k status]]
                                                  [k #(on-error (assoc % :status status))]))
                                           (into {})))]))
        get-router (fn [] (reitit-ring/router (bu/realize routes)))
        handler (fn []
                  (if (not-empty default-handlers)
                    (reitit-ring/ring-handler
                      (get-router)
                      (apply reitit-ring/routes default-handlers))
                    (reitit-ring/ring-handler (get-router))))
        [get-router handler] (if (= mode :dev)
                               [get-router #((handler) %)]
                               [(constantly (get-router)) (handler)])]
    (assoc sys
           :biff.reitit/get-router get-router
           :biff/handler handler)))

(defn use-jetty
  "Starts a Jetty web server.

  websockets: A map from paths to handlers, e.g. {\"/api/chsk\" ...}."
  [{:biff/keys [host port handler]
    :biff.jetty/keys [quiet websockets]
    :or {host "localhost"
         port 8080}
    :as sys}]
  (let [server (jetty/run-jetty handler
                                {:host host
                                 :port port
                                 :join? false
                                 :websockets websockets
                                 :allow-null-path-info true})]
    (when-not quiet
      (println "Jetty running on" (str "http://" host ":" port)))
    (update sys :biff/stop conj #(jetty/stop-server server))))

(defn jwt-encrypt
  "TODO add docstring"
  [claims secret]
  (jwt/encrypt
    (-> claims
        (assoc :exp (bu/add-seconds (java.util.Date.) (:exp-in claims)))
        (dissoc :exp-in))
    (bu/base64-decode secret)
    {:alg :a256kw :enc :a128gcm}))

(defn jwt-decrypt
  "TODO add docstring"
  [token secret]
  (bu/catchall
    (jwt/decrypt
      token
      (bu/base64-decode secret)
      {:alg :a256kw :enc :a128gcm})))

(defn generate-secret
  "Generates a base64 string from the given number of random bytes."
  [n-bytes]
  (bu/base64-encode (nonce/random-bytes n-bytes)))

(defn assoc-url
  "Adds query parameters to a URL."
  [url & kvs]
  (str (apply uri/assoc-query url kvs)))

(defn send-mailgun
  "Sends an email with Mailgun."
  [{:mailgun/keys [api-key endpoint from]} opts]
  (try
    (http/post endpoint
      {:basic-auth ["api" api-key]
       :form-params (merge {:from from} opts)})
    true
    (catch Exception e
      (println "send-mailgun failed:" (:body (ex-data e)))
      false)))

(defn malli-registry
  "Returns registry combined with the default registry."
  [registry]
  (malr/composite-registry
    malc/default-registry
    registry))

(defn- assert-valid* [schema doc-type doc]
  (when-not (proto/valid? schema doc-type doc)
    (throw
      (ex-info "Invalid schema."
               {:doc-type doc-type
                :doc doc
                :explain (proto/explain-human schema doc-type doc)}))))

(defn- doc-type* [schema doc doc-types]
  (first (filter #(proto/valid? schema % doc) doc-types)))

(defrecord MalliSchema [doc-types malli-opts]
  proto/Schema
  (valid? [_ doc-type doc]
    (malc/validate doc-type doc malli-opts))
  (explain-human [_ doc-type doc]
    (male/humanize (malc/explain doc-type doc malli-opts)))
  (assert-valid [this doc-type doc]
    (assert-valid* this doc-type doc))
  (doc-type [this doc]
    (doc-type* this doc doc-types)))

(defrecord SpecSchema [doc-types]
  proto/Schema
  (valid? [_ doc-type doc]
    (s/valid? doc-type doc))
  (explain-human [_ doc-type doc]
    (s/explain-str doc-type doc))
  (assert-valid [this doc-type doc]
    (assert-valid* this doc-type doc))
  (doc-type [this doc]
    (doc-type* this doc doc-types)))

(defn- sente-csrf-token-fn [{:keys [biff/uid] :as req}]
  (if (some? uid)
    (or
      (:anti-forgery-token req)
      (get-in req [:session :csrf-token])
      (get-in req [:session :ring.middleware.anti-forgery/anti-forgery-token])
      (get-in req [:session "__anti-forgery-token"]))
    ; Disable CSRF checks for anonymous users.
    (or
      (get-in req [:params :csrf-token])
      (get-in req [:headers "x-csrf-token"])
      (get-in req [:headers "x-xsrf-token"]))))

(defn use-sente
  "Starts a Sente channel socket and event router, and adds a Reitit route.

  The keys returned by sente/make-channel-socket! are prefixed with biff.sente
  and added to the system map."
  [{:keys [biff.sente/adapter
           biff.sente/event-handler
           biff.sente/route
           biff.reitit/routes]
    :or {adapter (sente-jetty/get-sch-adapter)
         route "/api/chsk"}
    :as sys}]
  (let [{:keys [ajax-get-or-ws-handshake-fn
                ajax-post-fn]
         :as result} (sente/make-channel-socket!
                       adapter
                       {:user-id-fn :client-id
                        :csrf-token-fn sente-csrf-token-fn})
        sys (merge sys (bu/prepend-keys "biff.sente" result))
        stop-router (sente/start-server-chsk-router!
                      (:ch-recv result)
                      (fn [{:keys [?reply-fn ring-req client-id id ?data] :as event}]
                        (try
                          (let [response (event-handler (merge sys ring-req event))]
                            (when ?reply-fn
                              (?reply-fn response)))
                          (catch Throwable t
                            (st/print-stack-trace t)
                            (flush)
                            ((:send-fn result) client-id
                             [:biff/error (bu/anom :fault "Internal server error."
                                                   {:event-id id
                                                    :data ?data})]))))
                      (merge {:simple-auto-threading? true}
                             (bu/select-ns-as sys 'biff.sente.router nil)))]
    (-> sys
        (assoc :biff.reitit/routes
               (fn []
                 (conj (bu/realize routes)
                       [route {:get ajax-get-or-ws-handshake-fn
                               :post ajax-post-fn}])))
        (update :biff/stop into [#(async/close! (:ch-recv result))
                                 stop-router]))))

(defn parse-form-tx
  "Gets a Biff transaction from a form POST.

  Returns a tuple of Biff transaction, redirect path. See
  https://biff.findka.com/#receiving-transactions.

  get-router: A function returning a Reitit router.
  tx-info:    A map serialized as EDN.
  coercions:  A map from keywords (field types) to coercion functions, e.g.
              {:text identity, :checkbox #(= % \"on\")}.

  tx-info has the following keys:
  :tx           - A Biff transaction, with symbols (corresponding to field
                  names) marking where field values should be inserted, e.g.
                  {[:foo \"abc\"] {:foo/text 'text-field}}
  :fields       - A map from symbols to field types, e.g. {'text-field :text}
  :redirect     - The name of a Reitit route (a keyword). The route must have
                  :biff/redirect true for redirects to be authorized.
  :path-params,
  :query-params - Maps used by Reitit to resolve the redirect path."
  [{:keys [biff.reitit/get-router]
    {:keys [tx-info] :as params} :params
    :as req}
   {:keys [coercions]}]
  (let [{:keys [tx
                fields
                redirect
                path-params
                query-params]
         :as tx-info} (edn/read-string tx-info)
        route (reitit/match-by-name (get-router) redirect path-params)
        path (reitit/match->path route query-params)
        redirect-ok (get-in route [:data :biff/redirect])
        tx (postwalk (fn [x]
                       (if-some [field-type (get fields x)]
                         ((get coercions field-type identity)
                          (get params (keyword x)))
                         x))
                     tx)]
    (if-not redirect-ok
      (bu/throw-anom :incorrect "Invalid redirect route name."
                     {:redirect redirect})
      [tx path])))

(defn use-chime
  "Runs some functions periodically with jarohen/chime.

  tasks is a list of maps with the following keys:
  :fn     - A function to run. Receives the system map as its only argument.
  :period - How often to call the function (in minutes), starting from last
            midnight UTC.
  :offset - How many minutes forward to shift the starting time (default 0)."
  [{:keys [biff.chime/tasks] :as sys}]
  (let [now (java.util.Date)]
    (update sys :biff/stop into
            (for [{:keys [offset period]
                   task-fn :fn
                   :or {offset 0}} tasks]
              (let [closeable (chime/chime-at
                                (->> (bu/add-seconds (bu/last-midnight now)
                                                     (* 60 offset))
                                     (iterate #(bu/add-seconds % (* period 60)))
                                     (drop-while #(bu/compare< % now))
                                     (map #(.toInstant %)))
                                (fn [_] (task-fn sys)))]
                #(.close closeable))))))
