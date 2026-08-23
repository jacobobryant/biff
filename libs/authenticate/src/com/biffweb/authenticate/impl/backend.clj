(ns com.biffweb.authenticate.impl.backend
  (:require [com.biffweb.core :as biff.core]
            [com.biffweb.fx :as fx]
            [clojure.string :as str]
            [tick.core :as tick]
            [dev.onionpancakes.chassis.core :as chassis])
  (:import [java.security MessageDigest]))

(def record-schema
  {:biff-auth-signin/code-hash       :string
   :biff-auth-signin/created-at      'inst?
   :biff-auth-signin/failed-attempts [:and :int [:>= 0]]
   :biff-auth-signin/params          :map})

(defn validate-record [record]
  (biff.core/validate record {:extra-schema record-schema
                              :required     (keys record-schema)}))

(defn- url-encode [s]
  (java.net.URLEncoder/encode (str s) "UTF-8"))

(defn add-query [path query-map]
  (let [query-map (-> query-map
                      (update-keys name)
                      (update-vals url-encode))
        query-str (->> query-map
                       (mapv (fn [[k v]]
                               (str k "=" v)))
                       (str/join "&"))]
    (if (str/includes? path "?")
      (str path "&" query-str)
      (str path "?" query-str))))

(defn hash-secret [secret]
  (let [digest (.digest (MessageDigest/getInstance "SHA-256")
                        (.getBytes ^String secret "UTF-8"))]
    (apply str (map #(format "%02x" %) digest))))

(defn- secret-matches? [secret secret-hash]
  (and (string? secret)
       (string? secret-hash)
       (MessageDigest/isEqual (.getBytes ^String (hash-secret secret) "UTF-8")
                              (.getBytes ^String secret-hash "UTF-8"))))

(defn- params-to-save [params]
  (dissoc (or params {}) :email :__anti-forgery-token))

(defn- default-code-email [_ctx {:keys [code]}]
  {:subject "Your sign-in code"
   :text    (str "Your sign-in code is: " code
                 "\n\nThis code expires in 10 minutes.")
   :html    (chassis/html
             [[:p "Your sign-in code is: " [:strong code]]
              [:p "This code expires in 10 minutes."]])})

(fx/defmachine send-code-handler
  :start
  (fn [{:biff.auth/keys [email-valid? signin-page]
        :keys           [biff.stuff/params]
        :as             ctx}]
    (if-not (email-valid? ctx (:email params))
      {:status  303
       :headers {"location" (add-query signin-page {:error "invalid-email"})}}
      {::email          (:email params)
       ::code           [:biff.auth/new-code 6]
       ::captcha-passed [:biff.auth/captcha-verify]
       :biff.fx/next    :check-captcha}))

  :check-captcha
  (fn [{::keys          [email code captcha-passed]
        :biff.auth/keys [signin-page]
        :keys           [biff.fx/now biff.stuff/params]
        :as             ctx}]
    (let [defaults (default-code-email ctx {:code code})
          clean-p  (params-to-save params)]
      (if captcha-passed
        [{:_upsert [:biff.core/kv-set :biff.auth/signin email
                    (validate-record
                     {:biff-auth-signin/code-hash       (hash-secret code)
                      :biff-auth-signin/created-at      now
                      :biff-auth-signin/failed-attempts 0
                      :biff-auth-signin/params          clean-p})]}
         {::email       email
          ::sent        [:biff.auth/send-email (merge defaults
                                                      {:to   email
                                                       :code code})]
          :biff.fx/next :check-send-result}]
        {:status  303
         :headers {"location" (add-query signin-page {:error "captcha"})}})))

  :check-send-result
  (fn [{::keys [email sent] :biff.auth/keys [signin-page]}]
    (if sent
      {:status  303
       :headers {"location" (add-query signin-page {:sent-to email})}}
      {:status  303
       :headers {"location" (add-query signin-page {:error "send-failed"})}})))

(fx/defmachine verify-code-handler
  :start
  (fn [{:keys [biff.stuff/params]}]
    {::submitted-code (:code params)
     ::signin-record  [:biff.core/kv-get :biff.auth/signin (:email params)]
     :biff.fx/next    :check-code})

  :check-code
  (fn [{::keys          [submitted-code signin-record]
        :biff.auth/keys [max-failed-attempts code-expiry-minutes signin-page]
        :keys           [biff.fx/now biff.stuff/params]}]
    (let [{:keys [email]} params

          {:biff-auth-signin/keys [code-hash created-at failed-attempts params]}
          signin-record

          elapsed-minutes (when created-at
                            (tick/minutes (tick/between created-at now)))
          success         (and signin-record
                               (< failed-attempts max-failed-attempts)
                               (< elapsed-minutes code-expiry-minutes)
                               (secret-matches? submitted-code code-hash))]
      (if success
        {:_delete           [:biff.core/kv-set :biff.auth/signin email nil]
         ::saved-params     params
         ::existing-user-id [:biff.auth/get-user-id email]
         :biff.fx/next      :ensure-user}
        (merge
         {:status  303
          :headers {"location" (add-query signin-page
                                          {:error   "invalid-code"
                                           :sent-to email})}}
         (when (and signin-record
                    (< failed-attempts max-failed-attempts))
           {:_inc [:biff.core/kv-set :biff.auth/signin email
                   (-> signin-record
                       (update :biff-auth-signin/failed-attempts inc)
                       validate-record)]})))))

  :ensure-user
  (fn [{::keys [saved-params existing-user-id]
        :keys  [biff.stuff/params]}]
    {::user-id     (or existing-user-id
                       [:biff.auth/create-user
                        {:email (:email params) :params saved-params}])
     :biff.fx/next :success-redirect})

  :success-redirect
  (fn [{:keys [::user-id biff.auth/app-path session]}]
    {:status  303
     :headers {"location" app-path}
     :session (-> session
                  (assoc :uid user-id)
                  (dissoc :biff.auth/state))}))

(defn signout-handler [_]
  {:status  303
   :headers {"location" "/"}
   :session {}})

(defn wrap-normalize-email [handler]
  (fn [{:keys [biff.stuff/params] :as request}]
    (handler
     (cond-> request
       (contains? params :email)
       (update-in [:biff.stuff/params :email]
                  #(some-> %
                           str/trim
                           str/lower-case))))))

(def routes
  ["" {:middleware [wrap-normalize-email]}
   ["/_biff/auth/send-code"   {:post send-code-handler}]
   ["/_biff/auth/verify-code" {:post verify-code-handler}]
   ["/_biff/auth/signout"     {:post signout-handler}]])
