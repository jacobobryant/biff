(ns nimbus.pack.components
  (:require
    [trident.util :as u]
    [nimbus.pack.db :as db]
    [nimbus.pack.mutations :as m]
    [rum.core :as rum :refer [defc defcs static reactive react local]]))

(defc main < reactive
  []
  [:div
   [:p "hello"]
   [:button.btn.btn-primary {:on-click #(m/api-send [:nimbus.crux/tx
                                                     [[:crux.tx/put
                                                       {:crux.db/id :foo
                                                        :name "hey"}]]])}
    "Fire ze missiles"]
   [:pre (with-out-str (u/pprint (react db/data)))]])
