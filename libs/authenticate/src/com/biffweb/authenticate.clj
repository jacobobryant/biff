(ns com.biffweb.authenticate
  "Database-agnostic and captcha-provider-agnostic email authentication
   module for Biff web applications.
 
   Public API:
   - module           — creates the auth module with routes
   - turnstile-config — Cloudflare Turnstile captcha config
   - recaptcha-config — Google reCAPTCHA v2/v3 captcha config
   - hcaptcha-config  — hCaptcha captcha config"
  (:require [com.biffweb.authenticate.impl.backend :as backend]
            [com.biffweb.authenticate.impl.frontend :as frontend]
            [com.biffweb.authenticate.impl.captcha :as captcha]))

;; =============================================================================
;; Captcha configs
;; =============================================================================

(defn- configured? [ctx ks]
  (every? #(some? (get ctx %)) ks))

(def turnstile-config
  "Cloudflare Turnstile captcha configuration map.
   Merge this into the options passed to `module`.

   The caller must also provide :biff.auth/turnstile-secret and
   :biff.auth/turnstile-site-key in the module options."
  {:biff.auth/verify-captcha      captcha/turnstile-verify
   :biff.auth/captcha-head        captcha/turnstile-head
   :biff.auth/captcha-widget      captcha/turnstile-widget
   :biff.auth/captcha-param       :cf-turnstile-response
   :biff.auth/captcha-configured? #(configured? % [:biff.auth/turnstile-secret
                                                   :biff.auth/turnstile-site-key])})

(def recaptcha-config
  "Google reCAPTCHA v2/v3 captcha configuration map.
   Merge this into the options passed to `module`.

   Supports both reCAPTCHA v2 (pass/fail) and v3 (score-based).
   The caller must also provide :biff.auth/recaptcha-secret and
   :biff.auth/recaptcha-site-key in the module options.
   Optionally set :biff.auth/recaptcha-threshold (default 0.5, v3 only)."
  {:biff.auth/verify-captcha       captcha/recaptcha-verify
   :biff.auth/captcha-head         captcha/recaptcha-head
   :biff.auth/captcha-button-attrs captcha/recaptcha-button-attrs
   :biff.auth/captcha-param        :g-recaptcha-response
   :biff.auth/captcha-configured?  #(configured? % [:biff.auth/recaptcha-secret
                                                    :biff.auth/recaptcha-site-key])})

(def hcaptcha-config
  "hCaptcha captcha configuration map.
   Merge this into the options passed to `module`.

   The caller must also provide :biff.auth/hcaptcha-secret and
   :biff.auth/hcaptcha-site-key in the module options."
  {:biff.auth/verify-captcha      captcha/hcaptcha-verify
   :biff.auth/captcha-head        captcha/hcaptcha-head
   :biff.auth/captcha-widget      captcha/hcaptcha-widget
   :biff.auth/captcha-param       :h-captcha-response
   :biff.auth/captcha-configured? #(configured? % [:biff.auth/hcaptcha-secret
                                                   :biff.auth/hcaptcha-site-key])})

(def ^:private handler-keys
  "Keys that map to biff.fx effect handler functions."
  #{:biff.auth/get-user-id
    :biff.auth/create-user!
    :biff.auth/send-email
    :biff.auth/verify-captcha
    :biff.auth/new-code
    :biff.auth/new-link-token})

(def ^:private required-handler-keys
  "Handler keys that must be present in the options."
  #{:biff.auth/get-user-id
    :biff.auth/create-user!})

(def ^:private required-option-keys
  "Non-handler option keys that must be present in the options."
  #{:biff.auth/app-name
    :biff.auth/send-email})

(def ^:private required-runtime-handler-keys
  "Handler keys that must be present in the request ctx."
  #{:biff.kv/get-value
    :biff.kv/set-value})

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
    (let [missing-runtime-handlers (filter #(not (contains? ctx %)) required-runtime-handler-keys)
          _                        (when (seq missing-runtime-handlers)
                                     (throw (ex-info (str "Missing required ctx keys: "
                                                          (pr-str (set missing-runtime-handlers)))
                                                     {:missing (set missing-runtime-handlers)})))
          skip-captcha?            (boolean skip-captcha)
          captcha-enabled?         (boolean (and captcha-configured? (captcha-configured? ctx)))
          _                        (when (and (not skip-captcha?) (not captcha-enabled?))
                                     (throw (ex-info "Captcha is not configured and :biff.auth/skip-captcha is false."
                                                     {:skip-captcha false})))
          runtime-handlers         (select-keys ctx required-runtime-handler-keys)
          fx-handlers              (-> fx-handlers
                                       (merge runtime-handlers)
                                       (assoc :biff.auth/verify-captcha
                                              (if skip-captcha?
                                                (constantly {:success true})
                                                (:biff.auth/verify-captcha fx-handlers))))]
      (handler (cond-> (update ctx :biff.fx/handlers merge fx-handlers)
                 skip-captcha?
                 (dissoc :biff.auth/captcha-head
                         :biff.auth/captcha-widget
                         :biff.auth/captcha-button-attrs
                         :biff.auth/captcha-param))))))

