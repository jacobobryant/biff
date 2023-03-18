(ns com.biffweb.impl.middleware
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [com.biffweb.impl.util :as util]
            [com.biffweb.impl.xtdb :as bxt]
            [muuntaja.middleware :as muuntaja]
            [ring.middleware.anti-forgery :as anti-forgery]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.middleware.defaults :as rd]
            [ring.middleware.resource :as res]
            [ring.middleware.session :as session]
            [ring.middleware.session.cookie :as cookie]
            [ring.middleware.ssl :as ssl]
            [rum.core :as rum]))

(defn wrap-debug [handler]
  (fn [ctx]
    (util/pprint [:request ctx])
    (let [resp (handler ctx)]
      (util/pprint [:response resp])
      resp)))

(defn wrap-anti-forgery-websockets [handler]
  (fn [{:keys [biff/base-url headers] :as ctx}]
    (if (and (str/includes? (str/lower-case (get headers "upgrade" "")) "websocket")
             (str/includes? (str/lower-case (get headers "connection" "")) "upgrade")
             (some? base-url)
             (not= base-url (get headers "origin")))
      {:status 403
       :headers {"content-type" "text/plain"}
       :body "Forbidden"}
      (handler ctx))))

(defn wrap-render-rum [handler]
  (fn [ctx]
    (let [response (handler ctx)]
      (if (vector? response)
        {:status 200
         :headers {"content-type" "text/html"}
         :body (str "<!DOCTYPE html>\n" (rum/render-static-markup response))}
        response))))

