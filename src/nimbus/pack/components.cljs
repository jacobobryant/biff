(ns nimbus.pack.components
  (:require
    [trident.util :as u]
    [rum.core :as rum :refer [defc defcs static reactive react local]]))

(defc main < reactive
  [{:m/keys [get-deps]
    :db/keys [data] :as env}]
  [:div
   [:p "hello"]
   [:pre (with-out-str (u/pprint (react data)))]])
