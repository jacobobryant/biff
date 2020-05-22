(ns hello.client.app
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require
    [clojure.pprint :refer [pprint]]
    [cljs.core.async :refer [<!]]
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
    (bu/init-sub {:handler m/api
                  :sub-data db/sub-data
                  :subscriptions db/subscriptions}))
  (mount))

(comment
  (-> (m/api-send [:hello/echo {:foo "bar"}])
    <!
    pprint
    go))
