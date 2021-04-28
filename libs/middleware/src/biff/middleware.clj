(ns biff.middleware
  (:require
    [clojure.stacktrace :as st]
    [biff.util-tmp :as bu]
    [muuntaja.middleware :as muuntaja]
    [ring.middleware.defaults :as rd]))

(defn wrap-flat-keys [handler]
  (fn [{:keys [session params] :as req}]
    (some-> req
      (merge
        (bu/prepend-keys "session" session)
        (bu/prepend-keys "params" params))
      handler
      (bu/nest-string-keys [:headers :cookies]))))

(defn wrap-env [handler env]
  (fn [req]
    (handler (merge env req))))

(defn wrap-internal-error [handler {:keys [on-error]}]
  (fn [req]
    (try
      (handler req)
      (catch Throwable t
        (st/print-stack-trace t)
        (flush)
        (on-error req t)))))

(defn wrap-defaults [handler {:keys [session-store
                                     secure
                                     session-max-age
                                     on-error
                                     env]
                              :or {session-max-age (* 60 60 24 90)}}]
  (let [ring-defaults (-> (if secure
                            rd/secure-site-defaults
                            rd/site-defaults)
                        (update :session merge {:store session-store
                                                :cookie-name "ring-session"})
                        (update-in [:session :cookie-attrs]
                          merge {:max-age session-max-age
                                 :same-site :lax})
                        (update :security merge {:anti-forgery false
                                                 :ssl-redirect false})
                        (assoc :static false))]
    (-> handler
      (wrap-env env)
      wrap-flat-keys
      muuntaja/wrap-params
      muuntaja/wrap-format
      (rd/wrap-defaults ring-defaults)
      (wrap-internal-error {:on-error on-error}))))
