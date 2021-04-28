(ns biff.middleware
  (:require [clojure.java.io :as io]
            [clojure.stacktrace :as st]
            [clojure.string :as str]
            [biff.util-tmp :as bu]
            [muuntaja.middleware :as muuntaja]
            [ring.middleware.defaults :as rd]
            [ring.util.codec :as codec]
            [ring.util.io :as rio]
            [ring.util.request :as request]
            [ring.util.time :as rtime]))

(defn wrap-static [handler {:keys [root path->file]
                            :or {root "public"
                                 path->file (comp io/file io/resource)}}]
  (fn [{:keys [request-method] :as req}]
    (or (when (#{:get :head} request-method)
          (let [path (str root (codec/url-decode (request/path-info req)))
                file (path->file path)
                file (if (some-> file (.isDirectory))
                       (path->file (str/replace-first path #"/?$" "/index.html"))
                       file)]
            (when (some-> file (.isFile))
              (bu/nest-string-keys
                (merge {:status 200
                        :headers/Content-Length (str (.length file))
                        :headers/Last-Modified (rtime/format-date (rio/last-modified-date file))}
                       (when (not= :head request-method)
                         {:body file})
                       (when (str/ends-with? (.getPath file) ".html")
                         {:headers/Content-Type "text/html"}))
                [:headers]))))
        (handler req))))

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
      (wrap-static (select-keys opts [:root :path->file]))
      (rd/wrap-defaults ring-defaults)
      (wrap-internal-error {:on-error on-error}))))
