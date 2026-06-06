(ns com.biffweb.demo.lib.middleware
  (:require [com.biffweb.demo.routes :as routes]))

(defn wrap-signed-in [handler]
  (fn [{:keys [session] :as ctx}]
    (if (some? (:uid session))
      (handler ctx)
      {:status  303
       :headers {"location" (routes/signin)}})))
