(ns biff.util.system)
;(ns biff.util.system
;  (:require
;    [biff.util :as bu]
;    [biff.util.crux :as bu-crux]
;    [biff.util.http :as bu-http]
;    [byte-streams :as bs]
;    [clojure.core.async :refer [<!!]]
;    [clojure.spec.alpha :as s]
;    [cemerick.url :as url]
;    [crypto.random :as random]
;    [byte-transforms :as bt]
;    [biff.util.static :as bu-static]
;    [biff.core :as core]
;    [clojure.java.io :as io]
;    [clojure.walk :as walk]
;    [crux.api :as crux]
;    [clojure.set :as set]
;    [clojure.string :as str]
;    [ring.middleware.session.cookie :as cookie]
;    [ring.middleware.anti-forgery :as anti-forgery]
;    [trident.util :as u]))
;
;;(defn write-static-resources [{:keys [static-pages static-resource-root static-root]}]
;;  (bu-static/export-rum static-pages static-root)
;;  (bu-static/copy-resources static-resource-root static-root))
;
;;(defn start-crux [{:keys [subscriptions triggers crux-config] :as config}]
;;  (let [node (bu-crux/start-node crux-config)
;;        _ (crux/sync node)
;;        last-tx-id (-> (bu-crux/tx-log {:node node}) ; Better way to do this?
;;                     last
;;                     :crux.tx/tx-id
;;                     atom)
;;        config (assoc config :node node :last-tx-id last-tx-id)]
;;    (->
;;      (bu/pipe-fn
;;        (fn [opts]
;;          (update opts :tx #(crux/submit-tx node %)))
;;        #(bu/fix-stdout
;;           (bu-crux/notify-tx (merge % config))))
;;      (set/rename-keys {:f :submit-tx :close :close-tx-pipe})
;;      (merge config))))
;
;
;(defn wrap-sub [handler]
;  (fn [{:keys [event-id id api-send client-id uid] {:keys [query action]} :?data :as env}]
;    (if (not= :biff/sub event-id)
;      (handler env)
;      (let [result (cond
;                     (= [query action] [:uid :subscribe])
;                     (api-send client-id
;                       [:biff/sub {:changeset {[:uid nil]
;                                               {:uid (or uid :signed-out)}}
;                                   :query query}])
;
;                     (= action :subscribe) (bu-crux/crux-subscribe! env query)
;                     (= action :unsubscribe) (bu-crux/crux-unsubscribe! env query)
;                     :default (bu/anom :incorrect "Invalid action." :action action))]
;        (when (bu/anomaly? result)
;          result)))))
;
;(defn wrap-tx [handler]
;  (fn [{:keys [event-id api-send client-id submit-tx] :as env}]
;    (if (not= event-id :biff/tx)
;      (handler env)
;      (let [tx (bu-crux/authorize-tx
;                 (set/rename-keys env
;                   {:uid :auth-uid :?data :tx}))]
;        (if (bu/anomaly? tx)
;          tx
;          (<!! (submit-tx (assoc env :tx tx))))))))
;
;(defn wrap-env [handler {:keys [node] :as env}]
;  (comp handler #(-> %
;                   (merge env)
;                   (assoc :db (crux/db node)))))
;
;(defn start-sente [{:keys [event-handler] :as config}]
;  (merge config
;    (bu-http/init-sente
;      {:route-name ::chsk
;       :handler (-> event-handler
;                  wrap-sub
;                  wrap-tx
;                  (wrap-env config))})))
;
;(defn start-session-store [{{:keys [cookie-key-path]} :route-config :as config}]
;  (assoc :session-store (cookie/cookie-store {:key (bu/cookie-key cookie-key-path)})))
;
;(defn derive-config [{:keys [biff.http/host app-namespace] {:keys [port]} :http-config :as config}]
;  (-> {:base-url (if (= "localhost" host)
;                   (str "http://localhost" port)
;                   (str "https://" host))}
;    (merge config)
;    (update :crux-config #(merge {:storage-dir (str "data/" app-namespace "/crux-db")} %))
;    (update :route-config #(merge {:cookie-key-path (str "data/" app-namespace "/cookie-key")}))
;    (update :static-resource-config #(merge {:static-root (str "www/" host)
;                                             :static-resource-root (str "www/" app-namespace)} %))
;    (assoc :subscriptions (atom #{}))))
;
;;;;; auth
;
;(defn get-key [{:keys [node db k]}]
;  (or (get (crux/entity db :biff.auth/keys) k)
;    (doto (bs/to-string (bt/encode (random/bytes 16) :base64))
;      (#(crux/submit-tx
;          node
;          [[:crux.tx/put
;            {:crux.db/id :biff.auth/keys
;             k %}]])))))
;
;(defn signin-token [jwt-key email]
;  (bu/encode-jwt
;    {:iss "biff"
;     :iat (u/now)
;     :exp (u/add-seconds (u/now) (* 60 30))
;     :email email}
;    {:secret jwt-key
;     :alg :HS256}))
;
;(defn signin-link [{:keys [email url] :as env}]
;  (let [email (str/trim email)
;        jwt-key (get-key (assoc env :k :jwt-key))
;        jwt (signin-token jwt-key email)]
;    (-> url
;      url/url
;      (assoc :query {:token jwt})
;      str)))
;
;(defn send-signin-link [{:keys [send-email url-base template location]
;                         {:keys [email]} :params
;                         :as env}]
;  (let [link (signin-link (assoc env
;                            :email email
;                            :url (str url-base "/api/signin")))]
;    (send-email {:to email
;                 :template template
;                 :data {:biff.auth/link link}})
;    {:status 302
;     :headers/Location location}))
;
;(defn email= [s1 s2]
;  (.equalsIgnoreCase s1 s2))
;
;(defn get-uid [{:keys [node db email]}]
;  (or (ffirst
;        (crux/q db
;          {:find '[e]
;           :args [{'input-email email}]
;           :where '[[e :email email]
;                    [(biff.util.system/email= email input-email)]]}))
;    (doto (java.util.UUID/randomUUID)
;      (#(crux/submit-tx
;          node
;          [[:crux.tx/put
;            {:crux.db/id {:user/id %}
;             :user/id %
;             :user/email email}]])))))
;
;(defn signin [{:keys [session on-signin on-signin-fail]
;               {:keys [token]} :params
;               :as env}]
;  (if-some [email (-> token
;                    (bu/decode-jwt {:secret (get-key (assoc env :k :jwt-key))
;                                    :alg :HS256})
;                    u/catchall
;                    :email)]
;    (let [uid (get-uid (assoc env :email email))]
;      {:status 302
;       :headers/Location on-signin
;       :cookies/csrf {:path "/"
;                      :value (force anti-forgery/*anti-forgery-token*)}
;       :session (assoc session :uid uid)})
;    {:status 302
;     :headers/Location on-signin-fail}))
;
;(defn signout [{:keys [on-signout]}]
;  {:status 302
;   :headers/Location on-signout
;   :cookies/ring-session {:value "" :max-age 0}
;   :cookies/csrf {:value "" :max-age 0}
;   :session nil})
;
;(defn signed-in? [req]
;  {:status (if (-> req :session :uid some?)
;             200
;             403)})
;
;(defn auth-route [{:keys [on-signup on-signin-request] :as config}]
;  ["/api" {:middleware [[comp #(merge % config)]]}
;   ["/signup" {:post #(send-signin-link (assoc %
;                                          :template :biff.auth/signup
;                                          :location (or on-signup on-signin-request)))
;               :name ::signup}]
;   ["/signin-request" {:post #(send-signin-link (assoc %
;                                                  :template :biff.auth/signin
;                                                  :location on-signin-request))
;                       :name ::signin-request}]
;   ["/signin" {:get signin
;               :name ::signin
;               :middleware [anti-forgery/wrap-anti-forgery]}]
;   ["/signout" {:get signout
;                :name ::signout}]
;   ["/signed-in" {:get signed-in?
;                  :name ::signed-in}]])
;
;(defn start-handler [{:keys [session-store debug sente-route]
;                      {:keys [static-root]} :static-resource-config
;                      :as config}]
;  (assoc config :biff.http/handler
;    (bu/http-make-handler
;      {:roots (if debug
;                ["www-dev" static-root]
;                [static-root])
;       :session-store session-store
;       :secure-defaults (not debug)
;       :not-found-path (str static-root "/404.html")
;       :routes [["" {:middleware [[wrap-env config]]}
;                 sente-route
;                 (auth-route config)
;                 route]]})))
;
;(bu/sdefs
;  ::app-namespace symbol?
;  ::crux-config (s/and
;                  (s/keys :opt-un [::topology
;                                   ::storage-dir])
;                  (s/or
;                    :standalone #(= :standalone (:topology %))
;                    :jdbc (s/and
;                            #(= :jdbc (:topology %))
;                            (s/keys :req [:crux.jdbc/dbname
;                                          :crux.jdbc/user
;                                          :crux.jdbc/password
;                                          :crux.jdbc/host
;                                          :crux.jdbc/port]))))
;  ::auth-config (s/keys
;                  :req-un [::send-email
;                           ::on-signin
;                           ::on-signin-request
;                           ::on-signin-fail
;                           ::on-signout]
;                  :opt-un [::on-signup])
;  ::static-resource-config (s/keys
;                             :opt-un [::static-pages
;                                      ::static-root
;                                      ::static-resource-root])
;  ::route-config (s/keys
;                  :req-un [::route]
;                  :opt-un [::serve-static
;                           ::cookie-key-path])
;  ::rules-config (s/keys
;                   :opt-un [::rules
;                            ::fn-whitelist])
;  ::biff-config (s/keys
;                  :req [:biff.http/host
;                        :biff.http/config]
;                  :req-un [::app-namespace
;                           ::crux-config
;                           ::auth-config
;                           ::static-resource-config
;                           ::route-config]
;                  :opt-un [::debug
;                           ::rules-config
;                           ::triggers
;                           ::event-handler]))
;
;(defn start-biff [config]
;  {:pre [(s/valid? ::biff-config config)]}
;  (let [config (derive-config config)
;        config (start-crux config)
;        config (start-sente config)
;        config (start-handler config)
;        config (start-session-store config)
;
;        routes [["" {:middleware [[wrap-env env]]}
;                 reitit-route
;                 (auth-route config)
;                 route]]
;
;        handler (bu-http/make-handler
;                  {:root static-root
;                   :debug debug
;                   :routes routes
;                   :cookie-path cookie-key-path})]
;    (write-static-resources static-resource-config)
;    (assoc config
;      {:handler handler
;       :node node
;       :subscriptions subscriptions
;       :submit-tx submit-tx
;       :connected-uids connected-uids
;       :api-send api-send
;       :close (fn []
;                (close-router)
;                (close-tx-pipe)
;                (.close node))})))
