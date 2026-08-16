(ns com.biffweb.authenticate
  (:require [com.biffweb.authenticate.impl.backend :as backend]
            [com.biffweb.authenticate.impl.frontend :as frontend]
            [com.biffweb.authenticate.impl.captcha :as captcha]))

;; =============================================================================
;; Captcha configs
;; =============================================================================

(defn- configured? [ctx ks]
  (every? #(some? (get ctx %)) ks))

(def turnstile-config
  {:biff.auth/verify-captcha      captcha/turnstile-verify
   :biff.auth/captcha-head        captcha/turnstile-head
   :biff.auth/captcha-widget      captcha/turnstile-widget
   :biff.auth/captcha-param       :cf-turnstile-response
   :biff.auth/captcha-configured? #(configured?
                                    % [:biff.auth/turnstile-secret
                                       :biff.auth/turnstile-site-key])})

(def recaptcha-config
  {:biff.auth/verify-captcha       captcha/recaptcha-verify
   :biff.auth/captcha-head         captcha/recaptcha-head
   :biff.auth/captcha-button-attrs captcha/recaptcha-button-attrs
   :biff.auth/captcha-param        :g-recaptcha-response
   :biff.auth/captcha-configured?  #(configured?
                                     % [:biff.auth/recaptcha-secret
                                        :biff.auth/recaptcha-site-key])})

(def hcaptcha-config
  {:biff.auth/verify-captcha      captcha/hcaptcha-verify
   :biff.auth/captcha-head        captcha/hcaptcha-head
   :biff.auth/captcha-widget      captcha/hcaptcha-widget
   :biff.auth/captcha-param       :h-captcha-response
   :biff.auth/captcha-configured? #(configured?
                                    % [:biff.auth/hcaptcha-secret
                                       :biff.auth/hcaptcha-site-key])})

(def ^:private handler-keys
  #{:biff.auth/get-user-id
    :biff.auth/create-user!
    :biff.auth/send-email
    :biff.auth/verify-captcha
    :biff.auth/new-code
    :biff.auth/new-link-token})

(def ^:private required-handler-keys
  #{:biff.auth/get-user-id
    :biff.auth/create-user!})

(def ^:private required-option-keys
  #{:biff.auth/app-name
    :biff.auth/send-email})

(def ^:private required-runtime-handler-keys
  #{:biff.core/kv-get
    :biff.core/kv-set})

(def ^:private default-options
  #:biff.auth{:app-path            "/app"
              :email-validator     backend/email-valid?
              :primary-color       "#4F46E5"
              :accent-color        "#818CF8"
              :font-family         "'Inter', system-ui, sans-serif"
              :max-failed-attempts 5
              :code-expiry-minutes 10
              :link-expiry-minutes 60
              :code-signin-path    "/signin"
              :link-signin-path    "/signin"})

(defn- wrap-options [handler options]
  (fn [ctx]
    (let [ctx (merge options ctx)
          ctx (if (:biff.auth/base-url ctx)
                ctx
                (assoc ctx :biff.auth/base-url (backend/infer-base-url ctx)))]
      (handler ctx))))

(defn- wrap-handlers [handler fx-handlers]
  (fn [{:biff.auth/keys [captcha-configured? skip-captcha]
        :as             ctx}]
    (let [missing-runtime-handlers (filter #(not (contains? ctx %))
                                           required-runtime-handler-keys)
          _                        (when (seq missing-runtime-handlers)
                                     (throw
                                      (ex-info
                                       (str "Missing required ctx keys: "
                                            (pr-str
                                             (set missing-runtime-handlers)))
                                       {:missing
                                        (set missing-runtime-handlers)})))
          skip-captcha?            (boolean skip-captcha)
          captcha-enabled?         (boolean (and captcha-configured?
                                                 (captcha-configured? ctx)))
          _                        (when (and (not skip-captcha?)
                                              (not captcha-enabled?))
                                     (throw
                                      (ex-info
                                       (str "Captcha is not configured "
                                            "and :biff.auth/skip-captcha "
                                            "is false.")
                                       {:skip-captcha false})))
          runtime-handlers         (select-keys ctx
                                                required-runtime-handler-keys)
          fx-handlers              (-> fx-handlers
                                       (merge runtime-handlers)
                                       (assoc :biff.auth/verify-captcha
                                              (if skip-captcha?
                                                (constantly {:success true})
                                                (:biff.auth/verify-captcha
                                                 fx-handlers))))]
      (handler (cond-> (update ctx :biff.fx/handlers merge fx-handlers)
                 skip-captcha?
                 (dissoc :biff.auth/captcha-head
                         :biff.auth/captcha-widget
                         :biff.auth/captcha-button-attrs
                         :biff.auth/captcha-param))))))

(defn module [options]
  (let [opts             (merge default-options options)
        missing-handlers (filter #(not (contains? opts %))
                                 required-handler-keys)
        missing-options  (filter #(not (contains? opts %))
                                 required-option-keys)]
    (when (seq missing-handlers)
      (throw (ex-info (str "Missing required options: "
                           (pr-str (set missing-handlers)))
                      {:missing (set missing-handlers)})))
    (when (seq missing-options)
      (throw (ex-info (str "Missing required options: "
                           (pr-str (set missing-options)))
                      {:missing (set missing-options)})))
    (let [handlers        (-> (select-keys opts handler-keys)
                              (update :biff.auth/verify-captcha
                                      (fn [h]
                                        (or h (constantly {:success true}))))
                              (assoc :biff.auth/new-code backend/new-code
                                     :biff.auth/new-link-token
                                     backend/new-link-token))
          config-opts     (apply dissoc opts handler-keys)
          middleware      [[wrap-handlers handlers]
                           [wrap-options config-opts]]
          include-signin? (get opts :biff.auth/include-signin-page true)
          send-code       {:post backend/send-code-handler}
          send-link       {:post backend/send-link-handler}
          verify-code     {:post backend/verify-code-handler}
          verify-link     {:get backend/verify-link-handler}
          confirm-link    {:post backend/verify-link-confirm-handler}
          signout         {:post backend/signout-handler}
          signin          {:middleware middleware
                           :get        frontend/signin-page}
          routes          (cond-> [["/_biff/auth" {:middleware middleware}
                                    ["/send-code" send-code]
                                    ["/send-link" send-link]
                                    ["/verify-code" verify-code]
                                    ["/verify-link/:payload"
                                     verify-link]
                                    ["/verify-link-confirm"
                                     confirm-link]
                                    ["/signout" signout]]]
                            include-signin?
                            (conj ["/signin" signin]))]
      {:biff.ring/routes routes})))
