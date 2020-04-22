(ns biff.util.system
  (:require
    [biff.util :as bu]
    [biff.util.crux :as bu-crux]
    [biff.util.http :as bu-http]
    [byte-streams :as bs]
    [cemerick.url :as url]
    [crypto.random :as random]
    [byte-transforms :as bt]
    [biff.util.static :as bu-static]
    [biff.core :as core]
    [clojure.java.io :as io]
    [clojure.walk :as walk]
    [crux.api :as crux]
    [clojure.set :as set]
    [clojure.string :as str]
    [ring.middleware.anti-forgery :as anti-forgery]
    [trident.util :as u]))

(defn write-static-resources [{:keys [static-pages static-root app-namespace]}]
  (bu-static/export-rum static-pages static-root)
  (bu-static/copy-resources (str "www/" app-namespace) static-root))

(defn start-crux [{:keys [storage-dir subscriptions triggers]}]
  (let [node (bu-crux/start-node {:storage-dir storage-dir})
        _ (crux/sync node)
        last-tx-id (-> (bu-crux/tx-log {:node node}) ; Better way to do this?
                     last
                     :crux.tx/tx-id
                     atom)]
    (->
      (bu/pipe-fn
        (fn [opts]
          (update opts :tx #(crux/submit-tx node %)))
        #(bu/fix-stdout
           (bu-crux/notify-tx
             (assoc %
               :node node
               :subscriptions subscriptions
               :triggers triggers
               :last-tx-id last-tx-id))))
      (set/rename-keys {:f :submit-tx :close :close-tx-pipe})
      (assoc :node node))))

(defn wrap-env [handler {:keys [node] :as env}]
  (comp handler #(-> %
                   (merge env)
                   (assoc :db (crux/db node)))))

(defn make-config [{:keys [app-namespace] :as config}]
  (let [c (bu/with-defaults config
            :debug core/debug
            :biff-config core/config
            :secrets (bu/secrets)
            :crux-dir (str "data/" app-namespace "/crux-db")
            :cookie-key-path (str "data/" app-namespace "/cookie-key"))
        host (bu/ns->host (:biff-config c) app-namespace)]
    (bu/with-defaults c
      :url-base (if (:debug c)
                  (str "http://localhost:" (:biff.http/port (:biff-config c) bu-http/default-port))
                  (str "https://" host))
      :static-root (str "www/" host))))

;;;; auth

(defn get-key [{:keys [node db k]}]
  (or (get (crux/entity db :biff.auth/keys) k)
    (doto (bs/to-string (bt/encode (random/bytes 16) :base64))
      (#(crux/submit-tx
          node
          [[:crux.tx/put
            {:crux.db/id :biff.auth/keys
             k %}]])))))

(defn signin-token [jwt-key email]
  (bu/encode-jwt
    {:iss "biff"
     :iat (u/now)
     :exp (u/add-seconds (u/now) (* 60 30))
     :email email}
    {:secret jwt-key
     :alg :HS256}))

(defn signin-link [{:keys [email url] :as env}]
  (let [email (str/trim email)
        jwt-key (get-key (assoc env :k :jwt-key))
        jwt (signin-token jwt-key email)]
    (-> url
      url/url
      (assoc :query {:token jwt})
      str)))

(defn send-signin-link [{:keys [send-email url-base template location]
                         {:keys [email]} :params
                         :as env}]
  (let [link (signin-link (assoc env
                            :email email
                            :url (str url-base "/api/signin")))]
    (send-email {:to email
                 :template template
                 :data {:biff.auth/link link}})
    {:status 302
     :headers/Location location}))

(defn email= [s1 s2]
  (.equalsIgnoreCase s1 s2))

(defn get-uid [{:keys [node db email]}]
  (or (ffirst
        (crux/q db
          {:find '[e]
           :args [{'input-email email}]
           :where '[[e :email email]
                    [(biff.util.system/email= email input-email)]]}))
    (doto (java.util.UUID/randomUUID)
      (#(crux/submit-tx
          node
          [[:crux.tx/put
            {:crux.db/id {:user/id %}
             :user/id %
             :user/email email}]])))))

(defn signin [{:keys [session on-signin on-signin-fail]
               {:keys [token]} :params
               :as env}]
  (if-some [email (-> token
                    (bu/decode-jwt {:secret (get-key (assoc env :k :jwt-key))
                                    :alg :HS256})
                    u/catchall
                    :email)]
    (let [uid (get-uid (assoc env :email email))]
      {:status 302
       :headers/Location on-signin
       :cookies/csrf {:path "/"
                      :value (force anti-forgery/*anti-forgery-token*)}
       :session (assoc session :uid uid)})
    {:status 302
     :headers/Location on-signin-fail}))

(defn signout [{:keys [on-signout]}]
  {:status 302
   :headers/Location on-signout
   :cookies/ring-session {:value "" :max-age 0}
   :cookies/csrf {:value "" :max-age 0}
   :session nil})

(defn signed-in? [req]
  {:status (if (-> req :session :uid some?)
             200
             403)})

(defn auth-route [{:keys [on-signup on-signin-request] :as config}]
  ["/api" {:middleware [[comp #(merge % config)]]}
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

(defn start-biff [config]
  (let [{:keys [debug
                static-pages
                app-namespace
                event-handler
                route
                secrets
                rules
                crux-dir
                triggers
                static-root
                url-base
                cookie-key-path] :as config} (make-config config)
        subscriptions (atom {})
        {:keys [submit-tx
                close-tx-pipe
                node]} (start-crux {:storage-dir crux-dir
                                    :subscriptions subscriptions
                                    :triggers triggers})
        env {:subscriptions subscriptions
             :debug debug
             :node node
             :rules rules
             :url-base url-base
             :submit-tx submit-tx}
        {:keys [reitit-route
                close-router
                api-send
                connected-uids]} (bu-http/init-sente
                                   {:route-name ::chsk
                                    :handler (wrap-env event-handler env)})
        routes [["" {:middleware [[wrap-env env]]}
                 reitit-route
                 (auth-route config)
                 route]]
        handler (bu-http/make-handler
                  {:root static-root
                   :debug debug
                   :routes routes
                   :cookie-path cookie-key-path})]
    (when (not-empty static-pages)
      (write-static-resources config))
    {:handler handler
     :node node
     :subscriptions subscriptions
     :submit-tx submit-tx
     :connected-uids connected-uids
     :api-send api-send
     :close (fn []
              (close-router)
              (close-tx-pipe)
              (.close node))}))
