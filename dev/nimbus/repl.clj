(ns nimbus.repl
  (:require
    [nrepl.server :as nrepl]
    [nimbus.core :as core]))

(defn -main []
  (nrepl/start-server :port 7888)
  (.bindRoot #'core/debug true)
  (core/-main)
  (println :started))
