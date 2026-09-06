(ns com.biffweb.authenticate
  (:require [com.biffweb.authenticate.impl.captcha :as captcha]
            [com.biffweb.authenticate.impl.system :as system]
            [com.biffweb.core :as biff.core]))

(biff.core/register
 {;; integration
  :biff.auth/create-user 'ifn?
  :biff.auth/get-user-id 'ifn?
  :biff.auth/send-email  'ifn?

  ;; signin page appearance
  :biff.auth/app-name      :string
  :biff.auth/primary-color :string
  :biff.auth/logo-url      :string

  ;; captcha config
  :biff.auth/captcha-button-attrs 'ifn?
  :biff.auth/captcha-configured?  'ifn?
  :biff.auth/captcha-head         'ifn?
  :biff.auth/captcha-verify       'ifn?
  :biff.auth/captcha-widget       'ifn?
  :biff.auth/hcaptcha-secret      :biff.core/secret
  :biff.auth/hcaptcha-site-key    :string
  :biff.auth/recaptcha-secret     :biff.core/secret
  :biff.auth/recaptcha-site-key   :string
  :biff.auth/recaptcha-threshold  :number
  :biff.auth/turnstile-secret     :biff.core/secret
  :biff.auth/turnstile-site-key   :string

  ;; authentication behavior
  :biff.auth/app-path             :string
  :biff.auth/code-expiry-minutes  :int
  :biff.auth/email-valid?         'ifn?
  :biff.auth/include-signin-page  :boolean
  :biff.auth/max-failed-attempts  :int
  :biff.auth/signin-page          :string
  :biff.auth/skip-captcha         :boolean
  :biff.auth/skip-csrf-protection :boolean})

(def
  ^{:doc
    "Captcha config keys for Cloudflare Turnstile.

     Include this map in the options passed to `routes` / `module`."}
  turnstile-config captcha/turnstile-config)

(def
  ^{:doc
    "Captcha config keys for Google reCAPTCHA.

     Include this map in the options passed to `routes` / `module`."}
  recaptcha-config captcha/recaptcha-config)

(def
  ^{:doc
    "Captcha config keys for hCaptcha.

     Include this map in the options passed to `routes` / `module`."}
  hcaptcha-config captcha/hcaptcha-config)

(def
  ^{:doc
    "The URI path for the signout handler provided by `routes` / `module`.

     To sign out, send a POST request to this path."}
  signout-path "/_biff/auth/signout")

(defn routes
  "Returns a collection of Reitit routes with `options` merged into requests.

   Configuration:

   - :biff.auth/captcha-configured?
   - :biff.auth/include-signin-page
   - :biff.auth/skip-captcha
   - :biff.auth/skip-csrf-protection
   - Additional configuration keys recognized by the individual routes

   See docs/routes.md and docs/config.md. Configuration may be passed in
   `options` or included in Ring request maps."
  [options]
  (system/routes options))

(defn module
  "A biff.core module that includes `routes` under :biff.ring/routes."
  [options]
  (system/module options))
