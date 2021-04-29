(ns biff.misc
  (:require [biff.util-tmp :as bu]
            [buddy.core.nonce :as nonce]
            [buddy.sign.jwt :as jwt]
            [clj-http.client :as http]
            [lambdaisland.uri :as uri]
            [malli.core :as malc]
            [malli.error :as male]
            [malli.registry :as malr]
            [nrepl.server :as nrepl]
            [reitit.ring :as reitit]
            [ring.adapter.jetty9 :as jetty]
            ))

(defn start-nrepl [{:keys [biff.nrepl/port
                           biff.nrepl/quiet]
                    :or {port 7888}
                    :as sys}]
  (when port
    (nrepl/start-server :port port)
    (spit ".nrepl-port" (str port))
    (when-not quiet
      (println "nrepl running on port" port)))
  sys)

(defn merge-reitit [{:biff.reitit/keys [routes default-handlers on-error mode]
                     :as sys}]
  (let [default-handlers (when on-error
                           (concat default-handlers
                                   [(reitit/create-default-handler
                                      (->> [:not-found :method-not-allowed :not-acceptable]
                                           (map (fn [k]
                                                  [k #(on-error (assoc % :reitit-error k))]))
                                           (into {})))]))
        get-router (fn [] (reitit/router (bu/realize routes)))
        handler (fn []
                  (if (not-empty default-handlers)
                    (reitit/ring-handler
                      (router)
                      (apply reitit/routes default-handlers))
                    (reitit/ring-handler (router))))
        [get-router handler] (if (= mode :dev)
                               [get-router #((handler) %)]
                               [(constantly (get-router)) (handler)])]
    (assoc sys
           :biff.reitit/get-router get-router
           :biff.web/handler handler)))

(defn start-jetty [{:biff.web/keys [host port handler]
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

(defn malli-assert [schema x opts]
  (when-not (malc/validate schema x opts)
    (throw
      (ex-info "Invalid schema."
        {:value x
         :schema schema
         :explain (male/humanize (malc/explain schema x opts))}))))

(defn malli-debug [schema x opts]
  (if (malc/validate schema x opts)
    true
    (do
      (println (male/humanize (malc/explain schema x opts)))
      false)))

(defn malli-registry [reg]
  (malr/composite-registry malc/default-registry reg))
