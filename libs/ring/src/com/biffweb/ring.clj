(ns com.biffweb.ring
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [com.biffweb.core :as biff.core]
            [com.biffweb.fx :as fx]
            [com.biffweb.ring.impl :as impl]
            [muuntaja.middleware :as muuntaja]
            [reitit.ring :as reitit-ring]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.anti-forgery :as anti-forgery]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.middleware.defaults :as rd]
            [ring.middleware.resource :as res]
            [ring.middleware.session :as session]
            [ring.middleware.session.cookie :as cookie]
            [ring.middleware.session.memory :as memory]
            [ring.middleware.ssl :as ssl]
            [ring.util.codec :as codec]
            [taoensso.nippy :as nippy])
  (:import (java.nio ByteBuffer)
           (java.util Base64 UUID)))

(biff.core/register
 {:biff.ring/api-middleware         'sequential?
  :biff.ring/api-routes             'sequential?
  :biff.ring/base-middleware        'sequential?
  :biff.ring/base-url               'string?
  :biff.ring/cookie-secret          [:or 'string? 'ifn?]
  :biff.ring/fallback-session-store 'some?
  :biff.ring/handler                'ifn?
  :biff.ring/host                   'string?
  :biff.ring/hsts                   'boolean?
  :biff.ring/index-files            [:sequential 'string?]
  :biff.ring/on-error               'ifn?
  :biff.ring/port                   'integer?
  :biff.ring/root                   'string?
  :biff.ring/routes                 'sequential?
  :biff.ring/secure                 'boolean?
  :biff.ring/session-max-age        'integer?
  :biff.ring/session-same-site      'keyword?
  :biff.ring/site-middleware        'sequential?
  :biff.ring/ssl-redirect           'boolean?})

(def ^:dynamic *testing* false)

(defn- url-safe-base64-encode [^bytes bytes]
  (.encodeToString (.withoutPadding (Base64/getUrlEncoder)) bytes))

(defn- url-safe-base64-decode [^String s]
  (.decode (Base64/getUrlDecoder) s))

(defn- encode-path-param [x]
  (if (uuid? x)
    (let [buf (ByteBuffer/allocate 16)]
      (.putLong buf (.getMostSignificantBits ^UUID x))
      (.putLong buf (.getLeastSignificantBits ^UUID x))
      (url-safe-base64-encode (.array buf)))
    (str x)))

(defn- maybe-decode-uuid [x]
  (if-not (string? x)
    x
    (try
      (let [bytes (url-safe-base64-decode x)]
        (if (= 16 (alength bytes))
          (let [buf (ByteBuffer/wrap bytes)]
            (UUID. (.getLong buf) (.getLong buf)))
          x))
      (catch IllegalArgumentException _
        x))))

(defn- path-params [path]
  (re-seq #":[^/]+" path))

(defn ^:no-doc route-path [path-or-route]
  (cond
    (string? path-or-route)
    path-or-route

    (and (vector? path-or-route)
         (string? (first path-or-route)))
    (first path-or-route)

    :else
    (throw (ex-info "Path helpers expect a string or a reitit-style route vector."
                    {:path-or-route path-or-route}))))

(defn ^:no-doc render-path [path args]
  (loop [segments (str/split path #"/" -1)
         args     (map encode-path-param args)
         rendered []]
    (if-some [segment (first segments)]
      (if (str/starts-with? segment ":")
        (recur (rest segments) (rest args) (conj rendered (first args)))
        (recur (rest segments) args (conj rendered segment)))
      (str/join "/" rendered))))

(defn ^:no-doc path-with-query [path query-params]
  (when-not (map? query-params)
    (throw (ex-info "defpath query params must be a map."
                    {:query-params query-params})))
  (if *testing*
    [path query-params]
    (str path
         "?"
         (codec/form-encode
          {:npy (url-safe-base64-encode (nippy/freeze query-params))}))))

(defn path
  "Returns a path string with optional path params and nippy-encoded query params.

  The first argument can be either a path string or a reitit-style route vector."
  [path-or-route & args]
  (let [path             (route-path path-or-route)
        path-param-count (count (path-params path))
        arg-count        (count args)]
    (when-not (<= path-param-count arg-count (inc path-param-count))
      (throw (ex-info "Wrong number of args for path."
                      {:path             path
                       :path-param-count path-param-count
                       :arg-count        arg-count})))
    (let [[path-args query-params] (split-at path-param-count args)
          rendered                 (render-path path path-args)]
      (if (= arg-count path-param-count)
        rendered
        (path-with-query rendered (first query-params))))))

(defmacro defpath
  "Defines a function that returns a path string with optional path and nippy-encoded query params."
  [sym path-or-route]
  `(def ~sym (partial path (route-path ~path-or-route))))

(defmacro defroute
  "Defines a route var with auto-generated or explicit URI."
  [sym & args]
  (let [uri            (impl/autogen-endpoint *ns* sym)
        route-name     (keyword (str *ns*) (str sym))
        args-sym       (gensym "args")
        uri-sym        (gensym "uri")
        rest-args-sym  (gensym "rest-args")
        initial-fx-sym (gensym "initial-fx")
        params-sym     (gensym "params")]
    `(def ~sym
       (let [~args-sym             [~@args]
             [~uri-sym & ~rest-args-sym]
             (if (string? (first ~args-sym))
               ~args-sym
               (into [nil] ~args-sym))
             [~initial-fx-sym & kvs#]
             (if (and (vector? (first ~rest-args-sym))
                      (not (string? (ffirst ~rest-args-sym))))
               ~rest-args-sym
               (into [nil] ~rest-args-sym))
             [& {:as ~params-sym}] kvs#]
         (impl/route* (or ~uri-sym ~uri)
                      ~route-name
                      fx/machine
                      (cond-> (update-vals ~params-sym impl/wrap-hiccup)
                        ~initial-fx-sym
                        (impl/wrap-methods impl/wrap-result)

                        true
                        (merge {:start (fn [{:keys [~'request-method]}]
                                         (cond-> {:biff.fx/next ~'request-method}
                                           ~initial-fx-sym
                                           (assoc :biff.fx/result ~initial-fx-sym)))})))))))

(def ^:private http-status->msg
  {400 "Bad Request"
   401 "Unauthorized"
   403 "Forbidden"
   404 "Not Found"
   405 "Method Not Allowed"
   406 "Not Acceptable"
   500 "Internal Server Error"})

(defn- default-on-error [{:keys [status]}]
  {:status  status
   :headers {"content-type" "text/html; charset=utf-8"}
   :body    (str "<h1>" (http-status->msg status "Error") "</h1>")})

(defn- on-error-handler [ctx]
  (or (:biff.ring/on-error ctx)
      default-on-error))

(defn- websocket-request? [{:keys [headers]}]
  (and (str/includes? (str/lower-case (get headers "upgrade" "")) "websocket")
       (str/includes? (str/lower-case (get headers "connection" "")) "upgrade")))

(defn wrap-anti-forgery-websockets [handler]
  (fn [{:keys [headers] :as ctx}]
    (let [base-url (:biff.ring/base-url ctx)]
      (cond
        (not (websocket-request? ctx))
        (handler ctx)

        (nil? base-url)
        (do
          (log/warn "Rejecting websocket request because :biff.ring/base-url is not set.")
          {:status  403
           :headers {"content-type" "text/plain; charset=utf-8"}
           :body    "Forbidden"})

        (not= base-url (get headers "origin"))
        (do
          (log/warn "Rejecting websocket request due to origin mismatch." {:origin (get headers "origin")})
          {:status  403
           :headers {"content-type" "text/plain; charset=utf-8"}
           :body    "Forbidden"})

        :else
        (handler ctx)))))

(defn wrap-path-param-uuids [handler]
  (fn [{:as ctx}]
    (handler
     (cond-> ctx
       (:path-params ctx)
       (update :path-params #(update-vals % maybe-decode-uuid))))))

(defn wrap-nippy-params [handler]
  (fn [{:as ctx}]
    (let [npy (or (get-in ctx [:params :npy])
                  (get-in ctx [:params "npy"]))]
      (handler
       (if-not npy
         ctx
         (try
           (let [value (nippy/thaw (url-safe-base64-decode npy))]
             (if (map? value)
               (update ctx :params merge value)
               ctx))
           (catch Throwable _
             ctx)))))))

(defn wrap-resource [handler]
  (fn [ctx]
    (let [root        (:biff.ring/root ctx "public")
          index-files (:biff.ring/index-files ctx ["index.html"])]
      (or (->> index-files
               (map #(update ctx :uri str/replace-first #"/?$" (str "/" %)))
               (into [ctx])
               (some (wrap-content-type #(res/resource-request % root))))
          (handler ctx)))))

(defn wrap-internal-error [handler]
  (fn [ctx]
    (try
      (handler ctx)
      (catch Throwable t
        (log/error t "Exception while handling request")
        ((on-error-handler ctx) (assoc ctx :status 500 :ex t))))))

(defn wrap-log-requests [handler]
  (fn [ctx]
    (let [start    (System/nanoTime)
          resp     (handler ctx)
          stop     (System/nanoTime)
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
  (fn [ctx]
    (let [secure? (:biff.ring/secure ctx true)]
      (handler (if (and secure? (= :http (:scheme ctx)))
                 (assoc ctx :scheme :https)
                 ctx)))))

(defn- session-store [{:biff.ring/keys [cookie-secret
                                        fallback-session-store]}]
  (if-some [cookie-secret (some-> cookie-secret (.invoke))]
    (let [decoder (java.util.Base64/getDecoder)]
      (cookie/cookie-store
       {:key (.decode ^java.util.Base64$Decoder decoder ^String cookie-secret)}))
    (do
      (log/warn "No cookie secret configured; using in-memory Ring sessions.")
      (or fallback-session-store (memory/memory-store)))))

(defn wrap-session [handler]
  (fn [ctx]
    (let [session-max-age   (:biff.ring/session-max-age ctx (* 60 60 24 60))
          session-same-site (:biff.ring/session-same-site ctx :lax)]
      ((session/wrap-session
        handler
        {:cookie-attrs {:max-age   session-max-age
                        :same-site session-same-site
                        :http-only true}
         :store        (session-store ctx)})
       ctx))))

(defn wrap-ssl [handler]
  (fn [ctx]
    (let [secure?       (:biff.ring/secure ctx true)
          hsts?         (:biff.ring/hsts ctx true)
          ssl-redirect? (:biff.ring/ssl-redirect ctx false)
          handler       (if secure?
                          (cond-> handler
                            hsts? ssl/wrap-hsts
                            ssl-redirect? ssl/wrap-ssl-redirect)
                          handler)]
      (handler ctx))))

(defn wrap-site-defaults [handler]
  (-> handler
      wrap-anti-forgery-websockets
      anti-forgery/wrap-anti-forgery
      wrap-session
      wrap-path-param-uuids
      wrap-nippy-params
      muuntaja/wrap-params
      muuntaja/wrap-format
      (rd/wrap-defaults
       (-> rd/site-defaults
           (assoc-in [:security :anti-forgery] false)
           (assoc-in [:responses :absolute-redirects] false)
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

(defn- make-default-handler [status]
  (fn [ctx]
    ((on-error-handler ctx) (assoc ctx :status status))))

(defn- module-routes [modules key]
  (->> modules
       (keep #(get % key))
       vec))

(defn- module-middleware [modules key]
  (->> modules
       (mapcat #(get % key []))
       vec))

(defn- route-group [middleware routes]
  (when (seq routes)
    ["" {:middleware middleware}
     routes]))

(defn- routes [modules]
  (let [base-middleware (module-middleware modules :biff.ring/base-middleware)
        site-middleware (module-middleware modules :biff.ring/site-middleware)
        api-middleware  (module-middleware modules :biff.ring/api-middleware)
        site-routes     (module-routes modules :biff.ring/routes)
        api-routes      (module-routes modules :biff.ring/api-routes)
        children        (cond-> []
                          (seq site-routes)
                          (conj (route-group (into [wrap-site-defaults] site-middleware) site-routes))

                          (seq api-routes)
                          (conj (route-group (into [wrap-api-defaults] api-middleware) api-routes)))]
    [["" {:middleware base-middleware}
      children]]))

(def ^:private handler-for-modules
  (memoize
   (fn [modules]
     (wrap-base-defaults
      (reitit-ring/ring-handler
       (reitit-ring/router (routes modules))
       (reitit-ring/create-default-handler
        {:not-found          (make-default-handler 404)
         :method-not-allowed (make-default-handler 405)
         :not-acceptable     (make-default-handler 406)}))))))

(defn use-jetty [{:as ctx}]
  (let [host    (:biff.ring/host ctx "localhost")
        port    (:biff.ring/port ctx 8080)
        handler (:biff.ring/handler ctx)]
    (when-not handler
      (throw (ex-info "Missing Ring handler" {:required :biff.ring/handler})))
    (let [server (jetty/run-jetty
                  (fn [req]
                    (try
                      (handler (merge ctx req))
                      (catch Throwable t
                        (log/error t "Unhandled exception in Jetty handler")
                        {:status  500
                         :headers {"content-type" "text/plain; charset=utf-8"}
                         :body    "Internal Server Error"})))
                  {:host  host
                   :port  port
                   :join? false})]
      (log/info "Jetty running on" (str "http://" host ":" port))
      (update ctx :biff.core/stop conj #(.stop server)))))

(defn module []
  {:biff.core/init
   (fn [modules-var]
     {:biff.ring/fallback-session-store (memory/memory-store)
      :biff.ring/handler
      (fn [request]
        ((handler-for-modules @modules-var) request))})})
