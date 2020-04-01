(ns nimbus.pack.db
  (:require
    [trident.util :refer [defderivations defcursors]]
    [rum.core]
    [nimbus.lib :as lib :refer [defdb]]))

(defonce db (atom {}))

(defcursors db
  sub-data [:sub-data]
  env [:env])

(defderivations [db sub-data] nimbus.pack.db
  data (apply merge-with merge (vals sub-data))
  subscriptions #{[:nimbus.pack/subscribe nil]})
