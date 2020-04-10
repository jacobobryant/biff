(ns ^:biff biff.auth
  (:require
    [ring.middleware.anti-forgery :as anti-forgery]
    [biff.util :as util]
    [crypto.password.bcrypt :as pw]
    [rum.core :as rum :refer [defc]]))

(defc login-page [{:keys [logged-in password-incorrect]}]
  [:html util/html-opts
   (util/head {:title "Login to Biff"})
   [:body util/body-opts
    (util/navbar)
    [:.d-flex.flex-column.align-items-center.justify-content-center
     {:style {:height "70vh"}}
     (if logged-in
       [:form {:action "/biff/auth/logout" :method "post"}
        (util/csrf)
        [:p.text-center "Signed in as admin."]
        [:button.btn.btn-secondary.btn-block
         (util/unsafe {:type "submit"} "Sign&nbsp;out")]]
       [:form {:method "post"}
        (util/csrf)
        (if password-incorrect
          [:p.text-danger.text-center "Incorrect password."]
          [:p.text-center "Sign in as admin."])
        [:input.form-control {:name "password"
                              :autofocus true
                              :type "password"
                              :placeholder "Password"}]
        [:.mb-3]
        [:button.btn.btn-primary.btn-block
         (util/unsafe {:type "submit"} "Sign&nbsp;in")]])]]])

(defn serve-login-page [req]
  (util/render login-page
    {:logged-in (-> req :session :admin)}))

(defn login [{:keys [session params] :as req}]
  (let [next-url (:next params)
        password (:password params)
        correct (->> (util/deps)
                  :biff/config
                  ::password-hash
                  (pw/check password))]
    (if correct
      {:status 302
       :headers {"Location" (or next-url "/")}
       :cookies {"csrf" {:value (force anti-forgery/*anti-forgery-token*)}}
       :session (assoc session :admin true)
       :body ""}
      (util/render login-page
        {:logged-in (:admin session)
         :password-incorrect true}))))

(defn logout [req]
  {:status 302
   :headers {"Location" "/biff/auth"}
   :cookies {"ring-session" {:value "" :max-age 0}}
   :session nil
   :body ""})

(defc change-password-page [{:keys [success]}]
  [:html util/html-opts
   (util/head {:title "Change password | Biff"})
   [:body util/body-opts
    (util/navbar)
    [:.container-fluid.mt-3
     [:.d-flex.flex-column.align-items-center
      [:div
       (case success
         true [:p.text-success "Password changed."]
         false [:p.text-danger "Invalid input."]
         nil)
       [:form {:method "post"}
        (util/csrf)
        [:.form-group.mb-2
         [:label.mb-0 {:for "password"} "Current password:"]
         [:input#password.form-control {:name "password"
                                        :type "password"
                                        :autofocus true}]]
        [:.form-group.mb-2
         [:label.mb-0 {:for "newpassword"} "New password:"]
         [:input#newpassword.form-control {:name "newpassword" :type "password"}]]
        [:.form-group.mb-2
         [:label.mb-0 {:for "confirmpassword"} "Confirm password:"]
         [:input#confirmpassword.form-control {:name "confirmpassword" :type "password"}]]
        [:.mb-3]
        [:button.btn.btn-primary.btn-block {:type "submit"} "Change password"]]]]]]])

(defn change-password [{{:keys [password newpassword confirmpassword]} :params}]
  (let [success (and (->> (util/deps)
                       :biff/config
                       ::password-hash
                       (pw/check password))
                  (= newpassword confirmpassword)
                  (not-empty newpassword))]
    (when success
      (util/update-deps! assoc-in [:biff/config ::password-hash]
        (pw/encrypt newpassword)))
    (util/render change-password-page
      {:success (boolean success)})))

(def config
  {:biff.http/route
   ["/biff/auth" {:middleware [anti-forgery/wrap-anti-forgery]}
    ["" {:post login
         :get serve-login-page
         :name ::login}]
    ["/logout" {:post logout
                :name ::logout}]
    ["/change-password" {:get #(util/render change-password-page %)
                         :post change-password
                         :name ::change-password}]]})
