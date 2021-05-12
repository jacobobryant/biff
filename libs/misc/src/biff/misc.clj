(ns biff.misc
  (:require [biff.util :as bu]
            [biff.util.protocols :as proto]
            [buddy.core.nonce :as nonce]
            [buddy.sign.jwt :as jwt]
            [clj-http.client :as http]
            [clojure.core.async :as async]
            [clojure.spec.alpha :as s]
            [clojure.stacktrace :as st]
            [lambdaisland.uri :as uri]
            [malli.core :as malc]
            [malli.error :as male]
            [malli.registry :as malr]
            [nrepl.server :as nrepl]
            [reitit.ring :as reitit]
            [ring.adapter.jetty9 :as jetty]
            [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.jetty9 :as sente-jetty]))

(defn use-nrepl [{:keys [biff.nrepl/port
                         biff.nrepl/quiet]
                  :as sys}]
  (when port
    (nrepl/start-server :port port)
    (spit ".nrepl-port" (str port))
    (when-not quiet
      (println "nrepl running on port" port)))
  sys)

(defn use-reitit [{:biff.reitit/keys [routes default-handlers mode]
                   :keys [biff/on-error]
                   :as sys}]
  (let [default-handlers (when on-error
                           (concat default-handlers
                                   [(reitit/create-default-handler
                                      (->> [[:not-found 404]
                                            [:method-not-allowed 405]
                                            [:not-acceptable 406]]
                                           (map (fn [[k status]]
                                                  [k #(on-error (assoc % :status status))]))
                                           (into {})))]))
        get-router (fn [] (reitit/router (bu/realize routes)))
        handler (fn []
                  (if (not-empty default-handlers)
                    (reitit/ring-handler
                      (get-router)
                      (apply reitit/routes default-handlers))
                    (reitit/ring-handler (get-router))))
        [get-router handler] (if (= mode :dev)
                               [get-router #((handler) %)]
                               [(constantly (get-router)) (handler)])]
    (assoc sys
           :biff.reitit/get-router get-router
           :biff/handler handler)))

(defn use-jetty [{:biff/keys [host port handler]
                  :biff.jetty/keys [quiet websockets]
                  :or {host "0.0.0.0"
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

(defn jwt-encrypt [claims secret]
  (jwt/encrypt
    (-> claims
      (assoc :exp (bu/add-seconds (java.util.Date.) (:exp-in claims)))
      (dissoc :exp-in))
    (bu/base64-decode secret)
    {:alg :a256kw :enc :a128gcm}))

(defn jwt-decrypt [token secret]
  (bu/catchall
    (jwt/decrypt
      token
      (bu/base64-decode secret)
      {:alg :a256kw :enc :a128gcm})))

(defn generate-secret [length]
  (bu/base64-encode (nonce/random-bytes length)))

(defn assoc-url [url & kvs]
  (str (apply uri/assoc-query url kvs)))

(defn send-mailgun [{:mailgun/keys [api-key endpoint from]} opts]
  (try
    (http/post endpoint
      {:basic-auth ["api" api-key]
       :form-params (merge {:from from} opts)})
    true
    (catch Exception e
      (println "send-mailgun failed:" (:body (ex-data e)))
      false)))

(defn malli-registry [registry]
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

(defn doc-type* [schema doc doc-types]
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

(defn use-sente [{:keys [biff.sente/adapter
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
                                                   :event-id id
                                                   :data ?data)]))))
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
