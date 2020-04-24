(ns ^:biff biff.http
  (:require
    [biff.util.http :as bu-http]
    [immutant.web :as imm]
    [mount.core :refer [defstate]]
    [biff.core :as core]))

(defmulti handler ::ns)

(defstate server
  :start (let [{::keys [port debug-ns host->ns]
                :or {port bu-http/default-port}} core/config
               get-ns (if (and core/debug debug-ns)
                        (constantly debug-ns)
                        #(some->> % :server-name (get host->ns)))]
           (imm/run
             (comp handler #(assoc % ::ns (get-ns %)))
             {:port port}))
  :stop (imm/stop server))
