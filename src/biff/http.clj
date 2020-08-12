(ns biff.http
  (:require
    [ring.middleware.anti-forgery :as anti-forgery]
    [ring.middleware.head :as head]
    [trident.util :as u]
    [ring.util.time :as rtime]
    [ring.util.io :as rio]
    [clojure.string :as str]
    [ring.util.codec :as codec]
    [ring.util.request :as request]
    [clojure.java.io :as io]
    [ring.middleware.defaults :as rd]
    [reitit.ring :as reitit]))

(defn wrap-authorize [handler]
  (anti-forgery/wrap-anti-forgery
    (fn [req]
      (if (some? (get-in req [:session :uid]))
        (handler req)
        {:status 401
         :headers/Content-Type "text/plain"
         :body "Not authorized."}))))

(defn file-response [req file]
  (when (.isFile file)
    (head/head-response
      (u/assoc-some
        {:body file
         :status 200
         :headers/Content-Length (.length file)
         :headers/Last-Modified (rtime/format-date (rio/last-modified-date file))}
        :headers/Content-Type (when (str/ends-with? (.getPath file) ".html")
                                "text/html"))
      req)))

(defn file-handler [root]
  (fn [{:keys [request-method] :as req}]
    (when (#{:get :head} request-method)
      (let [path (str root (codec/url-decode (request/path-info req)))
            path (cond-> path
                   (.isDirectory (io/file path)) (str/replace-first #"/?$" "/index.html"))
            file (io/file path)]
        (file-response req file)))))

(defn nice-response [resp]
  (when resp
    (-> {:body "" :status 200}
      (merge resp)
      (u/nest-string-keys [:headers :cookies]))))

(defn wrap-nice-response [handler]
  (comp nice-response handler))

(defn make-handler [{:keys [session-store secure-defaults roots
                            not-found-path spa-path routes default-routes]}]
  (let [not-found (if not-found-path
                    #(-> %
                       (file-response (io/file not-found-path))
                       (assoc :status 404))
                    (constantly {:status 404
                                 :body "Not found."
                                 :headers/Content-Type "text/plain"}))
        default-handlers (concat
                           default-routes
                           (map file-handler roots)
                           (when spa-path
                             [#(file-response % (io/file spa-path))])
                           [(reitit/create-default-handler
                              {:not-found not-found})])
        ring-defaults (-> (if secure-defaults
                            rd/secure-site-defaults
                            rd/site-defaults)
                        (update :session merge {:store session-store
                                                :cookie-name "ring-session"})
                        (assoc-in [:session :cookie-attrs :max-age] (* 60 60 24 90))
                        (update :security merge {:anti-forgery false
                                                 :ssl-redirect false})
                        (assoc :static false))]
    (->
      (reitit/ring-handler
        (reitit/router routes)
        (apply reitit/routes default-handlers))
      wrap-nice-response
      (rd/wrap-defaults ring-defaults))))
