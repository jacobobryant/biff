(ns ^:nimbus nimbus.auth
  (:require
    [clojure.edn :as edn]
    [ring.middleware.anti-forgery :as anti-forgery]
    [ring.util.response :refer [redirect]]
    [trident.util :as u]
    [crypto.password.bcrypt :as pw]
    [rum.core :as rum :refer [defc]]))

(defn unsafe [html]
  {:dangerouslySetInnerHTML {:__html html}})

(defc login-html [{:keys [logged-in password-incorrect]}]
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
     (if logged-in
       [:form {:action "/nimbus/auth/logout" :method "post"}
        [:input#__anti-forgery-token
         {:name "__anti-forgery-token"
          :type "hidden"
          :value (force anti-forgery/*anti-forgery-token*)}]
        [:p.text-center "Signed in as admin."]
        [:button.btn.btn-secondary.btn-block
         (merge
           {:type "submit"}
           (unsafe "Sign&nbsp;out"))]]
       [:form {:method "post"}
        [:input#__anti-forgery-token
         {:name "__anti-forgery-token"
          :type "hidden"
          :value (force anti-forgery/*anti-forgery-token*)}]
        [:p.text-center (if password-incorrect
                          "Incorrect password."
                          "Sign in as admin.")]
        [:.form-row
         [:.col-12.col-sm-9.mb-2.mb-sm-0
          [:input.form-control {:name "password"
                                :type "password"
                                :placeholder "Password"}]]
         [:.col-12.col-sm-3
          [:button.btn.btn-primary.btn-block
           (merge
             {:type "submit"}
             (unsafe "Sign&nbsp;in"))]]]])]]])

(defn render-login [opts]
  (rum/render-static-markup (login-html opts)))

(defn login [{:keys [session params] :as req}]
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
       :headers {"Location" (or next-url "/nimbus/auth")}
       :cookies {"csrf" {:value (force anti-forgery/*anti-forgery-token*)}}
       :session (assoc session :admin true)
       :body ""}
      {:headers {"Content-Type" "text/html"}
       :body (render-login {:logged-in (:admin session)
                            :password-incorrect true})})))

(defn login-page [req]
  {:body (render-login {:logged-in (-> req :session :admin)})
   :headers {"Content-Type" "text/html"}})

(defn logout [req]
  {:status 302
   :headers {"Location" "/nimbus/auth"}
   :cookies {"ring-session" {:value "" :max-age 0}}
   :session nil
   :body ""})

(def config
  {:nimbus.comms/route
   [""
    ["/nimbus/auth" {:post login
                     :get login-page
                     :name ::login}]
    ["/nimbus/auth/logout" {:post logout
                            :name ::logout}]]})
