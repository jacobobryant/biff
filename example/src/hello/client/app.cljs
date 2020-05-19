(ns hello.client.app
  (:require
    [biff.util :as bu]
    [hello.client.app.components :as c]
    [hello.client.app.db :as db]
    [hello.client.app.mutations :as m]
    [hello.client.app.system :as s]
    [rum.core :as rum]))

(defn ^:export mount []
  (rum/mount (c/main) (js/document.querySelector "#app")))

(defn ^:export init []
  (reset! s/system
    (bu/init-sub {:url "/api/chsk"
                  :sub-data db/sub-data
                  :subscriptions db/subscriptions
                  :handler m/api}))
  (mount))
