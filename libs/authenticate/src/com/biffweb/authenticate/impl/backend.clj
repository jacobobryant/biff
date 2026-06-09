(ns com.biffweb.authenticate.impl.backend
  (:require [com.biffweb.fx :as fx]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [tick.core :as tick])
  (:import [java.util Base64]))

(defn normalize-email [email]
  (some-> email str/trim str/lower-case))

(defn email-valid? [_ctx email]
  (and (string? email)
       (re-matches #".+@.+\..+" email)
       (not (re-find #"\s" email))))

(defn new-code [_ctx length]
  (let [rng (java.security.SecureRandom.)]
    (format (str "%0" length "d")
            (.nextInt rng (dec (int (Math/pow 10 length)))))))

(defn new-link-token [_ctx n-bytes]
  (let [bytes (byte-array n-bytes)
        rng   (java.security.SecureRandom.)]
    (.nextBytes rng bytes)
    (apply str (map #(format "%02x" %) bytes))))

(defn- url-encode [s]
  (java.net.URLEncoder/encode (str s) "UTF-8"))

(defn append-query-params
  "Appends query parameters to a path, handling existing query strings."
  [path params-str]
  (if (str/includes? path "?")
    (str path "&" params-str)
    (str path "?" params-str)))

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

(defn- clean-params
  "Removes captcha token and internal keys from request params."
  [params captcha-param]
  (dissoc params :email captcha-param))

(defn- default-code-email [{:biff.auth/keys [app-name]} {:keys [code]}]
  {:subject (str "Your sign-in code for " app-name)
   :text    (str "Your sign-in code is: " code "\n\nThis code expires in a few minutes.")
   :html    (str "<p>Your sign-in code is: <strong>" code "</strong></p>"
                 "<p>This code expires in a few minutes.</p>")})

(defn- default-link-email [{:biff.auth/keys [app-name]} {:keys [url]}]
  {:subject (str "Sign in to " app-name)
   :text    (str "Click this link to sign in:\n\n" url "\n\nThis link expires in about an hour.")
   :html    (str "<p><a href=\"" url "\">Click here to sign in</a></p>"
                 "<p>Or copy and paste this URL: " url "</p>"
                 "<p>This link expires in about an hour.</p>")})

;; === Send code machine ===

(fx/defmachine send-code-handler
  :start
  (fn [{:keys [params] :as ctx}]
    (let [email            (normalize-email (:email params))
          email-validator  (:biff.auth/email-validator ctx)
          code-signin-path (:biff.auth/code-signin-path ctx)]
      (if (not (email-validator ctx email))
        {:status  303
         :headers {"location" (append-query-params code-signin-path "error=invalid-email")}}
        {:email           email
         :original-params params
         :code            [:biff.auth/new-code 6]
         :captcha-ok      [:biff.auth/verify-captcha]
         :biff.fx/next    :check-captcha})))

  :check-captcha
  (fn [{:keys [email code captcha-ok original-params biff.fx/now] :as ctx}]
    (let [defaults         (default-code-email ctx {:code code})
          code-signin-path (:biff.auth/code-signin-path ctx)
          captcha-param    (:biff.auth/captcha-param ctx)
          clean-p          (clean-params original-params captcha-param)]
      (if (:success captcha-ok)
        [{:_upsert [:biff.kv/set-value :biff.auth/signin email
                    {:biff-auth-signin/code            code
                     :biff-auth-signin/created-at      now
                     :biff-auth-signin/failed-attempts 0
                     :biff-auth-signin/params          clean-p}]}
         {:email        email
          :sent         [:biff.auth/send-email (merge defaults
                                                      {:template :signin-code
                                                       :to       email
                                                       :code     code})]
          :biff.fx/next :check-send-result}]
        {:status  303
         :headers {"location" (append-query-params code-signin-path "error=captcha")}})))

  :check-send-result
  (fn [{:keys [email sent] :as ctx}]
    (let [code-signin-path (:biff.auth/code-signin-path ctx)]
      (if sent
        {:status  303
         :headers {"location" (append-query-params code-signin-path
                                                   (str "verify=code&email=" (url-encode email)))}}
        {:status  303
         :headers {"location" (append-query-params code-signin-path "error=send-failed")}}))))

;; === Send link machine ===

(fx/defmachine send-link-handler
  :start
  (fn [{:keys [params] :as ctx}]
    (let [email            (normalize-email (:email params))
          email-validator  (:biff.auth/email-validator ctx)
          link-signin-path (:biff.auth/link-signin-path ctx)]
      (if (not (email-validator ctx email))
        {:status  303
         :headers {"location" (append-query-params link-signin-path "error=invalid-email")}}
        {:email           email
         :original-params params
         :token           [:biff.auth/new-link-token 32]
         :state-token     [:biff.auth/new-link-token 16]
         :captcha-ok      [:biff.auth/verify-captcha]
         :biff.fx/next    :check-captcha})))

  :check-captcha
  (fn [{:keys [email token state-token captcha-ok original-params biff.fx/now] :as ctx}]
    (let [base-url         (:biff.auth/base-url ctx)
          link-signin-path (:biff.auth/link-signin-path ctx)
          captcha-param    (:biff.auth/captcha-param ctx)
          clean-p          (clean-params original-params captcha-param)
          payload          (encode-payload {:token token :email email :state state-token})
          link-url         (str base-url "/_biff/auth/verify-link/" payload)
          defaults         (default-link-email ctx {:url link-url})]
      (if (:success captcha-ok)
        [{:_upsert [:biff.kv/set-value :biff.auth/signin email
                    {:biff-auth-signin/code            token
                     :biff-auth-signin/created-at      now
                     :biff-auth-signin/failed-attempts 0
                     :biff-auth-signin/params          clean-p}]}
         {:email        email
          :state-token  state-token
          :sent         [:biff.auth/send-email (merge defaults
                                                      {:template :signin-link
                                                       :to       email
                                                       :url      link-url})]
          :biff.fx/next :check-send-result}]
        {:status  303
         :headers {"location" (append-query-params link-signin-path "error=captcha")}})))

  :check-send-result
  (fn [{:keys [email state-token sent session] :as ctx}]
    (let [link-signin-path (:biff.auth/link-signin-path ctx)]
      (if sent
        {:status  303
         :headers {"location" (append-query-params link-signin-path
                                                   (str "verify=link&email=" (url-encode email)))}
         :session (assoc session :biff.auth/state state-token)}
        {:status  303
         :headers {"location" (append-query-params link-signin-path "error=send-failed")}}))))

;; === Verify code machine ===

(fx/defmachine verify-code-handler
  :start
  (fn [{:keys [params]}]
    (let [email (normalize-email (:email params))]
      {:email          email
       :submitted-code (:code params)
       :signin-record  [:biff.kv/get-value :biff.auth/signin email]
       :biff.fx/next   :check-code}))

  :check-code
  (fn [{:keys [email submitted-code signin-record biff.fx/now] :as ctx}]
    (let [{:biff-auth-signin/keys [code created-at failed-attempts params]} signin-record
          max-attempts                                                      (:biff.auth/max-failed-attempts ctx)
          max-minutes                                                       (:biff.auth/code-expiry-minutes ctx)
          code-signin-path                                                  (:biff.auth/code-signin-path ctx)
          elapsed-minutes                                                   (when created-at
                                                                              (tick/minutes (tick/between created-at now)))]
      (cond
        (and signin-record
             (< failed-attempts max-attempts)
             (some? elapsed-minutes)
             (< elapsed-minutes max-minutes)
             (= submitted-code code))
        {:email            email
         :saved-params     params
         :_delete          [:biff.kv/set-value :biff.auth/signin email nil]
         :existing-user-id [:biff.auth/get-user-id email]
         :biff.fx/next     :ensure-user}

        (and signin-record (< failed-attempts max-attempts))
        {:_inc    [:biff.kv/set-value :biff.auth/signin email
                   (update signin-record :biff-auth-signin/failed-attempts (fnil inc 0))]
         :status  303
         :headers {"location" (append-query-params code-signin-path
                                                   (str "verify=code&error=invalid-code&email="
                                                        (url-encode email)))}}

        :else
        {:status  303
         :headers {"location" (append-query-params code-signin-path
                                                   (str "verify=code&error=invalid-code&email="
                                                        (url-encode email)))}})))

  :ensure-user
  (fn [{:keys [email saved-params existing-user-id session] :as ctx}]
    (let [app-path (:biff.auth/app-path ctx)]
      (if existing-user-id
        {:status  303
         :headers {"location" app-path}
         :session (-> session
                      (assoc :uid existing-user-id)
                      (dissoc :biff.auth/state))}
        {:email        email
         :saved-params saved-params
         :new-user-id  [:biff.auth/create-user! {:email email :params saved-params}]
         :biff.fx/next :finish-signup})))

  :finish-signup
  (fn [{:keys [new-user-id session] :as ctx}]
    (let [app-path (:biff.auth/app-path ctx)]
      {:status  303
       :headers {"location" app-path}
       :session (-> session
                    (assoc :uid new-user-id)
                    (dissoc :biff.auth/state))})))

;; === Verify link machine ===

(fx/defmachine verify-link-handler
  :start
  (fn [{:keys [path-params session] :as ctx}]
    (let [payload                     (decode-payload (:payload path-params))
          {:keys [token email state]} (when (map? payload) payload)
          session-state               (:biff.auth/state session)
          link-signin-path            (:biff.auth/link-signin-path ctx)
          state-valid?                (and state session-state (= state session-state))]
      (cond
        (nil? payload)
        {:status  303
         :headers {"location" (append-query-params link-signin-path "error=invalid-link")}}

        state-valid?
        {:email           (normalize-email email)
         :submitted-token token
         :signin-record   [:biff.kv/get-value :biff.auth/signin (normalize-email email)]
         :biff.fx/next    :check-token}

        :else
        {:status  303
         :headers {"location" (append-query-params link-signin-path
                                                   (str "verify=link-confirm&token="
                                                        (url-encode token)))}})))

  :check-token
  (fn [{:keys [email submitted-token signin-record biff.fx/now] :as ctx}]
    (let [{:biff-auth-signin/keys [code created-at params]} signin-record
          max-minutes                                       (:biff.auth/link-expiry-minutes ctx)
          link-signin-path                                  (:biff.auth/link-signin-path ctx)
          elapsed-minutes                                   (when created-at
                                                              (tick/minutes (tick/between created-at now)))]
      (if (and signin-record
               (some? elapsed-minutes)
               (< elapsed-minutes max-minutes)
               (= submitted-token code))
        {:email            email
         :saved-params     params
         :_delete          [:biff.kv/set-value :biff.auth/signin email nil]
         :existing-user-id [:biff.auth/get-user-id email]
         :biff.fx/next     :ensure-user}
        {:status  303
         :headers {"location" (append-query-params link-signin-path "error=invalid-link")}})))

  :ensure-user
  (fn [{:keys [email saved-params existing-user-id session] :as ctx}]
    (let [app-path (:biff.auth/app-path ctx)]
      (if existing-user-id
        {:status  303
         :headers {"location" app-path}
         :session (-> session
                      (assoc :uid existing-user-id)
                      (dissoc :biff.auth/state))}
        {:email        email
         :saved-params saved-params
         :new-user-id  [:biff.auth/create-user! {:email email :params saved-params}]
         :biff.fx/next :finish-signup})))

  :finish-signup
  (fn [{:keys [new-user-id session] :as ctx}]
    (let [app-path (:biff.auth/app-path ctx)]
      {:status  303
       :headers {"location" app-path}
       :session (-> session
                    (assoc :uid new-user-id)
                    (dissoc :biff.auth/state))})))

;; === Verify link confirm (session fixation protection) ===

(fx/defmachine verify-link-confirm-handler
  :start
  (fn [{:keys [params] :as ctx}]
    (let [email            (normalize-email (:email params))
          token            (:token params)
          link-signin-path (:biff.auth/link-signin-path ctx)]
      (if (not (string? email))
        {:status  303
         :headers {"location" (append-query-params link-signin-path "error=invalid-link")}}
        {:email           email
         :submitted-token token
         :signin-record   [:biff.kv/get-value :biff.auth/signin email]
         :biff.fx/next    :check-token})))

  :check-token
  (fn [{:keys [email submitted-token signin-record biff.fx/now] :as ctx}]
    (let [{:biff-auth-signin/keys [code created-at params]} signin-record
          max-minutes                                       (:biff.auth/link-expiry-minutes ctx)
          link-signin-path                                  (:biff.auth/link-signin-path ctx)
          elapsed-minutes                                   (when created-at
                                                              (tick/minutes (tick/between created-at now)))]
      (if (and signin-record
               (some? elapsed-minutes)
               (< elapsed-minutes max-minutes)
               (= submitted-token code))
        {:email            email
         :saved-params     params
         :_delete          [:biff.kv/set-value :biff.auth/signin email nil]
         :existing-user-id [:biff.auth/get-user-id email]
         :biff.fx/next     :ensure-user}
        {:status  303
         :headers {"location" (append-query-params link-signin-path
                                                   (str "verify=link-confirm&error=invalid-link&token="
                                                        (url-encode submitted-token)))}})))

  :ensure-user
  (fn [{:keys [email saved-params existing-user-id session] :as ctx}]
    (let [app-path (:biff.auth/app-path ctx)]
      (if existing-user-id
        {:status  303
         :headers {"location" app-path}
         :session (-> session
                      (assoc :uid existing-user-id)
                      (dissoc :biff.auth/state))}
        {:email        email
         :saved-params saved-params
         :new-user-id  [:biff.auth/create-user! {:email email :params saved-params}]
         :biff.fx/next :finish-signup})))

  :finish-signup
  (fn [{:keys [new-user-id session] :as ctx}]
    (let [app-path (:biff.auth/app-path ctx)]
      {:status  303
       :headers {"location" app-path}
       :session (-> session
                    (assoc :uid new-user-id)
                    (dissoc :biff.auth/state))})))

;; === Console email (default when send-email not configured) ===

(defn console-send-email [_ctx {:keys [template to] :as params}]
  (println "=== EMAIL ===")
  (println "  To:" to)
  (println "  Template:" template)
  (println "  Subject:" (:subject params))
  (case template
    :signin-code (println "  Code:" (:code params))
    :signin-link (println "  URL:" (:url params))
    nil)
  (println "=============")
  true)

;; === Base URL inference ===

(defn infer-base-url
  "Infers the base URL from a Ring request map."
  [{:keys [scheme server-name server-port headers]}]
  (when server-name
    (let [scheme (or scheme
                     (if (= (get headers "x-forwarded-proto") "https")
                       :https
                       :http))
          host   (or (get headers "host")
                     (if (and server-port
                              (not (#{80 443} server-port)))
                       (str server-name ":" server-port)
                       server-name))]
      (str (name scheme) "://" host))))

;; === Signout ===

(defn signout-handler [{:keys [session]}]
  {:status  303
   :headers {"location" "/"}
   :session (dissoc session :uid)})