;; Deprecated; wrap-resource does this inline now.
(defn wrap-index-files [handler {:keys [index-files]
                                 :or {index-files ["index.html"]}}]
  (fn [ctx]
    (->> index-files
         (map #(update ctx :uri str/replace-first #"/?$" (str "/" %)))
         (into [ctx])
         (some (wrap-content-type handler)))))

(defn wrap-resource
  ([handler]
   (fn [{:biff.middleware/keys [root index-files]
         :or {root "public"
              index-files ["index.html"]}
         :as ctx}]
     (or (->> index-files
              (map #(update ctx :uri str/replace-first #"/?$" (str "/" %)))
              (into [ctx])
              (some (wrap-content-type #(res/resource-request % root))))
         (handler ctx))))
  ;; Deprecated, use 1-arg arity
  ([handler {:biff.middleware/keys [root index-files]
             :or {root "public"
                  index-files ["index.html"]}}]
   (let [resource-handler (wrap-index-files
                           #(res/resource-request % root)
                           {:index-files index-files})]
     (fn [ctx]
       (or (resource-handler ctx)
           (handler ctx))))))

(defn wrap-internal-error
  ([handler]
   (fn [{:biff.middleware/keys [on-error]
         :or {on-error util/default-on-error}
         :as ctx}]
     (try
       (handler ctx)
       (catch Throwable t
         (log/error t "Exception while handling request")
         (on-error (assoc ctx :status 500 :ex t))))))
  ;; Deprecated, use 1-arg arity
  ([handler {:biff.middleware/keys [on-error]
             :or {on-error util/default-on-error}}]
   (fn [ctx]
     (try
       (handler ctx)
       (catch Throwable t
         (log/error t "Exception while handling request")
         (on-error (assoc ctx :status 500 :ex t)))))))

(defn wrap-log-requests [handler]
  (fn [ctx]
    (let [start (System/nanoTime)
          resp (handler ctx)
          stop (System/nanoTime)
          duration (quot (- stop start) 1000000)]
      (log/infof "%3sms %s %-4s %s"
                 (str duration)
                 (:status resp "nil")
                 (name (:request-method ctx))
                 (str (:uri ctx)
                      (when-some [qs (:query-string ctx)]
                        (str "?" qs))))
      resp)))

(defn wrap-https-scheme [handler]
  (fn [{:keys [biff.middleware/secure] :or {secure true} :as ctx}]
    (handler (if (and secure (= :http (:scheme ctx)))
               (assoc ctx :scheme :https)
               ctx))))

(defn wrap-session [handler]
  (fn [{:keys [biff/secret]
        :biff.middleware/keys [session-store
                               cookie-secret
                               secure
                               session-max-age
                               session-same-site]
        :or {session-max-age (* 60 60 24 60)
             secure true
             session-same-site :lax}
        :as ctx}]
    (let [cookie-secret (if secret
                          (secret :biff.middleware/cookie-secret)
                          ;; For backwards compatibility
                          cookie-secret)
          session-store (if cookie-secret
                          (cookie/cookie-store
                           {:key (util/base64-decode cookie-secret)})
                          session-store)
          handler (session/wrap-session
                   handler
                   {:cookie-attrs {:max-age session-max-age
                                   :same-site session-same-site
                                   :http-only true
                                   :secure secure}
                    :store session-store})]
      (handler ctx))))

(defn wrap-ssl [handler]
  (fn [{:keys [biff.middleware/secure
               biff.middleware/hsts
               biff.middleware/ssl-redirect]
        :or {secure true
             hsts true
             ssl-redirect false}
        :as ctx}]
    (let [handler (if secure
                    (cond-> handler
                      hsts ssl/wrap-hsts
                      ssl-redirect ssl/wrap-ssl-redirect)
                    handler)]
      (handler ctx))))

(defn wrap-site-defaults [handler]
  (-> handler
      wrap-render-rum
      wrap-anti-forgery-websockets
      anti-forgery/wrap-anti-forgery
      wrap-session
      muuntaja/wrap-params
      muuntaja/wrap-format
      (rd/wrap-defaults (-> rd/site-defaults
                            (assoc-in [:security :anti-forgery] false)
                            (assoc-in [:responses :absolute-redirects] true)
                            (assoc :session false)
                            (assoc :static false)))))

(defn wrap-api-defaults [handler]
  (-> handler
      muuntaja/wrap-params
      muuntaja/wrap-format
      (rd/wrap-defaults rd/api-defaults)))

(defn wrap-base-defaults [handler]
  (-> handler
      wrap-https-scheme
      wrap-resource
      wrap-internal-error
      wrap-ssl
      wrap-log-requests))

(defn use-wrap-ctx [{:keys [biff/handler] :as ctx}]
  (assoc ctx :biff/handler (fn [req]
                             (handler (merge (bxt/merge-context ctx) req)))))

;;; Deprecated

(defn wrap-ring-defaults
  "Deprecated"
  [handler {:keys [biff/secret]
            :biff.middleware/keys [session-store
                                   cookie-secret
                                   secure
                                   session-max-age]
            :or {session-max-age (* 60 60 24 60)
                 secure true}
            :as ctx}]
  (let [cookie-secret (if secret
                        (secret :biff.middleware/cookie-secret)
                        ;; For backwards compatibility
                        cookie-secret)
        session-store (if cookie-secret
                        (cookie/cookie-store
                         {:key (util/base64-decode cookie-secret)})
                        session-store)
        changes {[:responses :absolute-redirects]    true
                 [:session :store]                   session-store
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
    (cond-> handler
      true (rd/wrap-defaults ring-defaults)
      ;; This is necessary when using a reverse proxy (e.g. Nginx), otherwise
      ;; wrap-absolute-redirects will set the redirect scheme to http.
      secure wrap-https-scheme)))

(defn wrap-env
  "Deprecated"
  [handler ctx]
  (fn [req]
    (handler (merge (bxt/merge-context ctx) req))))

(defn wrap-inner-defaults
  "Deprecated"
  [handler opts]
  (-> handler
      muuntaja/wrap-params
      muuntaja/wrap-format
      (wrap-resource opts)
      (wrap-internal-error opts)
      wrap-log-requests))

(defn wrap-outer-defaults
  "Deprecated"
  [handler opts]
  (-> handler
      (wrap-ring-defaults opts)
      (wrap-env opts)))
