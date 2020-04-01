(ns nimbus.sub
  (:require
    [trident.util :as u]
    [nimbus.comms :as comms]
    [cljs.core.async :refer [put! chan]]
    [nimbus.lib :as lib]))

(defn wrap-api-recv [api-recv sub-channels]
  (fn [{:keys [id ?data] :as event}]
    (when (= id :chsk/recv)
      (let [[id ?data] ?data
            sub-channels @sub-channels]
        (if-some [ch (some-> sub-channels
                       (get id)
                       (get (:query ?data)))]
          (put! ch (:changeset ?data))
          (api-recv event ?data))))))

(defn init [{:keys [sub-data subscriptions api-recv]}]
  (let [sub-channels (atom {})
        api-recv (wrap-api-recv api-recv sub-channels)
        {:keys [api-send] :as env} (comms/init {:api-recv api-recv})]
    (u/maintain-subscriptions subscriptions
      (fn [[provider query]]
        (let [ch (chan)]
          (swap! sub-channels assoc-in [provider query] ch)
          (api-send [provider query])
          (u/merge-subscription-results!
            {:sub-data-atom sub-data
             :merge-result lib/merge-changeset
             :sub-key [provider query]
             :sub-channel ch}))))
    env))
