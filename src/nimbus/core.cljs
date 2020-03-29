(ns nimbus.core
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop]])
  (:require
    [cljs.core.async :as async :refer [<! >! put! chan]]
    [taoensso.sente  :as sente :refer [cb-success?]]
    [nimbus.lib :as lib]
    [clojure.set :as set]
    [rum.derived-atom :refer [derived-atom]]
    [trident.util :as u]))

(defn wrap-api-send [api-send]
  (fn [event]
    (let [ch (chan)]
      (api-send event 5000
        (fn [response]
          (u/pprint [:api-send event response])
          (when response
            (put! ch response))))
      ch)))

(defn wrap-api-recv [api-recv sente-ready sub-channels]
  (fn [{:keys [id ?data] :as event}]
    (when (and (= id :chsk/state) (-> ?data second :first-open?))
      (println "sente ready")
      (put! sente-ready true))
    (u/pprint [:event id event])
    (when (= id :chsk/recv)
      (let [[id ?data] ?data
            sub-channels @sub-channels]
        (if-some [ch (some-> sub-channels
                       (get id)
                       (get (:query ?data)))]
          (put! ch (:changeset ?data))
          (api-recv event ?data))))))

(defn subscribe [{:keys [api-send sub-channels]} provider query]
  (let [ch (chan)]
    (swap! sub-channels assoc-in [provider query] ch)
    (println "api-send" provider query)
    (api-send [provider query])
    ch))

; chsk ch-recv send-fn state
(defn start-nimbus [{:keys [sub-data subscriptions api-recv]}]
  (let [{:keys [api-send sub-channels ch-recv] :as env}
        (-> (sente/make-channel-socket! "/chsk" {:type :auto})
          (set/rename-keys {:send-fn :api-send})
          (update :api-send wrap-api-send)
          (assoc :sub-channels (atom {})))
        sente-ready (chan)]
    (go (<! sente-ready)
      (when (and subscriptions sub-data)
        (u/maintain-subscriptions subscriptions
          (fn [[provider query :as sub]]
            (u/merge-subscription-results!
              {:sub-data-atom sub-data
               :merge-result lib/merge-changeset
               :sub-key sub
               :sub-channel (subscribe env provider query)})))))
    (sente/start-client-chsk-router! ch-recv
      (wrap-api-recv api-recv sente-ready sub-channels))
    env))
