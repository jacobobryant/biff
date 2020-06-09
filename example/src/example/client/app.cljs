(ns example.client.app
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require
    [clojure.pprint :refer [pprint]]
    [cljs.core.async :refer [<!]]
    [biff.client :as bc]
    [example.client.app.components :as c]
    [example.client.app.db :as db]
    [example.client.app.mutations :as m]
    [example.client.app.system :as s]
    [rum.core :as rum]))

(defn ^:export mount []
  (rum/mount (c/main) (js/document.querySelector "#app")))

(defn ^:export init []
  (reset! s/system
    (bc/init-sub {:handler m/api
                  :sub-data db/sub-data
                  :subscriptions db/subscriptions}))
  (mount))

(comment
  (-> (m/api-send [:example/echo {:foo "bar"}])
    <!
    pprint
    go))
