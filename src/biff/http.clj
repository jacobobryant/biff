(ns ^:biff biff.http
  (:require
    [biff.http.handler :as handler]
    [clojure.string :as str]
    [immutant.web :as imm]
    [mount.core :as mount :refer [defstate]]
    [biff.core :as core]
    [biff.util :as util]
    [reitit.ring :as reitit]
    [trident.util :as u]))

(defmulti handler ::ns)
(defmethod handler :default
  [req]
  (handler/default-handler req))

(defstate server
  :start (let [{::keys [debug-server-name host->ns]} (:main core/config)
               get-server-name (if (and core/debug debug-server-name)
                                 (constantly debug-server-name)
                                 :server-name)]
           (imm/run #(->> %
                       get-server-name
                       (get host->ns)
                       (assoc % ::ns)
                       handler)
             {:port 8080}))
  :stop (imm/stop server))
