(ns com.example.feat.auth
  (:require [com.biffweb :as biff]
            [com.example.ui :as ui]
            [com.example.util :as util]
            [clj-http.client :as http]
            [rum.core :as rum]
            [xtdb.api :as xt]))

(defn human? [{:keys [recaptcha/secret-key params]}]
  (let [{:keys [success score]}
        (:body
         (http/post "https://www.google.com/recaptcha/api/siteverify"
                    {:form-params {:secret secret-key
                                   :response (:g-recaptcha-response params)}
                     :as :json}))]
    (and success (or (nil? score) (<= 0.5 score)))))

;; For extra protection, you can call out to an email verification API here to
;; block spammy/high risk addresses.
(defn email-valid? [req email]
  (boolean (some->> email (re-matches #".+@.+\..+"))))

(defn signin-template [{:keys [to url]}]
  {:to [{:email to}]
   :subject "Sign in to My Application"
   :html (rum/render-static-markup
          [:div
           [:p "We received a request to sign in to My Application using this email address."]
           [:p [:a {:href url :target "_blank"} "Click here to sign in."]]
           [:p "If you did not request this link, you can ignore this email."]])})

(defn send-link! [req email url]
  (and (human? req)
       (email-valid? req email)
       (biff/mailersend
        req
        (signin-template {:to email :url url}))))

(defn send-token [{:keys [biff/base-url
                          biff/jwt-secret
                          anti-forgery-token
                          params]
                   :as req}]
  (let [email (biff/normalize-email (:email params))
        token (biff/jwt-encrypt
               {:intent "signin"
                :email email
                :state (biff/sha256 anti-forgery-token)
                :exp-in (* 60 60)}
               jwt-secret)
        url (str base-url "/auth/verify/" token)]
    (if-not (util/email-signin-enabled? req)
      (do
        (println (str "Click here to sign in as " email ": " url))
        {:headers {"location" "/auth/printed/"}
         :status 303})
      {:status 303
       :headers {"location" (if (send-link! req email url)
                              "/auth/sent/"
                              "/auth/fail/")}})))

(defn verify-token [{:keys [biff.xtdb/node
                            biff/jwt-secret
                            path-params
                            session
                            anti-forgery-token] :as req}]
  (let [{:keys [intent email state]} (biff/jwt-decrypt (:token path-params) jwt-secret)
        success (and (= intent "signin")
                     (= state (biff/sha256 anti-forgery-token)))
        get-user-id #(biff/lookup-id (xt/db node) :user/email email)
        existing-user-id (when success (get-user-id))]
    (when (and success (not existing-user-id))
      (biff/submit-tx req
        [{:db/op :merge
          :db/doc-type :user
          :xt/id [:db/lookup {:user/email email}]}]))
    (if-not success
      {:status 303
       :headers {"location" "/auth/fail/"}}
      {:status 303
       :headers {"location" "/app"}
       :session (assoc session :uid (or existing-user-id (get-user-id)))})))

(defn signout [{:keys [session]}]
  {:status 303
   :headers {"location" "/"}
   :session (dissoc session :uid)})

(def signin-printed
  (ui/page
   {}
   [:div
    "The sign-in link was printed to the console. If you add API "
    "keys for MailerSend and reCAPTCHA, the link will be emailed to you instead."]))

(def signin-sent
  (ui/page
   {}
   [:div "We've sent a sign-in link to your email address. Please check your inbox."]))

(def signin-fail
  (ui/page
   {}
   [:div
    "Your sign-in request failed. There are several possible reasons:"]
   [:ul
    [:li "You failed the reCAPTCHA test."]
    [:li "We think your email address is invalid or high risk."]
    [:li "We tried to email the link to you, but there was an unexpected error."]
    [:li "You opened the sign-in link on a different device or browser than the one you requested it on."]]))

(def features
  {:routes [["/auth/send"          {:post send-token}]
            ["/auth/verify/:token" {:get verify-token}]
            ["/auth/signout"       {:post signout}]]
   :static {"/auth/printed/" signin-printed
            "/auth/sent/" signin-sent
            "/auth/fail/" signin-fail}})
