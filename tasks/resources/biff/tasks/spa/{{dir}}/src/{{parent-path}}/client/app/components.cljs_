(ns {{parent-ns}}.client.app.components
  (:require
    [clojure.pprint :as pp]
    [reitit.frontend.easy :as rfe]
    [rum.core :as rum :refer [react defc defcs reactive local]]
    [{{parent-ns}}.client.app.db :as db]
    [{{parent-ns}}.client.app.mutations :as m]))

; See https://github.com/tonsky/rum

(def routes
  [["/app/" {:name :crud}]
   ["/app/db" {:name :db}]])

(defc tabs < reactive
  [tab-data]
  [:ul.flex.border-b
   (for [[tab label] tab-data]
     (if (= (react db/tab) tab)
       [:li.-mb-px.mr-1 {:key (str tab)}
        [:a.bg-white.inline-block.border-l.border-t.border-r.rounded-t.py-2.px-4.text-blue-700.font-semibold
         {:href (rfe/href tab)}
         label]]
       [:li.mr-1 {:key (str tab)}
        [:a.bg-white.inline-block.py-2.px-4.text-blue-500.hover:text-blue-800.font-semibold
         {:href (rfe/href tab)}
         label]]))])

(defc db-contents < reactive
  []
  [:div
   [:pre.text-sm (with-out-str (pp/pprint (react db/data)))]
   [:hr.my-6]
   [:p "When you've had enough fun here, start reading through the code. Here are some good "
    "starting points:"]
   [:ul.list-disc.pl-8.font-mono
    [:li "src/{{parent-path}}/client/app.cljs"]
    [:li "src/{{main-ns-path}}.clj"]
    [:li "all-tasks/10-biff"]
    [:li "config/"]]])

(defcs set-value < reactive (local "" ::tmp-value)
  [{::keys [tmp-value]} {:keys [label model mutate description]}]
  [:div
   [:.text-lg label ": " [:span.font-mono (pr-str (react model))]]
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
   [:.text-lg "Write a message"]
   [:.text-sm.text-gray-600 "Sign-in with an incognito window to have a conversation with yourself."]
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
   [:.text-lg "Messages sent after "
    (.toLocaleTimeString (react db/message-cutoff)) ":"]
   [:.h-3]
   (for [{:keys [text timestamp]
          doc-id :crux.db/id
          user-id :user/id} (react db/messages)]
     [:.mb-3 {:key (str doc-id)}
      [:.flex.align-baseline.text-sm
       [:.text-gray-600 (.toLocaleTimeString timestamp)]
       [:.w-4]
       (when (= user-id (react db/uid))
         [:button.text-blue-500.hover:text-blue-800
          {:on-click #(m/delete-message doc-id)}
          "Delete"])]
      [:div text]])])

(defc main < reactive
  []
  [:div
   [:.flex
    [:div "Signed in as " (react db/email)]
    [:.flex-grow]
    [:a.text-blue-500.hover:text-blue-800 {:href "/api/signout"}
     "Sign out"]]
   [:.h-6]
   (tabs [[:crud "CRUD"]
          [:db "DB Contents"]])
   [:.h-3]
   (case (react db/tab)
     :crud (crud)
     :db (db-contents))])
