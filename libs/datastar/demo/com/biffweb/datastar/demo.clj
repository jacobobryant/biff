(ns com.biffweb.datastar.demo
  (:require
   [com.biffweb.datastar :as biff.datastar
    :refer [signals-json signal-name patch-signals]]
   [dev.onionpancakes.chassis.core :as chassis]
   [malli.core :as m]
   [malli.error :as me]
   [ring.adapter.jetty :as ring-jetty]
   [ring.middleware.json :refer [wrap-json-params]]
   [ring.middleware.params :refer [wrap-params]]
   [ring.util.codec :as codec])
  (:import
   (java.time Instant)
   (java.util UUID)))

;;;; routes ====================================================================

(def chat-page-path      "/")
(def set-channel-path    "/channel")
(def create-channel-path "/channels")
(def send-message-path   "/messages")

;;;; data utilities ============================================================

(def new-channel-option "__new__")

(defn- channels [state]
  (mapv #(get-in state [:channels %]) (:channel-order state)))

(defn- channel-view [state channel-id]
  (get-in state
          [:channels channel-id]
          {:id channel-id :name channel-id :messages []}))

(defn- channel-exists? [state channel-id]
  (contains? (:channels state) channel-id))

;;;; UI utilities ==============================================================

(defn- input-signals [signals]
  {:data-signals__ifmissing (signals-json signals)})

(defn- replace-query-params [params]
  [:div {:data-query-params (codec/form-encode params)
         :data-init         (str "window.history.replaceState(null,'',"
                                 "'?'+el.dataset.queryParams);"
                                 "el.remove()")}])

(def datastar-script-url
  "https://cdn.jsdelivr.net/gh/starfederation/datastar@v1.0.1/bundles/datastar.js")

(defn- page-head []
  [:head
   [:meta {:charset "utf-8"}]
   [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
   [:script {:type "module" :src datastar-script-url}]
   [:title "biff.datastar demo"]])

(def ^:private stack-style
  {:display "grid" :gap "1rem"})

(def ^:private row-style
  {:display "flex" :gap "0.75rem" :flex-wrap "wrap" :align-items "center"})

(def ^:private label-style
  {:display "grid" :gap "0.35rem" :font-size "0.95rem" :font-weight 600})

(def ^:private field-style
  {:font          "inherit"
   :box-sizing    "border-box"
   :width         "100%"
   :padding       "0.7rem 0.85rem"
   :border        "1px solid #cbd5e1"
   :border-radius "10px"
   :background    "white"})

(def ^:private button-style
  {:font          "inherit"
   :border        0
   :border-radius "10px"
   :background    "#2563eb"
   :color         "white"
   :padding       "0.75rem 1rem"
   :cursor        "pointer"})

(def ^:private muted-style
  {:color "#64748b" :font-size "0.95rem"})

(def ^:private panel-style
  {:background    "white"
   :border        "1px solid #dbe3ee"
   :border-radius "12px"
   :padding       "1rem"
   :box-shadow    "0 8px 30px rgba(15, 23, 42, 0.05)"})

(def ^:private stack-panel-style
  (merge stack-style panel-style))

(defn- page-shim [_ctx & content]
  [chassis/doctype-html5
   [:html {:lang "en"}
    (page-head)
    [:body (merge {:style {:font-family "system-ui, sans-serif"
                           :margin      0
                           :background  "#f5f7fb"
                           :color       "#1f2937"}}
                  (biff.datastar/init-opts))
     content]]])

(defn- sse-page-response [{:keys [biff.datastar/sse-request] :as ctx}
                          & content]
  (let [content* [:div#biff-datastar-content content]]
    {:status  200
     :headers {"Content-Type" "text/html; charset=utf-8"}
     :body    (chassis/html
               (if sse-request
                 content*
                 (page-shim ctx content*)))}))

;;;; application UI ============================================================

(defn- channel-selector [selected-option state]
  (let [missing-channel? (and (not= selected-option new-channel-option)
                              (not (channel-exists? state selected-option)))]
    [:div {:style stack-style}
     [:form {:style          stack-style
             :data-on:change "@post(el.dataset.action)"
             :data-action    set-channel-path}
      [:label {:style label-style}
       "Channel"
       [:select {:id        "channel-select"
                 :name      "channelId"
                 :style     field-style
                 :data-bind (signal-name ::channel-id)}
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
     (when (= selected-option new-channel-option)
       [:form (merge {:style          stack-style
                      :data-on:submit "@post(el.dataset.action)"
                      :data-action    create-channel-path}
                     (input-signals {::new-channel-name ""}))
        [:label {:style label-style}
         "Create a channel"
         [:input {:id          "new-channel-name"
                  :name        "newChannelName"
                  :placeholder "team-updates"
                  :required    true
                  :style       field-style
                  :data-bind   (signal-name ::new-channel-name)}]]
        [:div {:style row-style}
         [:button {:type  "submit"
                   :style button-style}
          "Create channel"]]])]))

(defn- missing-channel-message [channel-id]
  [:div {:style stack-style}
   [:h2 {:style {:margin 0}} "Channel not found"]
   [:p {:style muted-style}
    (str "There isn't a channel named #" channel-id
         ". Create it from the selector above or pick another channel.")]])

(defn- message-list [state selected-channel]
  (let [{:keys [messages name]} (channel-view state selected-channel)]
    [:div {:style stack-style}
     [:div {:style row-style}
      [:h2 {:style {:margin 0}} (str "#" name)]
      [:div {:style muted-style}
       (str (count messages) " messages")]]
     [:div#messages
      {:style          {:height        "22rem"
                        :overflow-y    "auto"
                        :border        "1px solid #dbe3ee"
                        :border-radius "10px"
                        :padding       "0.75rem"
                        :background    "#f8fafc"}
       :data-on:scroll (str "el._atBottom = el.scrollHeight - "
                            "el.scrollTop - el.clientHeight <= 8")
       ;; this code fires whenever (count messages) changes
       :data-init      (str "/*" (count messages) "*/ "
                            "el._atBottom !== false && "
                            "requestAnimationFrame(() => { "
                            "  el.scrollTop = el.scrollHeight;"
                            "})")}
      (if (seq messages)
        [:div {:style {:display "grid" :gap "0.75rem"}}
         (for [{:keys [id display-name text created-at]} messages]
           [:article {:id    (str "message-" id)
                      :style {:background    "white"
                              :border        "1px solid #dbe3ee"
                              :border-radius "10px"
                              :padding       "0.75rem"}}
            [:header {:style {:display         "flex"
                              :justify-content "space-between"
                              :gap             "1rem"
                              :font-size       "0.9rem"
                              :color           "#475569"
                              :margin-bottom   "0.35rem"}}
             [:strong display-name]
             [:span (.toString ^Instant created-at)]]
            [:p {:style {:margin 0 :white-space "pre-wrap"}} text]])]
        [:p {:style muted-style}
         "No messages yet. Say hello."])]]))

(defn- composer []
  [:form (merge {:style          stack-style
                 :data-on:submit "@post(el.dataset.action)"
                 :data-action    send-message-path}
                (input-signals {::display-name "Alice"
                                ::message-text ""}))
   [:label {:style label-style}
    "Display name"
    [:input {:id          "display-name"
             :name        "displayName"
             :placeholder "Sprite"
             :required    true
             :style       field-style
             :data-bind   (signal-name ::display-name)}]]
   [:label {:style label-style}
    "Message"
    [:textarea {:id          "message-text"
                :name        "messageText"
                :placeholder "Type a message..."
                :required    true
                :style       (assoc field-style
                                    :min-height "7rem"
                                    :resize "vertical")
                :data-bind   (signal-name ::message-text)}]]
   [:div {:style row-style}
    [:button {:type  "submit"
              :style button-style}
     "Send"]]])

(defn- chat-page
  [{:keys [query-params biff.datastar/tab-id ::state-value] :as ctx}]
  (sse-page-response
   ctx
   (let [channel-options
         [(get-in state-value [:tab-state tab-id :channel-id])
          (get query-params "channel")
          "general"]

         selected-option
         (some identity channel-options)

         selected-channel
         (some identity (remove #{new-channel-option} channel-options))

         selected-channel-exists?
         (channel-exists? state-value selected-channel)]
     [:div
      (merge {:style {:max-width "960px"
                      :margin    "0 auto"
                      :padding   "2rem 1rem 3rem"}}
             (input-signals {::channel-id selected-option}))
      (replace-query-params {:channel selected-channel})
      [:div {:style stack-style}
       [:div {:style stack-panel-style}
        [:h1 {:style {:margin 0}} "biff.datastar demo chat"]
        [:p {:style (assoc muted-style :margin 0)}
         "One page, live updates, and per-tab channel state. Booyah."]
        (channel-selector selected-option state-value)]
       [:div {:style stack-panel-style}
        (if selected-channel-exists?
          (message-list state-value selected-channel)
          (missing-channel-message selected-channel))]
       (when selected-channel-exists?
         [:div {:style panel-style}
          (composer)])]])))

;;;; actions ===================================================================

(defn- set-channel-handler
  [{:biff.datastar/keys [signals tab-id] ::keys [state]}]
  (swap! state
         assoc-in
         [:tab-state tab-id :channel-id]
         (get signals ::channel-id "general"))
  {:status 204})

(defn- ensure-channel [state channel-id]
  (if (get-in state [:channels channel-id])
    state
    (-> state
        (assoc-in [:channels channel-id] {:id       channel-id
                                          :name     channel-id
                                          :messages []})
        (update :channel-order conj channel-id))))

(defn- create-channel-handler
  [{:biff.datastar/keys [signals tab-id] ::keys [state]}]
  (let [{::keys [new-channel-name]} signals]
    (swap! state #(-> %
                      (ensure-channel new-channel-name)
                      (assoc-in [:tab-state tab-id :channel-id]
                                new-channel-name)))
    (patch-signals
     {::channel-id       new-channel-name
      ::new-channel-name ""})))

(defn- send-message-handler [{:biff.datastar/keys [signals] ::keys [state]}]
  (let [{::keys [channel-id display-name message-text]} signals

        message-id (str (UUID/randomUUID))
        new-state  (if (and channel-id display-name message-text)
                     (swap! state
                            (fn [app-state]
                              (if (channel-exists? app-state channel-id)
                                (update-in app-state
                                           [:channels channel-id :messages]
                                           conj
                                           {:id           message-id
                                            :display-name display-name
                                            :text         message-text
                                            :created-at   (Instant/now)})
                                app-state)))
                     @state)]
    (if (some #(= message-id (:id %))
              (get-in new-state [:channels channel-id :messages]))
      (patch-signals {::message-text ""})
      {:status 204})))

;;;; system ====================================================================

(def routes
  {[:get chat-page-path]       chat-page
   [:post set-channel-path]    set-channel-handler
   [:post create-channel-path] create-channel-handler
   [:post send-message-path]   send-message-handler})

(defn- base-handler [{:keys [request-method uri] :as req}]
  (if-let [handler (get routes [request-method uri])]
    (handler req)
    {:status  404
     :headers {"Content-Type" "text/plain; charset=utf-8"}
     :body    "not found"}))

(defn wrap-state [handler]
  (fn [request]
    (handler (assoc request ::state-value @(::state request)))))

(def handler
  (-> base-handler
      ;; wrap-state must come before wrap-sse-render so that we get up-to-date
      ;; state every time wrap-sse-render calls the underlying handler.
      wrap-state
      biff.datastar/wrap-sse-render
      (wrap-json-params {:keywords? true})
      wrap-params))

(defonce system (atom nil))

(def state-schema
  [:map {:closed true}
   [:channels
    [:map-of
     :string
     [:map {:closed true}
      [:id :string]
      [:name :string]
      [:messages
       [:vector
        [:map {:closed true}
         [:id :string]
         [:display-name :string]
         [:text :string]
         [:created-at [:fn #(instance? Instant %)]]]]]]]]
   [:channel-order [:vector :string]]
   [:tab-state
    [:map-of
     :uuid
     [:map {:closed true}
      [:channel-id :string]]]]])

(def default-app-state
  {:channels      {"general" {:id       "general"
                              :name     "general"
                              :messages []}}
   :channel-order ["general"]
   :tab-state     {}})

(defn start! []
  (let [state  (atom default-app-state)
        ctx    (merge (biff.datastar/new-lock)
                      {::state state})
        _      (add-watch state ::refresh
                          (fn [_ _ old-state new-state]
                            (assert (m/validate state-schema new-state)
                                    (pr-str (me/humanize
                                             (m/explain state-schema
                                                        new-state))))
                            (when-not (= old-state new-state)
                              (biff.datastar/refresh ctx))))
        server (ring-jetty/run-jetty #(handler (merge % ctx))
                                     {:join? false :port 8080})
        ctx    (merge ctx {::server server
                           ::close  (fn []
                                      (.stop server)
                                      (remove-watch state ::refresh))})]
    (reset! system ctx)))

(defn stop! []
  (when-some [close (::close @system)]
    (close))
  (reset! system nil))

(defn -main [& _]
  (start!)
  (println "Demo running on http://localhost:8080"))
