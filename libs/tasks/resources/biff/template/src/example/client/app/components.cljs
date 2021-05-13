(ns {{parent-ns}}.client.app.components
  (:require [biff.util :as bu]
            [clojure.pprint :as pp]
            [reitit.frontend.easy :as rfe]
            [rum.core :as rum :refer [react defc defcs reactive local]]
            [{{parent-ns}}.views.shared :as shared]
            [{{parent-ns}}.client.app.db :as db]
            [{{parent-ns}}.client.app.mutations :as m]))

; See https://github.com/tonsky/rum

(defc db-contents < reactive
  []
  [:div
   [:div "db/data:"]
   [:.h-1]
   [:pre.text-sm (bu/ppr-str (react db/data))]
   [:.h-6]
   [:div "db/subscriptions:"]
   [:.h-1]
   [:pre.text-sm (bu/ppr-str (react db/subscriptions))]])

(defcs set-value < reactive (local "" ::tmp-value)
  [{::keys [tmp-value]} {:keys [label model mutate description]}]
  [:div
   [:div label ": " [:span.font-mono (pr-str (react model))]]
   [:.text-sm.text-gray-600 description]
   [:.h-1]
   [:.flex
    [:input.input-text.w-full
     {:value @tmp-value
      :on-change #(reset! tmp-value (.. % -target -value))}]
    [:.w-3]
    [:button.btn {:on-click #(mutate @tmp-value)}
     "Update"]]])

(defcs write-message < (local "" ::tmp-value)
  [{::keys [tmp-value]}]
  [:div
   [:div "Write a message"]
   [:.text-sm.text-gray-600
    "Sign-in with an incognito window to have a conversation with yourself."]
   [:.h-2]
   [:div [:textarea.input-text.w-full
          {:value @tmp-value
           :on-change #(reset! tmp-value (.. % -target -value))}]]
   [:.h-3]
   [:.flex.justify-end
    [:button.btn.ml-auto
     {:on-click #(do
                   (m/send-message @tmp-value)
                   (reset! tmp-value ""))}
     "Send"]]])

(defc crud < reactive
  []
  [:div
   (set-value {:label "Foo"
               :model db/foo
               :mutate m/set-foo
               :description "This demonstrates updating a document via a Biff transaction."})
   [:.h-6]
   (set-value {:label "Bar"
               :model db/bar
               :mutate m/set-bar
               :description (str "This demonstrates updating a document via a custom "
                              "websocket event handler. (Also, see the console for a surprise!)")})
   [:.h-6]
   (write-message)
   [:.h-6]
   [:div "Messages sent after "
    (.toLocaleTimeString (react db/message-cutoff)) ":"]
   [:.h-3]
   (for [{:msg/keys [text sent-at user]
          doc-id :crux.db/id} (react db/messages)]
     [:.mb-3 {:key (str doc-id)}
      [:.flex.align-baseline.text-sm
       [:.text-gray-600 (.toLocaleTimeString sent-at)]
       [:.w-4]
       (when (= user (react db/uid))
         [:button.text-blue-500.hover:text-blue-800
          {:on-click #(m/delete-message doc-id)}
          "Delete"])]
      [:div text]])])

(defc main < reactive
  []
  [:div
   (shared/header {:email db/email})
   [:.h-6]
   (shared/tabs {:active-id db/tab
                 :tab-data [{:id :crud
                             :href (rfe/href :crud)
                             :label "CRUD"}
                            {:id :db
                             :href (rfe/href :db)
                             :label "DB Contents"}
                            {:id :ssr
                             :href "/app/ssr"
                             :label "SSR"}]})
   [:.h-3]
   (case (react db/tab)
     :crud (crud)
     :db (db-contents))])
