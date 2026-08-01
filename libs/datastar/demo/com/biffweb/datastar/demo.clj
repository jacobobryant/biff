(ns com.biffweb.datastar.demo
  (:require
   [clojure.data.json :as json]
   [clojure.string :as str]
   [com.biffweb.datastar :as biff.datastar]
   [dev.onionpancakes.chassis.core :as chassis]
   [ring.adapter.jetty :as ring-jetty]
   [ring.middleware.json :refer [wrap-json-params]]
   [ring.middleware.params :refer [wrap-params]]
   [ring.middleware.session :refer [wrap-session]])
  (:import
   (java.time Instant)
   (java.util UUID)
   (java.util.concurrent Executors)
   (org.eclipse.jetty.util.thread ExecutorThreadPool)))

(defonce app-state
  (atom {:channels      {"general" {:id       "general"
                                    :name     "general"
                                    :messages []}}
         :channel-order ["general"]
         :tab-state     {}}))

(defonce lock-state
  (biff.datastar/new-lock))

(def datastar-script-url
  "https://cdn.jsdelivr.net/gh/starfederation/datastar@v1.0.1/bundles/datastar.js")

(def new-channel-option "__new__")

(add-watch app-state ::refresh
           (fn [_ _ old-state new-state]
             (when-not (= old-state new-state)
               (future (biff.datastar/refresh lock-state)))))

(defn- vthread-pool []
  (doto (ExecutorThreadPool.)
    (.setVirtualThreadsExecutor (Executors/newVirtualThreadPerTaskExecutor))))

(defn- post [path]
  (str "@post("
       (pr-str path)
       ")"))

(defn- signal-patch-body [signals]
  (str "event: datastar-patch-signals\n"
       "data: signals "
       (json/write-str signals)
       "\n\n"))

(defn- signal-patch-response [signals]
  (if (seq signals)
    {:status  200
     :headers {"Cache-Control" "no-store"
               "Content-Type"  "text/event-stream; charset=utf-8"}
     :body    (signal-patch-body signals)}
    {:status 204}))

(defn- noop-response []
  {:status 204})

(defn- update-url-init [channel-id]
  (str "window.history.replaceState("
       "null, '', '?channel=' + encodeURIComponent("
       (pr-str channel-id)
       "));el.remove()"))

(defn- trim-to-nil [s]
  (let [s (some-> s str str/trim)]
    (not-empty s)))

(defn- query-channel [req]
  (some-> (get-in req [:query-params "channel"])
          trim-to-nil))

(defn- current-tab-state [state req]
  (when-let [tab-id (:biff.datastar/tab-id req)]
    (get-in state [:tab-state tab-id])))

(defn- selected-channel-id [channel-id]
  (when (and channel-id (not= channel-id new-channel-option))
    channel-id))

(defn- selected-channel-option [req state]
  (or (trim-to-nil (:channel-id (current-tab-state state req)))
      (query-channel req)
      "general"))

(defn- signal-value [req k]
  (some-> (get-in req [:biff.datastar/signals k])
          trim-to-nil))

(defn- current-channel-id [req state]
  (or (selected-channel-id (selected-channel-option req state))
      (selected-channel-id (query-channel req))
      "general"))

(defn- new-channel-selected? [req state]
  (= new-channel-option (selected-channel-option req state)))

(defn- input-signal [name value]
  {(keyword (str "data-signals:" name "__ifmissing"))
   (if (empty? value) "''" (pr-str value))})

(defn- ensure-channel [state channel-id]
  (if (get-in state [:channels channel-id])
    state
    (-> state
        (assoc-in [:channels channel-id] {:id       channel-id
                                          :name     channel-id
                                          :messages []})
        (update :channel-order conj channel-id))))

(defn- update-tab-state [state tab-id f & args]
  (if tab-id
    (apply update-in state [:tab-state tab-id] (fnil f {}) args)
    state))

