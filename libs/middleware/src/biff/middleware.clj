(ns biff.middleware
  "Ring middleware."
  (:require [clojure.stacktrace :as st]
            [clojure.string :as str]
            [biff.util :as bu]
            [muuntaja.middleware :as muuntaja]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.middleware.defaults :as rd]
            [ring.middleware.resource :as res]
            [ring.middleware.session.cookie :as cookie]))

(defn wrap-index-files
  "If handler returns nil, try again with each index file appended to the URI."
  [handler {:keys [index-files]
            :or {index-files ["index.html"]}}]
  (fn [req]
    (->> index-files
         (map #(update req :uri str/replace-first #"/?$" (str "/" %)))
         (into [req])
         (some (wrap-content-type handler)))))

(defn wrap-resource
  "Serves static resources with ring.middleware.resource/wrap-resource-request.

  root:              The resource root from which static files should be
                     served.
  index-files:       See wrap-index-files.
  spa-path:          If set, replace 404 responses with this file.
  spa-exclude-paths: Ignore spa-path if the request URI starts with one of
                     these.
  spa-client-paths:  A set of SPA routes. If set, ignore spa-path unless the
                     request URI is one of these. Takes precedence over
                     spa-exclude-paths."
  [handler {:keys [root
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

(defn wrap-flat-keys
  "Lets handler use flattened versions of session, params, headers and cookies.

  Adds :session/* and :params/* keys to the request, and nests :headers/* and
  :cookies/* keys in the response. For example:

  {:params {:foo \"bar\"}}
  => {:params/foo \"bar\"}

  {:headers/content-type \"text/html\"}
  => {:headers {:content-type \"text/html\"}}"
  [handler]
  (fn [{:keys [session params] :as req}]
    (some-> req
            (merge (bu/prepend-keys "session" session)
                   (bu/prepend-keys "params" params))
            handler
            (bu/nest-string-keys [:headers :cookies]))))

(defn wrap-env
  "Merges env into requests."
  [handler env]
  (fn [req]
    (handler (merge env req))))

(defn wrap-internal-error
  "Catches exceptions and calls on-error instead.

  on-error should accept a request map with :status and :ex (exception) keys.
  If the exception's ex-data is an anomaly, get the status from
  biff.util/anom->http-status; otherwise it will be 500.

  Also prints exception info (stack trace for 500s, message + ex-data
  otherwise)."
  [handler {:keys [on-error]}]
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

(defn wrap-log-requests
  "Prints status, request method and response status for each request."
  [handler]
  (fn [req]
    (let [resp (handler req)]
      (printf "%s  %-4s %s\n"
              (:status resp "nil")
              (name (:request-method req))
              (:uri req))
      (flush)
      resp)))

(defn wrap-defaults
  "TODO add docstring"
  [handler {:keys [session-store
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
  "TODO add docstring"
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
