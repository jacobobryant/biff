(ns ^:biff biff.http
  (:require
    [biff.util.http :as bu-http]
    [immutant.web :as imm]
    [mount.core :refer [defstate]]
    [biff.core :as core]
    [reitit.ring :as reitit]))

(defmulti handler ::ns)

(defn make-default-handler [{:keys [main plugins]}]
  (let [home (some :biff.http/home (conj (vals plugins) main))
        routes (->> plugins
                 vals
                 (map :biff.http/route)
                 (filterv some?)
                 (into [["/" {:get (constantly {:status 302
                                                :headers/Location home})
                              :name ::home}]]))]
    (bu-http/make-handler
      {:debug core/debug
       :routes routes
       :cookie-path "data/biff.http/cookie-key"
       :default-routes [(reitit/create-resource-handler {})]})))

(defstate server
  :start (let [{::keys [debug-ns host->ns]} (:main core/config)
               get-ns (if (and core/debug debug-ns)
                        (constantly debug-ns)
                        #(-> % :server-name host->ns))
               default-handler (make-default-handler core/config)]
           (defmethod handler :default
             [req]
             (default-handler req))
           (imm/run
             (comp handler #(assoc % ::ns (get-ns %)))
             {:port 8080}))
  :stop (imm/stop server))
