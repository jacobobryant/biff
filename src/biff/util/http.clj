(ns biff.util.http
  (:require
    [biff.util :as bu]
    [cemerick.url :as url]
    [ring.middleware.head :as head]
    [trident.util :as u]
    [ring.util.time :as rtime]
    [ring.util.io :as rio]
    [clojure.string :as str]
    [ring.util.codec :as codec]
    [ring.util.request :as request]
    [clojure.java.io :as io]
    [ring.middleware.defaults :as rd]
    [ring.middleware.session.cookie :as cookie]
    [reitit.ring :as reitit]
    [muuntaja.middleware :as muuntaja]
    [rum.core :as rum]
    [clojure.set :as set]
    [taoensso.sente :as sente]
    [taoensso.sente.server-adapters.immutant :refer [get-sch-adapter]]
    [ring.middleware.anti-forgery :as anti-forgery]))

(defn wrap-authorize-admin [handler]
  (anti-forgery/wrap-anti-forgery
    (fn [{:keys [uri] :as req}]
      (if (get-in req [:session :admin])
        (handler req)
        {:status 302
         :headers {"Location" (str "/biff/auth?next=" (url/url-encode uri))}
         :body ""}))))

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

(defn ring-settings [debug cookie-key]
  (-> (if debug
        rd/site-defaults
        rd/secure-site-defaults)
    (update :session merge {:store (cookie/cookie-store {:key cookie-key})
                            :cookie-name "ring-session"})
    (update :security merge {:anti-forgery false
                             :ssl-redirect false})
    (assoc :static false)))

(defn nice-response [resp]
  (when resp
    (-> {:body "" :status 200}
      (merge resp)
      (bu/nest-string-keys [:headers :cookies]))))

(defn wrap-nice-response [handler]
  (comp nice-response handler))

(defn make-handler [{:keys [root debug routes cookie-path default-routes cookie-key]}]
  (let [cookie-key (or cookie-key (some-> cookie-path bu/cookie-key))
        not-found #(file-response % (io/file (str root "/404.html")))
        default-handlers (->> [(when debug
                                 (file-handler "www-dev"))
                               (when (and debug root)
                                 (file-handler root))
                               (reitit/create-default-handler
                                 {:not-found not-found})]
                           (concat default-routes)
                           (filter some?))]
    (->
      (reitit/ring-handler
        (reitit/router routes)
        (apply reitit/routes default-handlers))
      wrap-nice-response
      muuntaja/wrap-format
      (rd/wrap-defaults (ring-settings debug cookie-key)))))

(defn wrap-sente-handler [handler]
  (fn [{:keys [uid ?data ?reply-fn] :as event}]
    (some->>
      (with-out-str
        (let [event (-> event
                      (set/rename-keys {:uid :sente-uid})
                      (merge (get-in event [:ring-req :session])))
              response (try
                         (handler event ?data)
                         (catch Exception e
                           (.printStackTrace e)
                           ::exception))]
          (when ?reply-fn
            (?reply-fn response))))
      not-empty
      (.print System/out))))

(defn init-sente [{:keys [route-name handler]}]
  (let [{:keys [ch-recv send-fn connected-uids
                ajax-post-fn ajax-get-or-ws-handshake-fn]}
        (sente/make-channel-socket! (get-sch-adapter) {:user-id-fn :client-id})]
    {:reitit-route ["/api/chsk" {:get ajax-get-or-ws-handshake-fn
                             :post ajax-post-fn
                             :middleware [anti-forgery/wrap-anti-forgery]
                             :name route-name}]
     :close-router (sente/start-server-chsk-router! ch-recv
                     (wrap-sente-handler handler))
     :api-send send-fn
     :connected-uids connected-uids}))
