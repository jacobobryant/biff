(ns nimbus.repl
  (:require
    [nrepl.server :as nrepl]
    [nimbus.core :as nimbus]
    [mount.core :as mount]))

(defn -main []
  (nrepl/start-server :port 7888)
  (nimbus/-main)
  (println :started))
