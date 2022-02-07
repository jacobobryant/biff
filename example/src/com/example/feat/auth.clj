(ns com.example.feat.auth
  (:require [better-cond.core :as b]
            [com.biffweb :as biff]
            [com.example.ui :as ui]
            [clj-http.client :as http]
            [rum.core :as rum]
            [xtdb.api :as xt]))

;; TODO add comments about how to finish implementing these.

;; You can enable recaptcha to prevent bot signups.
(defn human? [{:keys [recaptcha/secret-key params]}]
  (if-not secret-key
    true
    (let [{:keys [success score]}
          (:body
            (http/post "https://www.google.com/recaptcha/api/siteverify"
                       {:form-params {:secret secret-key
                                      :response (:g-recaptcha-response params)}
                        :as :json}))]
      (and success (<= 0.5 score)))))

;; You can call out to an email verification API here to block spammy/high risk
;; addresses.
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

(b/defnc send-token [{:keys [biff/base-url
                             mailersend/api-key
                             biff/jwt-secret
                             anti-forgery-token
                             params] :as req}]
  (nil? anti-forgery-token) {:status 303
                             :headers {"location" "/auth/fail/"}}
  :let [email (biff/normalize-email (:email params))
        token (biff/jwt-encrypt
                {:intent "signin"
                 :email email
                 :state (biff/sha256 anti-forgery-token)
                 :exp-in (* 60 60)}
                jwt-secret)
        url (str base-url "/auth/verify/" token)]
  (nil? api-key) (do
                   (println (str "Click here to sign in as " email ": " url))
                   {:headers {"location" "/auth/sent/"}
                    :status 303})
  :let [success (and (human? req)
                     (email-valid? req email)
                     (biff/mailersend
                       req
                       (signin-template {:to email :url url})))]
  {:status 303
   :headers {"location" (if success
                          "/auth/sent/"
                          "/auth/fail/")}})

(b/defnc verify-token [{:keys [biff/db
                               biff.xtdb/node
                               biff/jwt-secret
                               path-params
                               session
                               anti-forgery-token] :as req}]
  :let [{:keys [intent
                email
                state]} (biff/jwt-decrypt (:token path-params) jwt-secret)]
  (or (not= intent "signin")
      (not= state (biff/sha256 anti-forgery-token))) {:status 303
                                                      :headers {"location" "/auth/fail/"}}
  :do (biff/submit-tx req
        [{:db/op :merge
          :db/doc-type :user
          :xt/id [:db/lookup {:user/email email}]}])
  :let [user-id (biff/lookup-id (xt/db node) :user/email email)]
  {:status 303
   :headers {"location" "/app"}
   :session (assoc session :uid user-id)})

(defn signout [{:keys [biff/uid]}]
  (if uid
    {:status 303
     :headers {"location" "/"}
     :session nil}
    {:status 303
     :headers {"location" "/"}}))

(def signin-sent
  (ui/page
    {}
    nil
    [:div
     "The sign-in link was printed to the console. If you add an API "
     "key for MailerSend, the link will be emailed to you instead."]))

(def signin-fail
  (ui/page
    {}
    nil
    [:div
     "Your sign-in request failed. There are several possible reasons:"]
    [:ul
     [:li "You opened the sign-in link on a different device or browser than the one you requested it on."]
     [:li "We're not sure you're a human."]
     [:li "We think your email address is invalid or high risk."]
     [:li "We tried to email the link to you, but it didn't work."]]))

(def features
  {:routes [["/auth/send"          {:post send-token}]
            ["/auth/verify/:token" {:get verify-token}]
            ["/auth/signout"       {:post signout}]]
   :static {"/auth/sent/" signin-sent
            "/auth/fail/" signin-fail}})
