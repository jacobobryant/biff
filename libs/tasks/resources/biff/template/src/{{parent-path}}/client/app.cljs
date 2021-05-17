(ns {{parent-ns}}.client.app
  (:require [biff.client :as bc]
            [goog.net.Cookies]
            [reitit.frontend :as rf]
            [reitit.frontend.easy :as rfe]
            [rum.core :as rum]
            [{{parent-ns}}.client.app.components :as c]
            [{{parent-ns}}.client.app.db :as db]
            [{{parent-ns}}.client.app.handlers :as h]
            [{{parent-ns}}.client.app.mutations :as m]
            [{{parent-ns}}.client.app.routes :as r]
            [{{parent-ns}}.client.app.system :as s]))

(defn ^:export mount []
  (rum/mount (c/main) (js/document.querySelector "#app")))

(defn ^:export init []
  (reset! s/system
    (bc/init-sub {:url "/api/chsk"
                  :handler #(h/api % (second (:?data %)))
                  :sub-results db/sub-results
                  :subscriptions db/subscriptions
                  :csrf-token (js/decodeURIComponent
                                (.get (new goog.net.Cookies js/document) "csrf"))}))
  (rfe/start!
    (rf/router r/client-routes)
    #(reset! db/route %)
    {:use-fragment false})
  (mount))
