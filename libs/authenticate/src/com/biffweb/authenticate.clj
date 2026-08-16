(ns com.biffweb.authenticate
  (:require [com.biffweb.authenticate.impl :as impl]
            [com.biffweb.authenticate.impl.captcha :as captcha]))

(def turnstile-config captcha/turnstile-config)

(def recaptcha-config captcha/recaptcha-config)

(def hcaptcha-config captcha/hcaptcha-config)

(defn module [options]
  (impl/module options))
