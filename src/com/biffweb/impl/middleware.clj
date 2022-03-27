(ns com.biffweb.impl.middleware
  (:require [clojure.stacktrace :as st]
            [clojure.string :as str]
            [com.biffweb.impl.util :as util]
            [com.biffweb.impl.xtdb :as bxt]
            [muuntaja.middleware :as muuntaja]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.middleware.defaults :as rd]
            [ring.middleware.resource :as res]
            [ring.middleware.session.cookie :as cookie]
            [rum.core :as rum]))

(defn wrap-anti-forgery-websockets [handler]
  (fn [{:keys [biff/base-url headers] :as req}]
    (if (and (str/includes? (str/lower-case (get headers "upgrade" "")) "websocket")
             (str/includes? (str/lower-case (get headers "connection" "")) "upgrade")
             (some? base-url)
             (not= base-url (get headers "origin")))
      {:status 403
       :headers {"content-type" "text/plain"}
       :body "Forbidden"}
      (handler req))))

(defn wrap-render-rum [handler]
  (fn [req]
    (let [response (handler req)]
      (if (vector? response)
        {:status 200
         :headers {"content-type" "text/html"}
         :body (str "<!DOCTYPE html>\n" (rum/render-static-markup response))}
        response))))

(defn wrap-index-files [handler {:keys [index-files]
                                 :or {index-files ["index.html"]}}]
  (fn [req]
    (->> index-files
         (map #(update req :uri str/replace-first #"/?$" (str "/" %)))
         (into [req])
         (some (wrap-content-type handler)))))

(defn wrap-resource [handler {:biff.middleware/keys [root index-files]
                              :or {root "public"
                                   index-files ["index.html"]}}]
  (let [resource-handler (wrap-index-files
                           #(res/resource-request % root)
                           {:index-files index-files})]
    (fn [req]
      (or (resource-handler req)
          (handler req)))))

(defn wrap-internal-error [handler {:biff.middleware/keys [on-error]
                                    :or {on-error util/default-on-error}}]
  (fn [req]
    (try
      (handler req)
      (catch Throwable t
        (st/print-stack-trace t)
        (flush)
        (on-error (assoc req :status 500 :ex t))))))

(defn wrap-log-requests [handler]
  (fn [req]
    (let [start (java.util.Date.)
          resp (handler req)
          stop (java.util.Date.)
          duration (- (inst-ms stop) (inst-ms start))]
      (printf "%3sms %s %-4s %s\n"
              (str duration)
              (:status resp "nil")
              (name (:request-method req))
              (str (:uri req)
                   (when-some [qs (:query-string req)]
                     (str "?" qs))))
      (flush)
      resp)))

(defn wrap-ring-defaults [handler {:biff.middleware/keys [session-store
                                                          cookie-secret
                                                          secure
                                                          session-max-age]
                                   :or {session-max-age (* 60 60 24 60)
                                        secure true}}]
  (let [session-store (if cookie-secret
                        (cookie/cookie-store
                          {:key (util/base64-decode cookie-secret)})
                        session-store)
        changes {[:session :store]                   session-store
                 [:session :cookie-name]             "ring-session"
                 [:session :cookie-attrs :max-age]   session-max-age
                 [:session :cookie-attrs :same-site] :lax
                 [:security :anti-forgery]           false
                 [:security :ssl-redirect]           false
                 [:static]                           false}
        ring-defaults (reduce (fn [m [path value]]
                                (assoc-in m path value))
                              (if secure
                                rd/secure-site-defaults
                                rd/site-defaults)
                              changes)]
    (rd/wrap-defaults handler ring-defaults)))

(defn wrap-env [handler sys]
  (fn [req]
    (handler (merge (bxt/assoc-db sys) req))))

(defn wrap-inner-defaults
  [handler opts]
  (-> handler
      muuntaja/wrap-params
      muuntaja/wrap-format
      (wrap-resource opts)
      (wrap-internal-error opts)
      wrap-log-requests))

(defn wrap-outer-defaults [handler opts]
  (-> handler
      (wrap-ring-defaults opts)
      (wrap-env opts)))
