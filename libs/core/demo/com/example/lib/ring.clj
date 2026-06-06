(ns com.example.lib.ring
  (:require [clojure.tools.logging :as log]
            [ring.adapter.jetty :as jetty]
            [com.biffweb.core :as biff.core]))

(defn- not-found [_request]
  {:status  404
   :headers {"content-type" "text/plain"}
   :body    "Not found."})

(defn make-handler [{:keys [com.example/routes]}]
  (fn [{:keys [request-method uri] :as request}]
    (let [handler (get routes [request-method uri] not-found)]
      (handler request))))

(defn- modules->handler [modules]
  (let [routes (->> modules
                    (mapv :com.example/routes)
                    (apply merge))]
    (make-handler {:com.example/routes routes})))

(def module
  {:biff.core/init
   (fn [modules-var]
     (let [modules->handler* (memoize modules->handler)]
       {:com.example/handler (fn [request]
                               ((modules->handler* @modules-var)
                                request))}))})

(defn use-webserver [{:com.example/keys [handler port]
                      :or               {port 8080}
                      :as               ctx}]
  (biff.core/validate ctx {:required [:com.example/handler]})
  (let [handler (fn [request]
                  (handler (merge ctx request)))
        server  (jetty/run-jetty handler
                                 {:host  "localhost"
                                  :port  port
                                  :join? false})]
    (log/info (str "Web server started on http://localhost:" port))
    (update ctx :biff.core/stop conj #(.stop server))))
