(ns biff.middleware
  (:require [clojure.stacktrace :as st]
            [clojure.string :as str]
            [biff.util :as bu]
            [muuntaja.middleware :as muuntaja]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.middleware.defaults :as rd]
            [ring.middleware.resource :as res]
            [ring.middleware.session.cookie :as cookie]))

(defn wrap-index-files [handler {:keys [index-files]}]
  (fn [req]
    (->> index-files
         (map #(update req :uri str/replace-first #"/?$" (str "/" %)))
         (into [req])
         (some (wrap-content-type handler)))))

(defn wrap-resource [handler {:keys [root
                                     index-files
                                     spa-path
                                     spa-exclude-paths
                                     spa-client-paths]
                              :or {root "public"
                                   index-files ["index.html"]
                                   spa-exclude-paths ["/js/"
                                                      "/css/"
                                                      "/cljs/"
                                                      "/img/"
                                                      "/assets/"
                                                      "/favicon.ico"]}}]
  (fn [req]
    (let [resource-handler (wrap-index-files
                             #(res/resource-request % root)
                             {:index-files index-files})
          static-resp (resource-handler req)
          handler-resp (delay (handler req))
          spa-resp (delay
                     (when (and spa-path
                                (if spa-client-paths
                                  (contains? spa-client-paths (:uri req))
                                  (not (some #(str/starts-with? (:uri req) %)
                                             spa-exclude-paths))))
                       (resource-handler (assoc req :uri spa-path))))]
      (cond
        static-resp static-resp
        (and (or (not @handler-resp)
                 (= 404 (:status @handler-resp)))
             @spa-resp) @spa-resp
        :else @handler-resp))))

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
                              :or {session-max-age (* 60 60 24 30)
                                   secure true}
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
        (wrap-resource (select-keys opts [:root
                                          :index-files
                                          :spa-path
                                          :spa-client-paths
                                          :spa-exclude-paths]))
        (rd/wrap-defaults ring-defaults)
        (wrap-internal-error {:on-error on-error})
        wrap-log-requests)))

(defn use-default-middleware
  [{:keys [biff.middleware/cookie-secret
           biff.middleware/secure
           biff.middleware/spa-path
           biff/on-error]
    :or {on-error (constantly
                    {:status 500
                     :headers {"Content-Type" "text/plain"}
                     :body "Internal server error."})
         secure true}
    :as sys}]
  (update sys :biff/handler wrap-defaults
          {:cookie-session-secret cookie-secret
           :secure secure
           :env sys
           :on-error on-error
           :spa-path spa-path}))
