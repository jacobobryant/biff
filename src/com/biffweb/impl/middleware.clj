(ns com.biffweb.impl.middleware
  (:require [clojure.stacktrace :as st]
            [clojure.string :as str]
            [com.biffweb.impl.util :as util]
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
  [handler {:biff.middleware/keys [root
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

(defn wrap-ring-defaults
  [handler {:biff.middleware/keys [session-store
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

(defn wrap-internal-error
  [handler {:biff.middleware/keys [on-error]
            :or {on-error util/default-on-error}}]
  (fn [req]
    (try
      (handler req)
      (catch Throwable t
        (st/print-stack-trace t)
        (flush)
        (on-error (assoc req :status 500 :ex t))))))

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
  [handler opts]
  (-> handler
      muuntaja/wrap-params
      muuntaja/wrap-format
      (wrap-resource opts)
      (wrap-ring-defaults opts)
      (wrap-internal-error opts)
      wrap-log-requests))
