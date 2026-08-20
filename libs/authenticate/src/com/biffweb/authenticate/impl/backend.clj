(ns com.biffweb.authenticate.impl.backend
  (:require [com.biffweb.core :as biff.core]
            [com.biffweb.fx :as fx]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [tick.core :as tick]
            [com.biffweb.authenticate.impl.routes :as routes]
            [dev.onionpancakes.chassis.core :as chassis])
  (:import [java.security MessageDigest]
           [java.util Base64]
           [java.util.concurrent.locks ReentrantLock]))

(def record-schema
  {:biff-auth-signin/code-hash       :string
   :biff-auth-signin/created-at      'inst?
   :biff-auth-signin/failed-attempts [:and :int [:>= 0]]
   :biff-auth-signin/flow            [:enum :code :link]
   :biff-auth-signin/params          :map})

(defn validate-record [record]
  (biff.core/validate record {:extra-schema record-schema
                              :required     (keys record-schema)}))

(defn normalize-email [email]
  (some-> email str/trim str/lower-case))

(defn hash-secret [secret]
  (let [digest (.digest (MessageDigest/getInstance "SHA-256")
                        (.getBytes ^String secret "UTF-8"))]
    (apply str (map #(format "%02x" %) digest))))

(defn- secret-matches? [secret secret-hash]
  (and (string? secret)
       (string? secret-hash)
       (MessageDigest/isEqual (.getBytes ^String (hash-secret secret) "UTF-8")
                              (.getBytes ^String secret-hash "UTF-8"))))

(defn- url-encode [s]
  (java.net.URLEncoder/encode (str s) "UTF-8"))

(defn- base64url-encode [^String s]
  (.encodeToString (Base64/getUrlEncoder) (.getBytes s "UTF-8")))

(defn base64url-decode [^String s]
  (String. (.decode (Base64/getUrlDecoder) s) "UTF-8"))

(defn- encode-payload [m]
  (base64url-encode (pr-str m)))

(defn decode-payload [s]
  (try
    (let [edn-str (base64url-decode s)]
      (edn/read-string edn-str))
    (catch Exception _ nil)))

(defn- params-to-save [params captcha-param]
  (dissoc (or params {})
          captcha-param
          :email
          :__anti-forgery-token
          "__anti-forgery-token"))

(defn- default-code-email [{:biff.auth/keys [app-name]} {:keys [code]}]
  {:subject (str "Your sign-in code for " app-name)
   :text    (str "Your sign-in code is: " code
                 "\n\nThis code expires in 10 minutes.")
   :html    (chassis/html
             [[:p "Your sign-in code is: " [:strong code]]
              [:p "This code expries in 10 minutes."]])})

(defn- default-link-email [{:biff.auth/keys [app-name]} {:keys [url]}]
  {:subject (str "Sign in to " app-name)
   :text    (str "Click this link to sign in:\n\n" url
                 "\n\nThis link expires in one hour.")
   :html    (chassis/html
             [[:p [:a {:href url} "Click here to sign in"]]
              [:p "This link expires in one hour."]])})

;;;; shared states =============================================================

(defn ensure-user [{:keys [email saved-params existing-user-id]}]
  {:user-id      (or existing-user-id
                     [:fx/create-user {:email email :params saved-params}])
   :biff.fx/next :success-redirect})

(defn success-redirect [{:biff.auth/keys [app-path] :keys [user-id session]}]
  {:status  303
   :headers {"location" app-path}
   :session (-> session
                (assoc :uid user-id)
                (dissoc :biff.auth/state))})

;;;; code flow =================================================================

(fx/defmachine send-code-handler
  :start
  (fn [{:biff.auth/keys [email-validator
                         code-signin-path]
        :keys           [params]
        :as             ctx}]
    (let [email (normalize-email (:email params))]
      (if-not (email-validator ctx email)
        {:status  303
         :headers {"location" (routes/append-query-params
                               code-signin-path "error=invalid-email")}}
        {:email           email
         :original-params params
         :code            [:fx/new-code 6]
         :captcha-ok      [:fx/captcha-verify]
         :biff.fx/next    :check-captcha})))

  :check-captcha
  (fn [{:biff.auth/keys [code-signin-path
                         captcha-param]
        :keys           [email code captcha-ok original-params biff.fx/now]
        :as             ctx}]
    (let [defaults (default-code-email ctx {:code code})
          clean-p  (params-to-save original-params captcha-param)]
      (if (:success captcha-ok)
        [{:_upsert [:fx/kv-set :biff.auth/signin email
                    (validate-record
                     {:biff-auth-signin/code-hash       (hash-secret code)
                      :biff-auth-signin/created-at      now
                      :biff-auth-signin/failed-attempts 0
                      :biff-auth-signin/flow            :code
                      :biff-auth-signin/params          clean-p})]}
         {:email        email
          :sent         [:fx/send-email (merge defaults
                                               {:template :signin-code
                                                :to       email
                                                :code     code})]
          :biff.fx/next :check-send-result}]
        {:status  303
         :headers {"location" (routes/append-query-params
                               code-signin-path "error=captcha")}})))

  :check-send-result
  (fn [{:biff.auth/keys [code-signin-path] :keys [email sent]}]
    (if sent
      {:status  303
       :headers {"location" (routes/append-query-params
                             code-signin-path
                             (str "verify=code&email=" (url-encode email)))}}
      {:status  303
       :headers {"location" (routes/append-query-params
                             code-signin-path "error=send-failed")}})))

(fx/defmachine verify-code-handler
  :start
  (fn [{:keys [params]}]
    (let [email (normalize-email (:email params))]
      {:email          email
       :submitted-code (:code params)
       :signin-record  [:fx/kv-get :biff.auth/signin email]
       :biff.fx/next   :check-code}))

  :check-code
  (fn [{:biff.auth/keys [max-failed-attempts
                         code-expiry-minutes
                         code-signin-path]
        :keys           [email submitted-code signin-record biff.fx/now]}]
    (let [{:biff-auth-signin/keys [code-hash created-at failed-attempts flow
                                   params]}
          signin-record

          elapsed-minutes (when created-at
                            (tick/minutes (tick/between created-at now)))
          success         (and signin-record
                               (= flow :code)
                               (< failed-attempts max-failed-attempts)
                               (< elapsed-minutes code-expiry-minutes)
                               (secret-matches? submitted-code code-hash))]
      (if success
        {:email            email
         :saved-params     params
         :_delete          [:fx/kv-set :biff.auth/signin email nil]
         :existing-user-id [:fx/get-user-id email]
         :biff.fx/next     :ensure-user}
        (merge
         {:status  303
          :headers {"location" (routes/append-query-params
                                code-signin-path
                                (str "verify=code&error=invalid-code&email="
                                     (url-encode email)))}}
         (when (and signin-record
                    (= flow :code)
                    (< failed-attempts max-failed-attempts))
           {:_inc [:fx/kv-set :biff.auth/signin email
                   (-> signin-record
                       (update :biff-auth-signin/failed-attempts inc)
                       validate-record)]})))))

  :ensure-user      ensure-user
  :success-redirect success-redirect)

;;;; link flow =================================================================

(fx/defmachine send-link-handler
  :start
  (fn [{:biff.auth/keys [email-validator link-signin-path]
        :keys           [params]
        :as             ctx}]
    (let [email (normalize-email (:email params))]
      (if-not (email-validator ctx email)
        {:status  303
         :headers {"location" (routes/append-query-params
                               link-signin-path "error=invalid-email")}}
        {:email           email
         :original-params params
         :token           [:fx/new-link-token 32]
         :state-token     [:fx/new-link-token 16]
         :captcha-ok      [:fx/captcha-verify]
         :biff.fx/next    :check-captcha})))

  :check-captcha
  (fn [{:biff.auth/keys [base-url link-signin-path captcha-param]
        :keys           [email token state-token captcha-ok original-params
                         biff.fx/now]
        :as             ctx}]
    (let [clean-p  (params-to-save original-params captcha-param)
          payload  (encode-payload {:token token
                                    :email email
                                    :state state-token})
          link-url (routes/verify-link base-url payload)
          defaults (default-link-email ctx {:url link-url})]
      (if (:success captcha-ok)
        [{:_upsert [:fx/kv-set :biff.auth/signin email
                    (validate-record
                     {:biff-auth-signin/code-hash       (hash-secret token)
                      :biff-auth-signin/created-at      now
                      :biff-auth-signin/failed-attempts 0
                      :biff-auth-signin/flow            :link
                      :biff-auth-signin/params          clean-p})]}
         {:email        email
          :state-token  state-token
          :sent         [:fx/send-email (merge defaults
                                               {:template :signin-link
                                                :to       email
                                                :url      link-url})]
          :biff.fx/next :check-send-result}]
        {:status  303
         :headers {"location" (routes/append-query-params
                               link-signin-path "error=captcha")}})))

  :check-send-result
  (fn [{:biff.auth/keys [link-signin-path]
        :keys           [email state-token sent session]}]
    (if sent
      {:status  303
       :headers {"location" (routes/append-query-params
                             link-signin-path
                             (str "verify=link&email=" (url-encode email)))}
       :session (assoc session :biff.auth/state state-token)}
      {:status  303
       :headers {"location" (routes/append-query-params
                             link-signin-path "error=send-failed")}})))

;; This handler is used for both when the user clicks the link (GET) and for the
;; confirmation form post (POST) which happens if they open the link on a
;; different device/browser from the one they requested it on.
(defn- verify-link-machine [{:keys [get-params
                                    confirmed-from-user?
                                    invalid-token-params]}]
  {:start
   (fn [{:biff.auth/keys [link-signin-path] :as ctx}]
     (let [{:keys [token email] :as auth-params} (get-params ctx)

           {:keys [email] :as auth-params}
           (cond-> auth-params
             email (update :email normalize-email))]
       (cond
         (not (every? string? [token email]))
         {:status  303
          :headers {"location" (routes/append-query-params
                                link-signin-path "error=invalid-link")}}

         (confirmed-from-user? (assoc ctx :auth-params auth-params))
         {:auth-params   auth-params
          :signin-record [:fx/kv-get :biff.auth/signin email]
          :biff.fx/next  :check-token}

         :else
         {:status  303
          :headers {"location"
                    (routes/append-query-params
                     link-signin-path
                     (str "verify=link-confirm&token=" (url-encode token)))}})))

   :check-token
   (fn [{:biff.auth/keys [link-expiry-minutes link-signin-path]
         :keys           [auth-params signin-record biff.fx/now]}]
     (let [{:biff-auth-signin/keys [code-hash created-at flow params]}
           signin-record

           elapsed-minutes (when created-at
                             (tick/minutes (tick/between created-at now)))]
       (if (and signin-record
                (= flow :link)
                (< elapsed-minutes link-expiry-minutes)
                (secret-matches? (:token auth-params) code-hash))
         {:email            (:email auth-params)
          :saved-params     params
          :_delete          [:fx/kv-set :biff.auth/signin (:email auth-params)
                             nil]
          :existing-user-id [:fx/get-user-id (:email auth-params)]
          :biff.fx/next     :ensure-user}
         {:status  303
          :headers {"location" (routes/append-query-params
                                link-signin-path
                                (invalid-token-params auth-params))}})))

   :ensure-user      ensure-user
   :success-redirect success-redirect})

(fx/defmachine verify-link-handler
  (verify-link-machine
   {:get-params           (fn [{:keys [path-params]}]
                            (let [payload (-> path-params
                                              :payload
                                              decode-payload)]
                              (when (map? payload)
                                payload)))
    :confirmed-from-user? (fn [{:keys [session auth-params]}]
                            (and (not-empty (:biff.auth/state session))
                                 (= (:biff.auth/state session)
                                    (:state auth-params))))
    :invalid-token-params (fn [_]
                            "error=invalid-link")}))

;; If the (:biff.auth/state session) didn't match what was in the link payload,
;; we ask the user to submit a form to this handler, and in that form we have
;; them enter the email manually to ensure an attacker doesn't trick them into
;; signing into the attacker's account.
(fx/defmachine verify-link-handler-confirm
  (verify-link-machine
   {:get-params           :params
    :confirmed-from-user? (fn [_] true) ; CSRF protection ensures this
    :invalid-token-params (fn [{:keys [token]}]
                            (str "verify=link-confirm&error=invalid-link&token="
                                 (url-encode token)))}))

;;;; signout ===================================================================

(defn signout-handler [{:keys [session]}]
  {:status  303
   :headers {"location" "/"}
   :session (dissoc session :uid)})

;; Prevent attackers from getting around the max-failed-attempts limit by
;; submitting a bunch of concurrent requests. If you have N web servers, an
;; attacker could get up to (N - 1) extra attempts. Not a big deal.
;; Also helps to avoid race conditions with :biff.auth/create-user, although
;; that function is supposed to handle that.
(defn wrap-lock [lock]
  (fn [handler]
    (fn [request]
      (.lock lock)
      (try
        (handler request)
        (finally
          (.unlock lock))))))

(def routes
  [[routes/signout-link  {:post signout-handler}]
   [(routes/send-code)   {:post send-code-handler}]
   [(routes/send-link)   {:post send-link-handler}]
   [(routes/verify-code) {:middleware [(wrap-lock (ReentrantLock.))]
                          :post       verify-code-handler}]
   [(routes/verify-link) {:middleware [(wrap-lock (ReentrantLock.))]
                          :get        verify-link-handler}]

   [(routes/verify-link-confirm)
    {:middleware [(wrap-lock (ReentrantLock.))]
     :post       verify-link-handler-confirm}]])
