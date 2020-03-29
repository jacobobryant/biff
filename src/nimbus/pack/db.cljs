(ns nimbus.pack.db
  (:require
    [nimbus.lib :as lib :refer [defdb]]))

(defdb init
  :db {}
  :sub-data {}
  :data (apply merge-with merge (vals sub-data))
  :subscriptions #{[:nimbus.pack/subscribe nil]})
