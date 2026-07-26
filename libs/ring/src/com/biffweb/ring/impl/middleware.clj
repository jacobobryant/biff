(ns com.biffweb.ring.impl.middleware
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [com.biffweb.ring.impl.path :as path]
            [muuntaja.middleware :as muuntaja]
            [ring.middleware.anti-forgery :as anti-forgery]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.middleware.defaults :as defaults]
            [ring.middleware.resource :as resource]
            [ring.middleware.session :as session]
            [ring.middleware.session.cookie :as cookie]
            [ring.middleware.session.memory :as memory]
            [ring.middleware.ssl :as ssl]
            [taoensso.nippy :as nippy]))

(def ^:private status->message
  {400 "Bad Request"
   401 "Unauthorized"
   403 "Forbidden"
   404 "Not Found"
   405 "Method Not Allowed"
   406 "Not Acceptable"
   500 "Internal Server Error"})

(defn on-error [{:keys [status biff.ring/on-error] :as ctx}]
  (if on-error
    (on-error ctx)
    {:status  status
     :headers {"content-type" "text/html; charset=utf-8"}
     :body    (str "<h1>" (status->message status "Error") "</h1>")}))

(defn- websocket-request? [{:keys [headers]}]
  (and (str/includes? (str/lower-case (get headers "upgrade" "")) "websocket")
       (str/includes? (str/lower-case (get headers "connection" "")) "upgrade")))

(defn wrap-anti-forgery-websockets [handler]
  (fn [{:keys [headers biff.ring/base-url] :as ctx}]
    (cond
      (not (websocket-request? ctx))
      (handler ctx)

      (nil? base-url)
      (do
        (log/warn "Rejecting websocket request because :biff.ring/base-url is not set.")
        (on-error (assoc ctx :status 403)))

      (not= base-url (get headers "origin"))
      (on-error (assoc ctx :status 403))

      :else
      (handler ctx))))

(defn wrap-path-param-uuids [handler]
  (fn [ctx]
    (handler
     (cond-> ctx
       (:path-params ctx)
       (update :path-params #(update-vals % path/decode-uuid))))))

(defn wrap-nippy-params [handler]
  (fn [ctx]
    (let [npy (or (get-in ctx [:params :npy])
                  (get-in ctx [:params "npy"]))]
      (handler
       (if-not npy
         ctx
         (try
           (let [value (nippy/fast-thaw (path/base64-decode npy))]
             (if (map? value)
               (update ctx :params merge value)
               ctx))
           (catch Exception _
             ctx)))))))

(defn wrap-resource [handler]
  (fn [{:biff.ring/keys [root index-files]
        :or             {root        "public"
                         index-files ["index.html"]}
        :as             ctx}]
    (or (->> index-files
             (map #(update ctx :uri str/replace-first #"/?$" (str "/" %)))
             (into [ctx])
             (some (wrap-content-type #(resource/resource-request % root))))
        (handler ctx))))

(defn wrap-internal-error [handler]
  (fn [ctx]
    (try
      (handler ctx)
      (catch Exception e
        (log/error e "Exception while handling request")
        (on-error (assoc ctx :status 500 :ex e))))))

(defn wrap-log-requests [handler]
  (fn [ctx]
    (let [start       (System/nanoTime)
          response    (handler ctx)
          duration-ms (quot (- (System/nanoTime) start) 1000000)]
      (log/infof "%3sms %s %-4s %s"
                 (str duration-ms)
                 (:status response "nil")
                 (some-> (:request-method ctx) name)
                 (str (:uri ctx)
                      (when-some [query-string (:query-string ctx)]
                        (str "?" query-string))))
      response)))

(defn wrap-session [handler]
  (fn [{:biff.ring/keys [secure
                         cookie-secret
                         session-store
                         fallback-session-store
                         session-max-age
                         session-same-site]
        :or             {session-max-age   (* 60 60 24 60)
                         secure            true
                         session-same-site :lax}
        :as             ctx}]
    (let [cookie-secret (force cookie-secret)

          session-store
          (or session-store
              (when cookie-secret
                (cookie/cookie-store
                 {:key (.decode (java.util.Base64/getDecoder)
                                ^String cookie-secret)}))
              fallback-session-store
              (memory/memory-store))]
      (when-not (or cookie-secret session-store)
        (log/warn "No cookie secret configured. Using in-memory Ring sessions."))
      ((session/wrap-session
        handler
        {:cookie-attrs {:max-age   session-max-age
                        :same-site session-same-site
                        :http-only true
                        :secure    secure}
         :store        session-store})
       ctx))))

(defn wrap-ssl [handler]
  (fn [{:biff.ring/keys [secure
                         hsts
                         ssl-redirect]
        :or             {secure       true
                         hsts         true
                         ssl-redirect false}
        :as             ctx}]
    ((cond-> handler
       (and secure hsts) ssl/wrap-hsts
       (and secure ssl-redirect) ssl/wrap-ssl-redirect)
     ctx)))

;; wrap-ssl is duplicated in case wrap-{api,site}-defaults is used by itself.

(defn wrap-site-defaults [handler]
  (-> handler
      wrap-anti-forgery-websockets
      anti-forgery/wrap-anti-forgery
      wrap-session
      wrap-path-param-uuids
      wrap-nippy-params
      muuntaja/wrap-params
      muuntaja/wrap-format
      (defaults/wrap-defaults
       (-> defaults/site-defaults
           (assoc-in [:security :anti-forgery] false)
           (assoc :session false)
           (assoc :static false)))
      wrap-ssl))

(defn wrap-api-defaults [handler]
  (-> handler
      muuntaja/wrap-params
      muuntaja/wrap-format
      (defaults/wrap-defaults defaults/api-defaults)
      wrap-ssl))

(defn wrap-base-defaults [handler]
  (-> handler
      ;; wrap-resource could conceptually be part of wrap-site-defaults, however
      ;; it needs to run before the reitit handler.
      wrap-resource
      wrap-internal-error
      wrap-ssl
      wrap-log-requests))
