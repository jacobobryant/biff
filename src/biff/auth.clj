(ns ^:biff biff.auth
  (:require
    [ring.middleware.anti-forgery :as anti-forgery]
    [biff.util :as bu]
    [biff.util.static :as bu-static]
    [crypto.password.bcrypt :as pw]
    [rum.core :as rum :refer [defc]]))

(defc login-page [{:keys [logged-in password-incorrect]}]
  [:html bu-static/html-opts
   (bu-static/head {:title "Login to Biff"})
   [:body bu-static/body-opts
    (bu-static/navbar)
    [:.d-flex.flex-column.align-items-center.justify-content-center
     {:style {:height "70vh"}}
     (if logged-in
       [:form {:action "/biff/auth/logout" :method "post"}
        (bu-static/csrf)
        [:p.text-center "Signed in as admin."]
        [:button.btn.btn-secondary.btn-block
         (bu-static/unsafe {:type "submit"} "Sign&nbsp;out")]]
       [:form {:method "post"}
        (bu-static/csrf)
        (if password-incorrect
          [:p.text-danger.text-center "Incorrect password."]
          [:p.text-center "Sign in as admin."])
        [:input.form-control {:name "password"
                              :autofocus true
                              :type "password"
                              :placeholder "Password"}]
        [:.mb-3]
        [:button.btn.btn-primary.btn-block
         (bu-static/unsafe {:type "submit"} "Sign&nbsp;in")]])]]])

(defn serve-login-page [req]
  (bu-static/render login-page
    {:logged-in (-> req :session :admin)}))

(defn login [{:keys [session params] :as req}]
  (let [next-url (:next params)
        password (:password params)
        correct (->> (bu/deps)
                  :biff/config
                  ::password-hash
                  (pw/check password))]
    (if correct
      {:status 302
       :headers {"Location" (or next-url "/")}
       :cookies {"csrf" {:value (force anti-forgery/*anti-forgery-token*)}}
       :session (assoc session :admin true)
       :body ""}
      (bu-static/render login-page
        {:logged-in (:admin session)
         :password-incorrect true}))))

(defn logout [req]
  {:status 302
   :headers {"Location" "/biff/auth"}
   :cookies {"ring-session" {:value "" :max-age 0}}
   :session nil
   :body ""})

(defc change-password-page [{:keys [success]}]
  [:html bu-static/html-opts
   (bu-static/head {:title "Change password | Biff"})
   [:body bu-static/body-opts
    (bu-static/navbar)
    [:.container-fluid.mt-3
     [:.d-flex.flex-column.align-items-center
      [:div
       (case success
         true [:p.text-success "Password changed."]
         false [:p.text-danger "Invalid input."]
         nil)
       [:form {:method "post"}
        (bu-static/csrf)
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
  (let [success (and (->> (bu/deps)
                       :biff/config
                       ::password-hash
                       (pw/check password))
                  (= newpassword confirmpassword)
                  (not-empty newpassword))]
    (when success
      (bu/update-deps! assoc-in [:biff/config ::password-hash]
        (pw/encrypt newpassword)))
    (bu-static/render change-password-page
      {:success (boolean success)})))

(def config
  {:biff.http/route
   ["/biff/auth" {:middleware [anti-forgery/wrap-anti-forgery]}
    ["" {:post login
         :get serve-login-page
         :name ::login}]
    ["/logout" {:post logout
                :name ::logout}]
    ["/change-password" {:get #(bu-static/render change-password-page %)
                         :post change-password
                         :name ::change-password}]]})
