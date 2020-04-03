(ns ^:nimbus nimbus.pack
  (:require
    [clojure.edn :as edn]
    [trident.util :as u]
    [ring.middleware.anti-forgery :as anti-forgery]
    [taoensso.timbre :as timbre :refer [trace debug info warn error tracef debugf infof warnf errorf]]
    [nimbus.comms :refer [api-send api]]
    [ring.util.response :as resp]
    [rum.core :as rum :refer [defc]]))

(def subscriptions (atom #{}))

(defmethod api ::subscribe
  [{:keys [uid admin] :as event} _]
  (when admin
    (swap! subscriptions conj uid)
    (api-send uid [::subscribe
                   {:query nil
                    :changeset {[::deps nil]
                                (edn/read-string (slurp "deps.edn"))}}]))
  nil)

(defmethod api ::fire
  [{:keys [admin] :as event} _]
  (u/pprint event)
  (println admin))

(defc csrf []
  [:input#__anti-forgery-token
   {:name "__anti-forgery-token"
    :type "hidden"
    :value (force anti-forgery/*anti-forgery-token*)}])

(defn unsafe [html]
  {:dangerouslySetInnerHTML {:__html html}})

(defc pack-page [{{:keys [search]} :params :as req}]
  (u/pprint (:params req))
  (println search)
  [:html {:lang "en-US"
          :style {:min-height "100%"}}
   [:head
    [:title "Nimbus Pack"]
    [:meta {:charset "utf-8"}]
    [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
    [:link
     {:crossorigin "anonymous"
      :integrity "sha384-ggOyR0iXCbMQv3Xipma34MD+dH/1fQ784/j6cY/iJTQUOhcWr7x9JvoRxT2MZw1T"
      :href "https://stackpath.bootstrapcdn.com/bootstrap/4.3.1/css/bootstrap.min.css"
      :rel "stylesheet"}]]
   [:body {:style {:font-family "'Helvetica Neue', Helvetica, Arial, sans-serif"}}
    [:.container-fluid.mt-3
     [:form {:method "post"}
      (csrf)
      [:.d-flex
       [:input.form-control {:name "search"
                             :type "text"
                             :autofocus true
                             :value search
                             :placeholder "Search for a package"}]
       [:.mr-2]
       [:button.btn.btn-primary
         (merge
           {:type "submit"}
           (unsafe "Search"))]]]]]])

(defn render [f]
  (fn [req]
    {:status 200
     :body (rum/render-static-markup (f req))
     :headers {"Content-Type" "text/html"}}))

(def config
  {:nimbus.comms/route
   [""
    ["/nimbus/pack" {:get (render pack-page)
                     :post (render pack-page)
                     :name ::pack}]]})
