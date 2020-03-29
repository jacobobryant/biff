(ns nimbus.pack
  (:require
    [nimbus.pack.components :as c]
    [nimbus.pack.db :as db]
    [nimbus.pack.mutations :as m]
    [nimbus.core :as nimbus]
    [trident.util :as u]
    [rum.core :as rum]))

(def env
  (let [{:keys [sub-data subscriptions] :as db} (db/init)
        nimbus (nimbus/start-nimbus
                 {:sub-data sub-data
                  :subscriptions subscriptions
                  :api-recv m/api})]
    (->> {"nimbus" nimbus
          "db" db
          "m" m/env}
      (map #(apply u/prepend-keys %))
      (apply merge))))

(defn ^:export mount []
  (rum/mount (c/main env) (js/document.querySelector "#app")))

(defn ^:export init []
  (mount))