(defn module
  "Creates the authentication module. Returns a map with :biff.ring/routes.

   Usage:
     (biff.auth/module
       (merge {:biff.auth/app-path \"/app\"
               :biff.auth/app-name \"My App\"
                :biff.auth/send-email (fn [ctx params] ...)}
              (db-config)
              biff.auth/turnstile-config))

    The options map must include these user keys:
       :biff.auth/get-user-id              — (fn [ctx email]) returns user ID or nil
       :biff.auth/create-user!             — (fn [ctx {:keys [email params]}]) creates user, returns ID
       :biff.auth/send-email               — (fn [ctx params]) sends email, returns boolean.
                                             params includes :template, :to, :code or :url,
                                             :subject, :html, :text.

    The request ctx must also include these kv handlers:
       :biff.kv/get-value                  — (fn [ctx namespace key]) returns stored value or nil
       :biff.kv/set-value                  — (fn [ctx namespace key value]) upserts a stored value

     Backend keys:
      :biff.auth/app-path                 — redirect path after sign-in (default: \"/app\")
      :biff.auth/app-name                 — application name for pages and emails
                                            (required)
     :biff.auth/base-url                 — base URL for magic links. Optional: inferred from
                                           the incoming request if not set.
     :biff.auth/email-validator          — (fn [ctx email]) returns boolean (optional)
     :biff.auth/max-failed-attempts      — max code attempts before lockout (default: 5)
     :biff.auth/code-expiry-minutes      — code expiry in minutes (default: 10)
      :biff.auth/link-expiry-minutes      — link expiry in minutes (default: 60)
      :biff.auth/code-signin-path         — path for code sign-in page (default: \"/signin\")
      :biff.auth/link-signin-path         — path for link sign-in page (default: \"/signin\")
      :biff.auth/include-signin-page      — include /signin frontend route (default: true)
      :biff.auth/skip-captcha             — when true, bypass captcha verification and UI.
                                            When false, captcha must be configured.

   Frontend keys:
     :biff.auth/primary-color            — primary brand color (default: \"#4F46E5\")
     :biff.auth/accent-color             — accent color (default: \"#818CF8\")
     :biff.auth/logo-url                 — logo image URL (optional)
     :biff.auth/font-family              — CSS font-family
                                           (default: \"'Inter', system-ui, sans-serif\")

   Captcha keys (provided by turnstile-config, recaptcha-config, or hcaptcha-config):
     :biff.auth/verify-captcha           — captcha verify handler (optional, skipped if absent)
     :biff.auth/captcha-head             — (fn [ctx]) returns hiccup for <head> (optional)
     :biff.auth/captcha-widget           — (fn [ctx]) returns hiccup widget (optional)
     :biff.auth/captcha-button-attrs     — (fn [ctx]) returns button attrs map (optional)
     :biff.auth/captcha-param            — keyword for captcha token in params (optional)"
  [options]
  (let [opts             (merge default-options options)
        missing-handlers (filter #(not (contains? opts %)) required-handler-keys)
        missing-options  (filter #(not (contains? opts %)) required-option-keys)]
    (when (seq missing-handlers)
      (throw (ex-info (str "Missing required options: " (pr-str (set missing-handlers)))
                      {:missing (set missing-handlers)})))
    (when (seq missing-options)
      (throw (ex-info (str "Missing required options: " (pr-str (set missing-options)))
                      {:missing (set missing-options)})))
    (let [handlers        (-> (select-keys opts handler-keys)
                              (update :biff.auth/verify-captcha
                                      (fn [h] (or h (constantly {:success true}))))
                              (assoc :biff.auth/new-code backend/new-code
                                     :biff.auth/new-link-token backend/new-link-token))
          config-opts     (apply dissoc opts handler-keys)
          middleware      [[wrap-handlers handlers]
                           [wrap-options config-opts]]
          include-signin? (get opts :biff.auth/include-signin-page true)
          routes          (cond-> [["/_biff/auth" {:middleware middleware}
                                    ["/send-code"   {:post backend/send-code-handler}]
                                    ["/send-link"   {:post backend/send-link-handler}]
                                    ["/verify-code" {:post backend/verify-code-handler}]
                                    ["/verify-link/:payload" {:get backend/verify-link-handler}]
                                    ["/verify-link-confirm" {:post backend/verify-link-confirm-handler}]
                                    ["/signout"     {:post backend/signout-handler}]]]
                            include-signin?
                            (conj ["/signin" {:middleware middleware
                                              :get        frontend/signin-page}]))]
      {:biff.ring/routes routes})))
