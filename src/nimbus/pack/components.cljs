(ns nimbus.pack.components
  (:require
    [trident.util :as u]
    [rum.core :as rum :refer [defc defcs static reactive react local]]))

(defc main < reactive
  [{:m/keys [get-deps]
    :nimbus/keys [api-send]
    :db/keys [data] :as env}]
  [:div
   [:p "hello"]
   [:button.btn.btn-primary {:on-click #(api-send [:nimbus.crux/tx
                                                   [[:crux.tx/put
                                                     {:crux.db/id :foo
                                                      :name "hey"}]]])}
    "Fire ze missiles"]
   [:pre (with-out-str (u/pprint (react data)))]])
