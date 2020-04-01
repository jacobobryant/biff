(ns nimbus.pack
  (:require
    [nimbus.pack.components :as c]
    [nimbus.pack.db :as db]
    [nimbus.pack.mutations :as m]
    [nimbus.core :as nimbus]
    [trident.util :as u]
    [nimbus.lib :as lib]
    [rum.core :as rum]))

(defonce env
  (let [{:keys [sub-data subscriptions] :as db} (db/init)
        nimbus (nimbus/start-nimbus
                 {:sub-data sub-data
                  :subscriptions subscriptions
                  :api-recv m/api})]
    (lib/mcat
      {:nimbus nimbus
       :db db
       :m m/env})))

(defn ^:export mount []
  (rum/mount (c/main env) (js/document.querySelector "#app")))

(defn ^:export init []
  (mount))
