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
  :biff.auth/accent-color  :string
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

(def turnstile-config captcha/turnstile-config)

(def recaptcha-config captcha/recaptcha-config)

(def hcaptcha-config captcha/hcaptcha-config)

(def signout-path "/_biff/auth/signout")

(defn routes [options]
  (system/routes options))

(defn module [options]
  (system/module options))
