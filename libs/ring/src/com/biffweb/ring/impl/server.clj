(ns com.biffweb.ring.impl.server
  (:require [clojure.tools.logging :as log]
            [com.biffweb.core :as biff.core]
            [com.biffweb.ring.impl.middleware :as middleware]
            [reitit.ring :as reitit-ring]
            [ring.middleware.session.memory :as memory]))

(defn- default-error-handler [status]
  #(middleware/on-error (assoc % :status status)))

(defn make-handler [{:keys [site-routes
                            site-middleware
                            api-routes
                            api-middleware
                            base-middleware]}]
  (middleware/wrap-base-defaults
   (reitit-ring/ring-handler
    (reitit-ring/router
     [["" {:middleware base-middleware}
       ["" {:middleware (into [middleware/wrap-site-defaults]
                              site-middleware)}
        site-routes]
       ["" {:middleware (into [middleware/wrap-api-defaults]
                              api-middleware)}
        api-routes]]])
    (reitit-ring/create-default-handler
     {:not-found          (default-error-handler 404)
      :method-not-allowed (default-error-handler 405)
      :not-acceptable     (default-error-handler 406)}))))

(def modules->handler
  (memoize
   (fn [modules]
     (let [routes     #(into [] (keep %) modules)
           middleware #(into [] (mapcat %) modules)]
       (make-handler
        {:base-middleware (middleware :biff.ring/base-middleware)
         :site-middleware (middleware :biff.ring/site-middleware)
         :api-middleware  (middleware :biff.ring/api-middleware)
         :site-routes     (routes :biff.ring/routes)
         :api-routes      (routes :biff.ring/api-routes)})))))

(defn- start [{:biff.ring/keys [host port handler]
               :or             {host "localhost" port 8080}
               :as             ctx}]
  (biff.core/validate ctx {:required [:biff.ring/handler]})
  (let [server ((requiring-resolve 'ring.adapter.jetty/run-jetty)
                (fn [request]
                  (try
                    (handler (merge ctx request))
                    (catch Throwable t
                      (log/error t "Unhandled error in Jetty handler")
                      {:status  500
                       :headers {"content-type" "text/plain; charset=utf-8"}
                       :body    "Internal Server Error"})))
                {:host host :port port :join? false})]
    (log/info "Jetty running on" (str "http://" host ":" port))
    (assoc ctx ::server server)))

(defn module []
  {:biff.core/id    :biff.ring/jetty
   :biff.core/start start
   :biff.core/stop  (fn [{::keys [server]}] (.stop server))

   :biff.core/init
   (fn [modules-var]
     {:biff.ring/fallback-session-store (memory/memory-store)

      :biff.ring/handler
      (fn [request]
        ((modules->handler @modules-var) request))})})
