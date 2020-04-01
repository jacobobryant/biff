(ns nimbus.comms
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop]])
  (:require
    [taoensso.sente :as sente]
    [cljs.core.async :as async :refer [<! >! take! put! chan]]
    [clojure.set :as set]))

(defn wrap-api-send [api-send ready-pr]
  (fn [event]
    (let [ch (chan)]
      (.then ready-pr
        (fn []
          (api-send event 5000
            (fn [response]
              (put! ch (if response response ::no-response))))))
      ch)))

(defn wrap-api-recv [api-recv ready-ch]
  (fn [{:keys [id ?data] :as event}]
    (when (and (= id :chsk/state) (-> ?data second :first-open?))
      (put! ready-ch true))
    (api-recv event)))

(defn start-socket []
  (let [ready-ch (chan)
        ready-pr (js/Promise.
                   (fn [done]
                     (take! ready-ch done)))]
    (-> (sente/make-channel-socket! "/nimbus/comms/chsk" {:type :auto})
      (set/rename-keys {:send-fn :api-send})
      (update :api-send wrap-api-send ready-pr)
      (assoc :ready ready-ch))))

(defn start-router [{:keys [ch-recv api-recv ready]}]
  (sente/start-client-chsk-router! ch-recv
    (wrap-api-recv api-recv ready)))

(defn init [opts]
  (doto (merge opts (start-socket)) start-router))
