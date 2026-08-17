(ns com.biffweb.authenticate
  (:require [com.biffweb.authenticate.impl.captcha :as captcha]
            [com.biffweb.authenticate.impl.routes :as routes]
            [com.biffweb.authenticate.impl.system :as system]
            [com.biffweb.core :as biff.core]))

(biff.core/register
 {:biff.auth/accent-color         :string
  :biff.auth/app-name             :string
  :biff.auth/app-path             :string
  :biff.auth/base-url             :string
  :biff.auth/captcha-button-attrs 'ifn?
  :biff.auth/captcha-configured?  'ifn?
  :biff.auth/captcha-head         'ifn?
  :biff.auth/captcha-param        :keyword
  :biff.auth/captcha-verify       'ifn?
  :biff.auth/captcha-widget       'ifn?
  :biff.auth/code-expiry-minutes  :int
  :biff.auth/code-signin-path     :string
  :biff.auth/create-user          'ifn?
  :biff.auth/email-validator      'ifn?
  :biff.auth/font-family          :string
  :biff.auth/get-user-id          'ifn?
  :biff.auth/hcaptcha-secret      :biff.core/secret
  :biff.auth/hcaptcha-site-key    :string
  :biff.auth/include-signin-page  :boolean
  :biff.auth/link-expiry-minutes  :int
  :biff.auth/link-signin-path     :string
  :biff.auth/logo-url             :string
  :biff.auth/max-failed-attempts  :int
  :biff.auth/primary-color        :string
  :biff.auth/recaptcha-secret     :biff.core/secret
  :biff.auth/recaptcha-site-key   :string
  :biff.auth/recaptcha-threshold  :number
  :biff.auth/send-email           'ifn?
  :biff.auth/skip-captcha         :boolean
  :biff.auth/skip-csrf-protection :boolean
  :biff.auth/state                :string
  :biff.auth/turnstile-secret     :biff.core/secret
  :biff.auth/turnstile-site-key   :string})

(def turnstile-config captcha/turnstile-config)

(def recaptcha-config captcha/recaptcha-config)

(def hcaptcha-config captcha/hcaptcha-config)

(def signout-link routes/signout-link)

(defn module [options]
  (system/module options))
