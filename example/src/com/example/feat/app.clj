(ns com.example.feat.app
  (:require [better-cond.core :as b]
            [com.biffweb :as biff :refer [q]]
            [com.example.views :as v]
            [clj-http.client :as http]
            [rum.core :as rum]
            [xtdb.api :as xt]
            [ring.adapter.jetty9 :as jetty]))

(defn set-foo [{:keys [biff/uid params] :as req}]
  (biff/submit-tx req
    [{:db/op :update
      :db/doc-type :user
      :xt/id uid
      :user/foo (:foo params)}])
  {:status 303
   :headers {"location" "/app"}})

(defn bar-form [{:keys [value]}]
  (biff/form
    {:hx-post "/app/set-bar"}
    [:label.block {:for "bar"} "Bar: "
     [:span.font-mono (pr-str value)]]
    [:.h-1]
    [:.flex
     [:input.w-full#bar {:type "text" :name "bar" :value value}]
     [:.w-3]
     [:button.btn {:type "submit"} "Update"]]
    [:.h-1]
    [:.text-sm.text-gray-600
     "This demonstrates updating a value with HTMX."]))

(defn set-bar [{:keys [biff/uid params] :as req}]
  (biff/submit-tx req
    [{:db/op :update
      :db/doc-type :user
      :xt/id uid
      :user/bar (:bar params)}])
  (biff/render (bar-form {:value (:bar params)})))

(defn send-message [{:keys [biff/uid params] :as req}]
  (biff/submit-tx req
    [{:db/doc-type :msg
      :msg/user uid
      :msg/text (:text params)
      :msg/sent-at :db/now}])
  {:status 303
   :headers {"location" "/app"}})

(b/defnc app [{:keys [biff/uid biff/db] :as req}]
  :let [{:user/keys [email foo bar]} (xt/entity db uid)]
  (v/render-page
    {}
    nil
    [:div "Signed in as " email ". "
     (biff/form
       {:action "/auth/signout/"
        :class "inline"}
       [:button.text-blue-500.hover:text-blue-800 {:type "submit"}
        "Sign out"])
     "."]
    [:.h-6]
    (biff/form
      {:action "/app/set-foo"}
      [:label.block {:for "foo"} "Foo: "
       [:span.font-mono (pr-str foo)]]
      [:.h-1]
      [:.flex
       [:input.w-full#foo {:type "text" :name "foo" :value foo}]
       [:.w-3]
       [:button.btn {:type "submit"} "Update"]]
      [:.h-1]
      [:.text-sm.text-gray-600
       "This demonstrates updating a value with a plain old form."])
    [:.h-6]
    (bar-form {:value bar})
    [:.h-6]
    (biff/form
      {:action "/app/send-message"}
      [:label.block {:for "message"} "Write a message"]
      [:.h-1]
      [:textarea.w-full#message {:name "text"}]
      [:.h-1]
      [:.text-sm.text-gray-600
       "Sign in with an incognito window to have a conversation with yourself."]
      [:.h-2]
      [:div [:button.btn {:type "submit"} "Send message"]])
    [:.h-6]
    (let [messages (q db
                      '{:find (pull msg [*])
                        :in [t0]
                        :where [[msg :msg/sent-at t]
                                [(<= t0 t)]]}
                      (biff/add-seconds (java.util.Date.) (* -60 10)))]
      (if (empty? messages)
        [:div "No messages yet."]
        (list
          [:div "Messages sent in the past 10 minutes:"]
          [:.h-3]
          (for [{:msg/keys [text sent-at]} (sort-by :msg/sent-at #(compare %2 %1) messages)]
            [:.mb-3
             [:.text-gray-600 (biff/format-date sent-at "dd MMM yyyy")]
             [:div text]]))))
    [:div {:hx-ws "connect:/app/ws"}
     [:div#ws "hello there"]
     [:form {:hx-ws "send"}
      [:input {:type "text" :name "sometext"}]]]))

(defn wrap-signed-in [handler]
  (fn [{:keys [biff/uid session] :as req}]
    (if (some? uid)
      (handler req)
      {:status 303
       :headers {"location" "/"}})))

(defn handler [{:keys [example/chat-clients biff/base-url headers] :as req}]
  (if (not= base-url (get headers "origin" ""))
    {:status 401
     :headers {"content-type" "text/plain"}
     :body "Unauthorized"}
    {:status 101
     :headers {"upgrade" "websocket"
               "connection" "upgrade"}
     :ws {:on-connect (fn [ws]
                        (swap! chat-clients conj ws)
                        (jetty/send! ws "<div id=\"ws\">connected!</div>"))
          :on-text (fn [ws text-message]
                     (jetty/send! ws (str "<div id=\"ws\">" text-message "</div>")))
          :on-close (fn [ws status-code reason]
                      (swap! chat-clients disj ws))}}))

(def features
  {:routes ["/app" {:middleware [wrap-signed-in]}
            ["" {:get app}]
            ["/set-foo" {:post set-foo}]
            ["/set-bar" {:post set-bar}]
            ["/send-message" {:post send-message}]
            ["/ws" {:get handler}]]})
