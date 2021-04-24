(ns {{parent-ns}}.routes
  (:require
    [biff.crux :as bcrux]
    [biff.http :as bhttp]
    [biff.util :as bu]
    [clojure.stacktrace :as st]
    [crux.api :as crux]
    [ring.middleware.anti-forgery
     :refer [wrap-anti-forgery *anti-forgery-token*]]
    [rum.core :as rum]
    [{{parent-ns}}.static :as static]))

; See https://findka.com/biff/#http-routes

; Test it out:
; curl http://localhost:8080/echo?foo=bar
; curl -XPOST http://localhost:8080/echo -F foo=bar
; curl -XPOST http://localhost:8080/echo -H 'Content-Type: application/edn' -d '{:foo "bar"}'
; curl -XPOST http://localhost:8080/echo -H 'Content-Type: application/json' -d '{"foo":"bar"}'
(defn echo [req]
  ; Default :status is 200. Default :body is "". :headers/* and
  ; :cookies/* are converted to `:headers {...}` and `:cookies {...}`.
  {:headers/Content-Type "application/edn"
   :body (prn-str (merge
                    (select-keys req [:params :body-params])
                    (bu/select-ns req 'params)))})

; These require authentication, so you'll have to test them from the browser.
(defn whoami [{:keys [session/uid biff/db]}]
  (if (some? uid)
    {:body (:user/email (crux/entity db {:user/id uid}))
     :headers/Content-Type "text/plain"}
    {:status 401
     :headers/Content-Type "text/plain"
     :body "Not authorized."}))

(defn whoami2 [{:keys [session/uid biff/db]}]
  {:body (:user/email (crux/entity db {:user/id uid}))
   :headers/Content-Type "text/plain"})

(defn form [{:keys [event]} & contents]
  [:form.mb-0 {:method "post"
               :action "/api/write"}
   [:input {:type "hidden"
            :name "__anti-forgery-token"
            :value *anti-forgery-token*}]
   [:input {:type "hidden" :name "event" :value event}]
   contents])

(defn write-foo [{:keys [value]}]
  (form {:event "write-foo"}
    [:.text-lg "Foo: " [:span.font-mono (pr-str value)]]
    [:.text-sm.text-gray-600
     "This demonstrates updating a document."]
    [:.h-1]
    [:.flex
     [:input.input-text.w-full {:name "foo"}]
     [:.w-3]
     [:button.btn {:type "submit"} "Update"]]))

(defn write-message []
  (form {:event "write-message"}
    [:.text-lg "Write a message"]
    [:.text-sm.text-gray-600
     "Sign in with an incognito window to have a conversation with yourself."]
    [:.h-2]
    [:div [:textarea.input-text.w-full {:name "text"}]]
    [:.h-3]
    [:.flex.justify-end
     [:button.btn.ml-auto {:type "submit"} "Send"]]))

(defn app-page-contents [{:keys [biff/db session/uid]}]
  (let [{:keys [user/email foo]} (crux/entity db {:user/id uid})
        message-cutoff (bu/add-seconds (java.util.Date.) (* -60 5))
        messages (->> (crux/q db
                        {:find '[message]
                         :full-results? true
                         :args [{'t0 message-cutoff}]
                         :where '[[message :text]
                                  [message :timestamp t]
                                  [(<= t0 t)]]})
                   (map first)
                   (sort-by :timestamp #(compare %2 %1)))]
    [:div
     [:.flex
      [:div "Signed in as " email]
      [:.flex-grow]
      [:a.text-blue-500.hover:text-blue-800 {:href "/api/signout"}
       "Sign out"]]
     [:.h-6]
     [:p "When you've had enough fun here, start reading through the code. Here are some good "
      "starting points:"]
     [:ul.list-disc.pl-8.font-mono
      [:li "src/{{parent-path}}/routes.clj"]
      [:li "src/{{parent-path}}/core.clj"]
      [:li "all-tasks/10-biff"]
      [:li "config/"]]
     [:.h-6]
     (write-foo {:value foo})
     [:.h-6]
     (write-message)
     [:.h-6]
     [:.text-lg "Messages sent in the past 5 minutes :"]
     [:.text-sm.text-gray-600 "(refresh to see new messages from other users)"]
     [:.h-6]
     (when (empty? messages)
       [:p "No messages yet."])
     (for [{:keys [text timestamp]
            doc-id :crux.db/id
            user-id :user/id} messages]
       [:.mb-3
        [:.flex.align-baseline.text-sm
         [:.text-gray-600 timestamp]
         [:.w-4]
         (when (= user-id uid)
           (form {:event "delete-message"}
             [:input {:type "hidden" :name "message-id" :value doc-id}]
             [:button.text-blue-500.hover:text-blue-800
              {:type "submit"}
              "Delete"]))]
        [:div text]])]))

(defn app-page [{:keys [session/uid] :as sys}]
  (if (nil? uid)
    {:status 302
     :headers/Location "/"}
    {:headers/Content-Type "text/html"
     :body (rum/render-static-markup
             (static/base-page {}
               (app-page-contents sys)))}))

(defn write [{:keys [biff/db biff/node session/uid params/event params] :as sys}]
  (try
    (when (and (= event "delete-message")
            (not= uid (->> params
                        :message-id
                        java.util.UUID/fromString
                        (crux/entity db)
                        :user/id)))
      (throw (ex-info "Unauthorized transaction" {:params params :session/uid uid})))
    (crux/await-tx node
      (bcrux/submit-tx sys
        (case event
          "write-foo"
          {[:users {:user/id uid}] {:db/update true
                                    :foo (:foo params)}}

          "write-message"
          {[:messages] {:user/id uid
                        :timestamp (java.util.Date.)
                        :text (:text params)}}

          "delete-message"
          {[:messages (java.util.UUID/fromString (:message-id params))] nil})))
    (catch Exception e
      ; An exception would be thrown if the transaction doesn't meet the specs
      ; in {{parent-ns}}.rules, which would happen if the client e.g. set :text to a
      ; number.
      (st/print-stack-trace e)))
  {:status 302
   :headers/Location "/app"})

; See https://cljdoc.org/d/metosin/reitit/0.5.10/doc/introduction#ring-router
(def routes
  [["/echo" {:get #(echo %)
             :post #(echo %)
             :name ::echo}]
   ["/whoami" {:post #(whoami %)
               :middleware [wrap-anti-forgery]
               :name ::whoami}]
   ; Same as whoami
   ["/whoami2" {:post #(whoami2 %)
                :middleware [bhttp/wrap-authorize]
                :name ::whoami2}]
   ["/app" {:get #(app-page %)
            :middleware [wrap-anti-forgery]
            :name ::app}]
   ["/api/write" {:post #(write %)
                  :name ::write
                  :middleware [bhttp/wrap-authorize]}]])
