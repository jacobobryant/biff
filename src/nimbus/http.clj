(ns ^:nimbus nimbus.http
  (:require
    [byte-streams :as bs]
    [byte-transforms :as bt]
    [clojure.java.io :as io]
    [crypto.random :as random]
    [immutant.web :as imm]
    [mount.core :as mount :refer [defstate]]
    [nimbus.core :as core]
    [nimbus.util :as util]
    [reitit.ring :as reitit]
    [ring.middleware.defaults :as rd]
    [ring.middleware.session.cookie :as cookie]
    [trident.util :as u]))

(def secret-key-path "data/nimbus.http/secret-key")

(defn secret-key []
  (or
    (u/catchall (bt/decode (slurp secret-key-path) :base64))
    (let [k (random/bytes 16)
          k-str (bs/to-string (bt/encode k :base64))]
      (io/make-parents secret-key-path)
      (spit secret-key-path k-str)
      k)))

(defn redirect-home [_]
  {:status 302
   :headers {"Location" (get-in (util/deps) [:nimbus/config ::home]
                          (some ::home (vals core/config)))}
   :body ""})

(def ring-settings
  (-> (if core/debug
        rd/site-defaults
        rd/secure-site-defaults)
    (assoc-in [:session :store] (cookie/cookie-store {:key (secret-key)}))
    (assoc-in [:security :ssl-redirect] false)))

(defn app [routes]
  (reitit/ring-handler
    (reitit/router
      (into [["/" {:get redirect-home
                   :name ::home}]]
        routes)
      {:data {:middleware [[rd/wrap-defaults ring-settings]]}})
    (reitit/routes
      (reitit/create-resource-handler {:path "/"})
      (reitit/create-default-handler))))

(defstate server
  :start (imm/run
           (app (->> core/config
                  vals
                  (map ::route)
                  (filterv some?)))
           {:port 8080})
  :stop (imm/stop server))
