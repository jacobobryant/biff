(ns biff.middleware
  (:require [clojure.java.io :as io]
            [clojure.stacktrace :as st]
            [clojure.string :as str]
            [biff.util :as bu]
            [muuntaja.middleware :as muuntaja]
            [ring.middleware.defaults :as rd]
            [ring.middleware.session.cookie :as cookie]
            [ring.util.codec :as codec]
            [ring.util.io :as rio]
            [ring.util.request :as request]
            [ring.util.time :as rtime]))

(defn static-response [{:keys [request-method path path->file]}]
  (let [file (path->file path)
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

(defn wrap-static [handler {:keys [root
                                   path->file
                                   spa-path
                                   spa-exclude-paths
                                   spa-client-paths]
                            :or {root "public"
                                 path->file (comp io/file io/resource)
                                 spa-exclude-paths ["/js/"
                                                    "/css/"
                                                    "/cljs/"
                                                    "/img/"
                                                    "/assets/"]}}]
  (fn [{:keys [request-method] :as req}]
    (let [static-resp (delay
                        (when (#{:get :head} request-method)
                          (static-response
                            {:path (str root (codec/url-decode (request/path-info req)))
                             :path->file path->file
                             :request-method request-method})))
          handler-resp (delay (handler req))
          spa-resp (delay
                     (when (and (#{:get :head} request-method)
                                spa-path
                                (if spa-client-paths
                                  (contains? spa-client-paths (:uri req))
                                  (not (some #(str/starts-with? (:uri req) %)
                                             spa-exclude-paths))))
                       (static-response {:path (str root spa-path)
                                         :path->file path->file
                                         :request-method request-method})))]
      (cond
        @static-resp @static-resp
        (and (= 404 (:status @handler-resp)) @spa-resp) @spa-resp
        :default @handler-resp))))

(defn wrap-flat-keys [handler]
  (fn [{:keys [session params] :as req}]
    (some-> req
            (merge (bu/prepend-keys "session" session)
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
        (let [status (or (bu/anom->http-status (ex-data t)) 500)]
          (if (= status 500)
            (st/print-stack-trace t)
            (do
              (printf "%s\n" (.getMessage t))
              (some-> (ex-data t) bu/pprint)))
          (flush)
          (on-error (assoc req :status status :ex t)))))))

(defn wrap-log-requests [handler]
  (fn [req]
    (let [resp (handler req)]
      (printf "%s  %-4s %s\n"
              (:status resp "nil")
              (name (:request-method req))
              (:uri req))
      (flush)
      resp)))

(defn wrap-defaults [handler {:keys [session-store
                                     cookie-session-secret
                                     secure
                                     session-max-age
                                     on-error
                                     env]
                              :or {session-max-age (* 60 60 24 90)}
                              :as opts}]
  (let [session-store (if cookie-session-secret
                        (cookie/cookie-store
                          {:key (bu/base64-decode cookie-session-secret)})
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
    (-> handler
        (wrap-env env)
        wrap-flat-keys
        muuntaja/wrap-params
        muuntaja/wrap-format
        (wrap-static (select-keys opts [:root
                                        :path->file
                                        :spa-path
                                        :spa-client-paths
                                        :spa-exclude-paths]))
        (rd/wrap-defaults ring-defaults)
        (wrap-internal-error {:on-error on-error})
        wrap-log-requests)))

(defn use-default-middleware
  [{:keys [biff.middleware/cookie-secret
           biff.middleware/secure-cookies
           biff.middleware/spa-path
           biff/on-error]
    :or {on-error (constantly
                    {:status 500
                     :headers {"Content-Type" "text/plain"}
                     :body "Internal server error."})}
    :as sys}]
  (update sys :biff/handler wrap-defaults
          {:cookie-session-secret cookie-secret
           :secure secure-cookies
           :env sys
           :on-error on-error
           :spa-path spa-path}))
