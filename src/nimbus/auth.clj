(ns ^:nimbus nimbus.auth
  (:require
    [clojure.edn :as edn]
    [ring.middleware.anti-forgery :as anti-forgery]
    [ring.middleware.defaults :refer [wrap-defaults site-defaults secure-site-defaults]]
    [ring.middleware.session.store :as store]
    [ring.middleware.session.memory :refer [memory-store]]
    [ring.util.response :refer [redirect]]
    [trident.util :as u]
    [crypto.password.bcrypt :as pw]
    [rum.core :as rum :refer [defc]]))

(defn unsafe [html]
  {:dangerouslySetInnerHTML {:__html html}})

(defc login-html [message]
  [:html {:lang "en-US"
          :style {:min-height "100%"}}
   [:head
    [:title "Login | Nimbus"]
    [:meta {:charset "utf-8"}]
    [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
    [:link
     {:crossorigin "anonymous",
      :integrity
      "sha384-ggOyR0iXCbMQv3Xipma34MD+dH/1fQ784/j6cY/iJTQUOhcWr7x9JvoRxT2MZw1T",
      :href
      "https://stackpath.bootstrapcdn.com/bootstrap/4.3.1/css/bootstrap.min.css",
      :rel "stylesheet"}]]
   [:body {:style {:font-family "'Helvetica Neue', Helvetica, Arial, sans-serif"}}
    [:.d-flex.flex-column.align-items-center.justify-content-center
     {:style {:height "70vh"}}
     [:p (or message "Sign in as admin")]
     [:form {:method "post"}
      [:input#__anti-forgery-token
       {:name "__anti-forgery-token"
        :type "hidden"
        :value (force anti-forgery/*anti-forgery-token*)}]
      [:.form-row
       [:.col-12.col-sm-9.mb-2.mb-sm-0
        [:input.form-control {:name "password"
                              :type "password"
                              :placeholder "Password"}]]
       [:.col-12.col-sm-3
        [:button.btn.btn-primary.btn-block
         (merge
           {:type "submit"}
           (unsafe "Sign&nbsp;in"))]]]]]]])

(defn render-login [msg]
  (rum/render-static-markup (login-html msg)))

(defn login [{:keys [session params]}]
  (let [next-url (:next params)
        password (:password params)
        correct (->> "deps.edn"
                  slurp
                  edn/read-string
                  :nimbus/config
                  ::password-hash
                  (pw/check password))]
    (if correct
      {:status 302
       :headers {"Location" (or next-url "/pack")}
       :cookie {"csrf" {:value (force anti-forgery/*anti-forgery-token*)}}
       :session (assoc session :admin true)
       :body ""}
      {:headers {"Content-Type" "text/html"}
       :body (render-login "Incorrect password.")})))

(defn login-page [req]
  {:body (render-login nil)
   :headers {"Content-Type" "text/html"}})

(def config
  {:nimbus.comms/route
   [""
    ["/nimbus/auth" {:post login
                     :get login-page
                     :name ::login
                     :middleware [[wrap-defaults
                                   (assoc-in site-defaults
                                     [:session :store] (memory-store))]]}]]})
