(ns biff.util
  (:require
    [goog.net.Cookies]
    [cemerick.url :as url]
    [taoensso.sente :as sente]
    [cljs.core.async :as async :refer [take! put! chan]]
    [trident.util :as u]
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

(defn wrap-handler [handler ready-ch]
  (fn [{:keys [id ?data] :as event}]
    (when (and (= id :chsk/state) (-> ?data second :first-open?))
      (put! ready-ch true))
    (handler event)))

(defn start-socket [{:keys [url]}]
  (let [ready-ch (chan)
        ready-pr (js/Promise.
                   (fn [done]
                     (take! ready-ch done)))
        csrf-token (js/decodeURIComponent (.get (new goog.net.Cookies js/document) "csrf"))]
    (-> (sente/make-channel-socket-client! url csrf-token {:type :auto})
      (set/rename-keys {:send-fn :api-send})
      (update :api-send wrap-api-send ready-pr)
      (assoc :ready ready-ch))))

(defn start-router [{:keys [ch-recv handler ready]}]
  (sente/start-client-chsk-router! ch-recv
    (wrap-handler handler ready)))

(defn init-sente [{:keys [handler url] :as opts}]
  (doto (merge opts (start-socket opts)) start-router))

(defn wrap-sub [handler sub-channels]
  (fn [{:keys [id ?data] :as event}]
    (when (= id :chsk/recv)
      (let [[id ?data] ?data
            sub-channels @sub-channels]
        (if-some [ch (some-> sub-channels
                       (get id)
                       (get (:query ?data)))]
          (put! ch (:changeset ?data))
          (handler event ?data))))))

(defn merge-changeset [db changeset]
  (reduce (fn [db [[table id] ent]]
            (if ent
              (assoc-in db [table id] ent)
              (update db table dissoc id)))
    db
    changeset))

(defn init-sub [{:keys [sub-data subscriptions handler url]}]
  (let [sub-channels (atom {})
        handler (wrap-sub handler sub-channels)
        {:keys [api-send] :as env} (init-sente {:handler handler
                                                :url url})]
    (u/maintain-subscriptions subscriptions
      (fn [[provider query]]
        (let [ch (chan)]
          (swap! sub-channels assoc-in [provider query] ch)
          (api-send [provider query])
          (u/merge-subscription-results!
            {:sub-data-atom sub-data
             :merge-result merge-changeset
             :sub-key [provider query]
             :sub-channel ch}))))
    env))
