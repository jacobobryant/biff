(ns nimbus.pack
  (:require
    [nimbus.pack.components :as c]
    [nimbus.pack.db :as db]
    [nimbus.pack.mutations :as m]
    [nimbus.sub :as sub]
    [rum.core :as rum]))

(defn ^:export mount []
  (rum/mount (c/main) (js/document.querySelector "#app")))

(defn ^:export init []
  (reset! db/env
    (sub/init {:sub-data db/sub-data
               :subscriptions db/subscriptions
               :api-recv m/api}))
  (mount))
