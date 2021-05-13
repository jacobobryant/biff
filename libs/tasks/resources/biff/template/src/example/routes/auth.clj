(ns {{parent-ns}}.routes.auth
  (:require [{{parent-ns}}.templates :as templates]
            [biff.crux :as bcrux]
            [biff.misc :as misc]
            [clj-http.client :as http]
            [clojure.string :as str]
            [crux.api :as crux]
            [ring.middleware.anti-forgery :as anti-forgery]))

(defn wrap-authentication [handler]
  (anti-forgery/wrap-anti-forgery
    (fn [{:keys [session/uid] :as req}]
      (handler (assoc req :biff/uid uid)))
    ; If there isn't a CSRF token, still call the handler, but don't set
    ; :biff/uid. Then you can do unauthenticated POSTs from static pages (e.g.
    ; a sign-in form).
    {:error-handler handler}))

; You should take care of this before publicizing your site, especially if your
; sign-in form is not rendered with JS. Otherwise your deliverability will go
; down, and you'll be spamming innocent people. If you want to use recaptcha
; v3, add :recaptcha/secret-key to config/main.edn. To add recaptcha on the
; front-end, see https://developers.google.com/recaptcha/docs/v3.
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

(defn send-token [{:keys [params/email
                          biff/base-url
                          mailgun/api-key
                          biff.auth/jwt-secret]
                   :as sys}]
  (let [email (some-> email str/trim str/lower-case not-empty)
        token (misc/jwt-encrypt
                {:email email
                 :exp-in (* 60 60 24 3)}
                jwt-secret)
        url (misc/assoc-url (str base-url "/api/verify-token") :token token)
        human (human? sys)
        mock-send (and (not api-key) email human)
        send-success (when (and api-key email human)
                       (misc/send-mailgun
                         sys
                         (templates/signin {:to email :url url})))]
    (when mock-send
      (println (str "Click here to sign in as " email ": " url)))
    {:status 302
     :headers/Location (if (or send-success mock-send)
                         "/signin-sent/"
                         "/signin-fail/")}))

(defn verify-token [{:keys [biff.crux/node
                            biff.crux/db
                            biff.middleware/secure-cookies
                            params/token
                            session
                            biff.auth/jwt-secret] :as sys}]
  (if-some [{:keys [email]} (misc/jwt-decrypt token jwt-secret)]
    (let [existing-uid (ffirst
                         (crux/q @db
                           '{:find [user]
                             :in [email]
                             :where [[user :user/email email]]}
                           email))
          new-uid (java.util.UUID/randomUUID)]
      (when-not existing-uid
        (bcrux/submit-tx
          sys
          {[:user new-uid] {:user/email email}}))
      {:status 302
       :headers/Location "/"
       ; On server-rendered pages, the csrf token will be embedded in the html.
       ; But this is handy for static pages (retrieve the token with JS and
       ; include it in the request headers).
       :cookies/csrf {:max-age (* 60 60 24 90)
                      :same-site :lax
                      :value (force anti-forgery/*anti-forgery-token*)}
       :session (assoc session :uid (or existing-uid new-uid))})
    {:status 302
     :headers/Location "/signin-fail/"}))

(defn signout [_]
  {:status 302
   :headers/Location "/"
   :cookies/ring-session {:value "" :max-age 0}
   :cookies/csrf {:value "" :max-age 0}
   :session nil})

(defn status [{:keys [biff/uid]}]
  {:status 200
   :body {:signed-in (some? uid)}})

(defn routes []
  [["/api/send-token"   {:post send-token}]
   ["/api/verify-token" {:get verify-token}]
   ["/api/signout"      {:get signout}]
   ["/api/status"       {:get status}]])
