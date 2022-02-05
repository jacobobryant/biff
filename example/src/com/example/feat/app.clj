(ns com.example.feat.app
  (:require [better-cond.core :as b]
            [com.biffweb :as biff :refer [q]]
            [com.example.views :as v]
            [clj-http.client :as http]
            [rum.core :as rum]
            [xtdb.api :as xt]))

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
    {:hx-post "/api/set-bar"}
    [:label.block {:for "bar"} "Bar: "
     [:span.font-mono (pr-str value)]]
    [:.h-1]
    [:.flex
     [:input.input-text.w-full#bar {:type "text" :name "bar" :value value}]
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

(defn crud []
  nil
  )

(b/defnc app [{:keys [biff/uid biff/db] :as req}]
  :let [{:user/keys [email foo bar]} (xt/entity db uid)]
  (v/render-page
    {}
    nil
    [:div "Signed in as " email ". "
     (biff/form
       {:action "/api/signout"
        :class "inline"}
       [:button.text-blue-500.hover:text-blue-800 {:type "submit"}
        "Sign out"])
     "."]
    [:.h-3]
    (biff/form
      {:action "/api/set-foo"}
      [:label.block {:for "foo"} "Foo: "
       [:span.font-mono (pr-str foo)]]
      [:.h-1]
      [:.flex
       [:input.input-text.w-full#foo {:type "text" :name "foo" :value foo}]
       [:.w-3]
       [:button.btn {:type "submit"} "Update"]]
      [:.h-1]
      [:.text-sm.text-gray-600
       "This demonstrates updating a value with a plain old form."])
    [:.h-3]
    (bar-form {:value bar})))

(defn wrap-signed-in [handler]
  (fn [{:keys [biff/uid session] :as req}]
    (if (some? uid)
      (handler req)
      {:status 303
       :headers {"location" "/"}})))

(def features
  {:routes ["" {:middleware [wrap-signed-in]}
            ["/app" {:get app}]
            ["/api/set-foo" {:post set-foo}]
            ["/api/set-bar" {:post set-bar}]]})
