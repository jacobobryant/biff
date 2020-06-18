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

(defn send-signin-link [{:keys [params params/email biff/base-url template location]
                         :biff.auth/keys [send-email]
                         :as env}]
  (let [link (signin-link (assoc env
                            :claims params
                            :url (str base-url "/api/signin")))]
    (send-email (merge env
                  {:to email
                   :template template
                   :data {:biff.auth/link link}})))
  {:status 302
   :headers/Location location})

(defn signin [{:keys [params/token session biff/db biff/node]
               :biff.auth/keys [on-signin on-signin-fail]
               :as env}]
  (if-some [{:keys [email] :as claims}
            (-> token
              (tjwt/decode {:secret (jwt-key env)
                            :alg :HS256})
              u/catchall)]
    (let [new-user-ref {:user/id (java.util.UUID/randomUUID)}
          user (merge
                 {:crux.db/id new-user-ref
                  :user/email email}
                 new-user-ref
                 (ffirst
                   (crux/q db
                     {:find '[e]
                      :args [{'input-email email}]
                      :where '[[e :user/email email]
                               [(biff.auth/email= email input-email)]]
                      :full-results? true}))
                 (u/assoc-some
                   {:last-signed-in (u/now)}
                   :claims (not-empty (dissoc claims :email :iss :iat :exp))))]
      (crux/submit-tx node [[:crux.tx/put user]])
      {:status 302
       :headers/Location on-signin
       :cookies/csrf {:path "/"
                      :max-age (* 60 60 24 90)
                      :value (force anti-forgery/*anti-forgery-token*)}
       :session (assoc session :uid (:user/id user))})
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
