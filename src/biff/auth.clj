(ns biff.auth
  (:require
    [crux.api :as crux]
    [ring.middleware.anti-forgery :as anti-forgery]
    [biff.crux :as bcrux]
    [byte-streams :as bs]
    [crypto.random :as random]
    [trident.util :as u]
    [trident.jwt :as tjwt]
    [clojure.string :as str]
    [cemerick.url :as url]
    [byte-transforms :as bt]))

(defn get-key [{:keys [biff/node biff/db k] :as env}]
  (or (get (crux/entity db :biff.auth/keys) k)
    (doto (bs/to-string (bt/encode (random/bytes 16) :base64))
      (#(bcrux/submit-admin-tx
          env
          {[:biff/auth-keys :biff.auth/keys]
           {:db/merge true
            k %}})))))

(defn jwt-key [env]
  (get-key (assoc env :k :jwt-key)))

(defn signin-token [jwt-key claims]
  (tjwt/encode
    (merge
      claims
      {:iss "biff"
       :iat (u/now)
       :exp (u/add-seconds (u/now) (* 60 30))})
    {:secret jwt-key
     :alg :HS256}))

(defn signin-link [{:keys [claims url] :as env}]
  (let [jwt (signin-token (jwt-key env) (update claims :email str/trim))]
    (-> url
      url/url
      (assoc :query {:token jwt})
      str)))

(defn email= [s1 s2]
  (.equalsIgnoreCase s1 s2))

(defn get-uid [{:keys [biff/node biff/db email]}]
  (or (:user/id
        (ffirst
          (crux/q db
            {:find '[e]
             :args [{'input-email email}]
             :where '[[e :user/email email]
                      [(biff.auth/email= email input-email)]]})))
    (doto (java.util.UUID/randomUUID)
      ; todo set account created date
      (#(crux/submit-tx
          node
          [[:crux.tx/put
            {:crux.db/id {:user/id %}
             :user/id %
             :user/email email}]])))))

(defn send-signin-link [{:keys [params params/email biff.auth/send-email biff/base-url template location]
                         :as env}]
  (let [link (signin-link (assoc env
                            :claims params
                            :url (str base-url "/api/signin")))]
    (send-email {:to email
                 :template template
                 :data {:biff.auth/link link}})
    (get-uid (assoc env :email email))
    {:status 302
     :headers/Location location}))

(defn signin [{:keys [params/token session]
               :biff.auth/keys [on-signin on-signin-fail]
               :as env}]
  (if-some [claims (-> token
                     (tjwt/decode {:secret (jwt-key env)
                                   :alg :HS256})
                    u/catchall)]
    (let [uid (get-uid (merge env (select-keys claims [:email])))
          claims (not-empty (dissoc claims :email :iss :iat :exp))]
      (bcrux/submit-admin-tx
        env
        {[:users {:user/id uid}]
         (u/assoc-some
           {:db/update true
            :last-signed-in (u/now)
            :claims claims})})
      ; todo set last signin date
      {:status 302
       :headers/Location on-signin
       :cookies/csrf {:path "/"
                      :max-age (* 60 60 24 90)
                      :value (force anti-forgery/*anti-forgery-token*)}
       :session (assoc session :uid uid)})
    {:status 302
     :headers/Location on-signin-fail}))

(defn signout [{:keys [biff.auth/on-signout]}]
  {:status 302
   :headers/Location on-signout
   :cookies/ring-session {:value "" :max-age 0}
   :cookies/csrf {:value "" :max-age 0}
   :session nil})

(defn signed-in? [req]
  {:status (if (-> req :session/uid some?)
             200
             403)})

(defn route [{:biff.auth/keys [on-signup on-signin-request] :as sys}]
  ["/api"
   ["/signup" {:post #(send-signin-link (assoc %
                                          :template :biff.auth/signup
                                          :location (or on-signup on-signin-request)))
               :name ::signup}]
   ["/signin-request" {:post #(send-signin-link (assoc %
                                                  :template :biff.auth/signin
                                                  :location on-signin-request))
                       :name ::signin-request}]
   ["/signin" {:get signin
               :name ::signin
               :middleware [anti-forgery/wrap-anti-forgery]}]
   ["/signout" {:get signout
                :name ::signout}]
   ["/signed-in" {:get signed-in?
                  :name ::signed-in}]])
