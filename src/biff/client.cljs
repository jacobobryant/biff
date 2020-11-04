(ns biff.client
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop]])
  (:require
    [biff.util :as bu]
    [cljs.core.async :as async :refer [close! <! >! take! put! chan promise-chan mult tap]]
    [clojure.set :as set]
    [clojure.spec.alpha :as s]
    [goog.net.Cookies]
    [lambdaisland.uri :as uri]
    [sablono.util]
    [taoensso.sente :as sente]))

(defn wrap-api-send [api-send ready-pr]
  (fn [event]
    (let [ch (chan)]
      (.then ready-pr
        (fn []
          (api-send event 5000
            (fn [response]
              (when (bu/anomaly? response)
                (bu/pprint response))
              (put! ch (if response response ::no-response))))))
      ch)))

(defn wrap-handler [handler {:keys [subscriptions api-send ready]}]
  (fn [{:keys [id ?data] :as event}]
    (when (= id :chsk/state)
      (let [[old-state new-state] ?data]
        (when (:first-open? new-state)
          (put! ready true))
        (when (= (mapv (juxt :first-open? :open?) [old-state new-state])
                [[false false] [false true]])
          (doseq [[provider query] @subscriptions]
            (api-send [provider {:action :resubscribe
                                 :query query}])))))
    (handler event)))

(defn csrf []
  (js/decodeURIComponent (.get (new goog.net.Cookies js/document) "csrf")))

(defn start-socket [{:keys [url]}]
  (let [; todo use promise-chan
        ready-ch (chan)
        ready-pr (js/Promise.
                   (fn [done]
                     (take! ready-ch done)))]
    (-> (sente/make-channel-socket-client! url (csrf) {:type :auto})
      (set/rename-keys {:send-fn :api-send})
      (update :api-send wrap-api-send ready-pr)
      (assoc :ready ready-ch))))

(defn start-router [{:keys [ch-recv handler ready] :as opts}]
  (sente/start-client-chsk-router! ch-recv
    (wrap-handler handler opts)))

(defn init-sente [opts]
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

(defn maintain-subscriptions
  [sub-atom sub-fn]
  (let [sub->unsub-fn (atom {})
        c (chan)
        watch (fn [_ _ old-subs new-subs]
                (put! c [old-subs new-subs]))]
    (go-loop []
      (let [[old-subs new-subs] (<! c)
            tmp old-subs
            old-subs (set/difference old-subs new-subs)
            new-subs (vec (set/difference new-subs tmp))
            new-unsub-fns (when (not-empty new-subs)
                            (<! (async/map vector (map sub-fn new-subs))))]
        (swap! sub->unsub-fn merge (zipmap new-subs new-unsub-fns))
        (doseq [f (map @sub->unsub-fn old-subs)]
          (if (bu/chan? f)
            (close! f)
            (f)))
        (swap! sub->unsub-fn #(apply dissoc % old-subs)))
      (recur))
    (add-watch sub-atom ::maintain-subscriptions watch)
    (watch nil nil #{} @sub-atom)))

(defn merge-subscription-results!
  "Continually merge results from subscription into sub-results-atom. Returns a channel
  that delivers sub-channel after the first result has been merged."
  [{:keys [sub-results-atom merge-result sub-key sub-channel]}]
  (go
    (let [merge! #(swap! sub-results-atom update sub-key merge-result %)]
      (merge! (<! sub-channel))
      (go-loop []
        (if-some [result (<! sub-channel)]
          (do
            (merge! result)
            (recur))
          (swap! sub-results-atom dissoc sub-key)))
      sub-channel)))

(defn init-sub [{:keys [verbose sub-results subscriptions handler url]
                 :or {url "/api/chsk"
                      handler (constantly nil)}}]
  (let [sub-channels (atom {})
        handler (wrap-sub handler sub-channels)
        {:keys [api-send] :as env} (init-sente {:handler handler
                                                :subscriptions subscriptions
                                                :url url})]
    (maintain-subscriptions subscriptions
      (fn [[provider query]]
        (let [ch (chan)
              merge-changeset' (if verbose
                                 (fn [db changeset]
                                   (bu/pprint [:got-query-results [provider query] changeset])
                                   (merge-changeset db changeset))
                                 merge-changeset)]
          (when verbose
            (bu/pprint [:subscribed-to [provider query]]))
          (swap! sub-channels assoc-in [provider query] ch)
          (api-send [provider {:action :subscribe
                               :query query}])
          (go
            (<! (merge-subscription-results!
                  {:sub-results-atom sub-results
                   :merge-result merge-changeset'
                   :sub-key [provider query]
                   :sub-channel ch}))
            (fn []
              (when verbose
                (bu/pprint [:unsubscribed-to [provider query]]))
              (swap! sub-channels update provider dissoc query)
              (close! ch)
              (api-send [provider {:action :unsubscribe
                                   :query query}]))))))
    env))