(defn- channels [state]
  (mapv #(get-in state [:channels %]) (:channel-order state)))

(defn- channel-view [state channel-id]
  (get-in state [:channels channel-id]
          {:id channel-id :name channel-id :messages []}))

(defn- channel-exists? [state channel-id]
  (contains? (:channels state) channel-id))

(defn- page-head [req]
  [:head
   [:meta {:charset "utf-8"}]
   [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
   [:meta {:name "csrf-token" :content (:anti-forgery-token req)}]
   [:script {:type "module"
             :src  datastar-script-url}]
   [:script {:type "module"}
    (chassis/raw (biff.datastar/configure-csrf datastar-script-url (:anti-forgery-token req)))]
   [:title "biff.datastar demo"]
   [:style "
body { font-family: system-ui, sans-serif; margin: 0; background: #f5f7fb; color: #1f2937; }
.page { max-width: 960px; margin: 0 auto; padding: 2rem 1rem 3rem; }
.panel { background: white; border: 1px solid #dbe3ee; border-radius: 12px; padding: 1rem; box-shadow: 0 8px 30px rgba(15, 23, 42, 0.05); }
.stack { display: grid; gap: 1rem; }
.controls { display: grid; gap: 0.75rem; }
.row { display: flex; gap: 0.75rem; flex-wrap: wrap; align-items: center; }
label { display: grid; gap: 0.35rem; font-size: 0.95rem; font-weight: 600; }
 input, select, textarea, button { font: inherit; box-sizing: border-box; }
input, select, textarea { width: 100%; padding: 0.7rem 0.85rem; border: 1px solid #cbd5e1; border-radius: 10px; background: white; }
textarea { min-height: 7rem; resize: vertical; }
button { border: 0; border-radius: 10px; background: #2563eb; color: white; padding: 0.75rem 1rem; cursor: pointer; }
button.secondary { background: #475569; }
.messages { height: 22rem; overflow-y: auto; border: 1px solid #dbe3ee; border-radius: 10px; padding: 0.75rem; background: #f8fafc; }
.message-list { display: grid; gap: 0.75rem; }
.message { background: white; border: 1px solid #dbe3ee; border-radius: 10px; padding: 0.75rem; }
.message header { display: flex; justify-content: space-between; gap: 1rem; font-size: 0.9rem; color: #475569; margin-bottom: 0.35rem; }
.message p { margin: 0; white-space: pre-wrap; }
.muted { color: #64748b; font-size: 0.95rem; }
.grow { flex: 1 1 18rem; }
"]])

(defn- channel-selector [req state]
  (let [selected-option  (selected-channel-option req state)
        missing-channel? (and (selected-channel-id selected-option)
                              (not (channel-exists? state selected-option)))]
    [:div.stack
     [:form.stack (merge {:data-on:change (post "/channel")}
                         (input-signal "channelId" selected-option))
      [:label
       "Channel"
       [:select {:id                  "channel-select"
                 :name                "channelId"
                 :data-bind:channelId ""}
        (for [{:keys [id name]} (channels state)]
          [:option (cond-> {:value id}
                     (= id selected-option)
                     (assoc :selected true))
           name])
        (when missing-channel?
          [:option {:value    selected-option
                    :selected true
                    :disabled true}
           (str selected-option " (not found)")])
        [:option (cond-> {:value new-channel-option}
                   (= new-channel-option selected-option)
                   (assoc :selected true))
         "new channel..."]]]]
     (when (new-channel-selected? req state)
       [:form.stack
        (merge {:data-on:submit (post "/channels")}
               (input-signal "newChannelName" ""))
        [:label
         "Create a channel"
         [:input {:id                       "new-channel-name"
                  :name                     "newChannelName"
                  :placeholder              "team-updates"
                  :required                 true
                  :data-bind:newChannelName ""}]]
        [:div.row
         [:button {:type "submit"} "Create channel"]]])]))

(defn- missing-channel-message [channel-id]
  [:div.stack
   [:h2 {:style "margin:0"} "Channel not found"]
   [:p.muted
    (str "There isn't a channel named #" channel-id ". Create it from the selector above or pick another channel.")]])

(defn- message-list [state selected-channel]
  (let [{:keys [messages name]} (channel-view state selected-channel)]
    [:div.stack
     [:div.row
      [:h2 {:style "margin:0"} (str "#" name)]
      [:div.muted (str (count messages) " messages")]]
     [:div#messages.messages
      {:data-signals:message-count                 (str (count messages))
       :data-signals:messages-at-bottom__ifmissing "true"
       :data-on:scroll                             "$messagesAtBottom = el.scrollHeight - el.scrollTop - el.clientHeight <= 8"
       :data-effect                                "$messageCount, $messagesAtBottom && requestAnimationFrame(() => { el.scrollTop = el.scrollHeight; })"}
      (if (seq messages)
        [:div.message-list
         (for [{:keys [id display-name text created-at]} messages]
           [:article.message {:id (str "message-" id)}
            [:header
             [:strong display-name]
             [:span (.toString ^Instant created-at)]]
            [:p text]])]
        [:p.muted "No messages yet. Say hello."])]]))

(defn- composer [channel-id]
  [:form.stack
   (merge {:data-on:submit (post "/messages")}
          (input-signal "displayName" "Alice")
          (input-signal "channelId" channel-id)
          (input-signal "messageText" ""))
   [:label
    "Display name"
    [:input {:id                    "display-name"
             :name                  "displayName"
             :placeholder           "Sprite"
             :required              true
             :data-bind:displayName ""}]]
   [:label
    "Message"
    [:textarea {:id                    "message-text"
                :name                  "messageText"
                :placeholder           "Type a message..."
                :required              true
                :data-bind:messageText ""}]]
   [:div.row
    [:button {:type "submit"} "Send"]]])

(defn- content [req state]
  (let [selected-channel         (current-channel-id req state)
        selected-channel-exists? (channel-exists? state selected-channel)]
    [:div#biff-datastar-content.page
     [:div {:data-init (update-url-init selected-channel)}]
     [:div.stack
      [:div.panel.stack
       [:h1 {:style "margin:0"} "biff.datastar demo chat"]
       [:p.muted {:style "margin:0"} "One page, live updates, and per-tab channel state. Booyah."]
       (channel-selector req state)]
      [:div.panel.stack
       (if selected-channel-exists?
         (message-list state selected-channel)
         (missing-channel-message selected-channel))]
      (when selected-channel-exists?
        [:div.panel
         (composer selected-channel)])]]))

(defn- page-response [req]
  (let [state     @app-state
        page-body (if (:biff.datastar/sse-request req)
                    (content req state)
                    [chassis/doctype-html5
                     [:html {:lang "en"}
                      (page-head req)
                      [:body
                       [:div (merge {:class "stack"} biff.datastar/init-opts)
                        (content req state)]]]])]
    {:status  200
     :headers {"Content-Type" "text/html; charset=utf-8"}
     :body    (chassis/html page-body)}))

(defn- set-channel-handler [req]
  (swap! app-state
         update-tab-state
         (:biff.datastar/tab-id req)
         assoc
         :channel-id
         (or (signal-value req :channelid)
             "general"))
  (noop-response))

(defn- create-channel-handler [req]
  (if-let [channel-id (signal-value req :newchannelname)]
    (do
      (swap! app-state
             (fn [state]
               (-> state
                   (ensure-channel channel-id)
                   (update-tab-state (:biff.datastar/tab-id req)
                                     assoc
                                     :channel-id
                                     channel-id))))
      (signal-patch-response {"channelid"      channel-id
                              "newchannelname" ""}))
    {:status 204}))

(defn- send-message-handler [req]
  (let [channel-id   (signal-value req :channelid)
        display-name (signal-value req :displayname)
        message-text (signal-value req :messagetext)
        message-id   (str (UUID/randomUUID))
        state        (if (and channel-id display-name message-text)
                       (swap! app-state
                              (fn [state]
                                (if (channel-exists? state channel-id)
                                  (update-in state
                                             [:channels channel-id :messages]
                                             conj
                                             {:id           message-id
                                              :display-name display-name
                                              :text         message-text
                                              :created-at   (Instant/now)})
                                  state)))
                       @app-state)]
    (if (some #(= message-id (:id %))
              (get-in state [:channels channel-id :messages]))
      (signal-patch-response {"messagetext" ""})
      {:status 204})))

(defn- not-found [_]
  {:status  404
   :headers {"Content-Type" "text/plain; charset=utf-8"}
   :body    "not found"})

(defn- routes [req]
  (case [(:request-method req) (:uri req)]
    [:get "/"] (page-response req)
    [:head "/"] (page-response req)
    [:post "/channel"] (set-channel-handler req)
    [:post "/channels"] (create-channel-handler req)
    [:post "/messages"] (send-message-handler req)
    (not-found req)))

(def app-sync
  (-> routes
      (biff.datastar/wrap-datastar
       lock-state)
      wrap-session
      (wrap-json-params {:keywords? true})
      wrap-params))

(defonce server (atom nil))

(defn start! []
  (when-let [server* @server]
    (.stop server*))
  (reset! server
          (ring-jetty/run-jetty app-sync
                                {:join?       false
                                 :port        8080
                                 :thread-pool (vthread-pool)}))
  @server)

(defn stop! []
  (when-let [server* @server]
    (.stop server*)
    (reset! server nil)))

(defn -main [& _]
  (start!)
  (println "Demo running on http://localhost:8080"))
