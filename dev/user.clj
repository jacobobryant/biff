(ns user
  (:require
    [nrepl.server :as nrepl]
    [biff.core :as core]))

(defn -main []
  (nrepl/start-server :port 7888)
  (.bindRoot #'core/debug true)
  (core/-main))
