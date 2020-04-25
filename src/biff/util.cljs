(ns biff.util
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop]])
  (:require
    [clojure.spec.alpha :as s]
    [goog.net.Cookies]
    [cemerick.url :as url]
    [taoensso.sente :as sente]
    [cljs.core.async :as async :refer [close! <! >! take! put! chan promise-chan mult tap]]
    [trident.util :as u]
    [clojure.set :as set]))

(defn anomaly? [x]
  (s/valid? (s/keys :req [:cognitect.anomalies/category] :opt [:cognitect.anomalies/message]) x))

(defn anom [category & [message & kvs]]
  (apply u/assoc-some
    {:cognitect.anomalies/category (keyword "cognitect.anomalies" (name category))}
    :cognitect.anomalies/message message
    kvs))

(defn wrap-api-send [api-send ready-pr]
  (fn [event]
    (let [ch (chan)]
      (.then ready-pr
        (fn []
          (api-send event 5000
            (fn [response]
              (when (anomaly? response)
                (u/pprint response))
              (put! ch (if response response ::no-response))))))
      ch)))

(defn wrap-handler [handler ready-ch]
  (fn [{:keys [id ?data] :as event}]
    (when (and (= id :chsk/state) (-> ?data second :first-open?))
      (put! ready-ch true))
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
        ;(u/pprint [:got-message id ?data])
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
          (if (u/chan? f)
            (close! f)
            (f)))
        (swap! sub->unsub-fn #(apply dissoc % old-subs)))
      (recur))
    (add-watch sub-atom ::maintain-subscriptions watch)
    (watch nil nil #{} @sub-atom)))

(defn init-sub [{:keys [sub-data subscriptions handler url]}]
  (let [sub-channels (atom {})
        handler (wrap-sub handler sub-channels)
        {:keys [api-send] :as env} (init-sente {:handler handler
                                                :url url})]
    (maintain-subscriptions subscriptions
      (fn [[provider query]]
        (let [ch (chan)]
          (swap! sub-channels assoc-in [provider query] ch)
          (api-send [provider {:action :subscribe
                               :query query}])
          (go
            (<! (u/merge-subscription-results!
                  {:sub-data-atom sub-data
                   :merge-result merge-changeset
                   :sub-key [provider query]
                   :sub-channel ch}))
            (fn []
              (swap! sub-channels update provider dissoc query)
              (close! ch)
              (api-send [provider {:action :unsubscribe
                                   :query query}]))))))
    env))

(defn adapt-react [Component props children]
  (.createElement js/React Component
    (->> props
      (u/map-vals (fn [v]
                    (cond-> v
                      (fn? v) (comp #(js->clj % :keywordize-keys true)))))
      clj->js)
    (array children)))
