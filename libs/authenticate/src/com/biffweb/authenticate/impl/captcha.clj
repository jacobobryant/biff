(ns com.biffweb.authenticate.impl.captcha
  (:require [com.biffweb.fx :as fx]))

;; === Cloudflare Turnstile ===

(def turnstile-url "https://challenges.cloudflare.com/turnstile/v0/siteverify")

(fx/defmachine turnstile-verify
  :start
  (fn [{:keys [biff.auth/turnstile-secret params]}]
    {:response     [:biff.fx/http
                    {:method           :post
                     :url              turnstile-url
                     :form-params      {:secret   (turnstile-secret)
                                        :response (:cf-turnstile-response params)}
                     :as               :json
                     :coerce           :always
                     :throw-exceptions false}]
     :biff.fx/next :check-response})

  :check-response
  (fn [{:keys [response]}]
    {:success (boolean (get-in response [:body :success]))}))

(defn turnstile-head [_ctx]
  [:script {:src   "https://challenges.cloudflare.com/turnstile/v0/api.js"
            :async true
            :defer true}])

(defn turnstile-widget [{:biff.auth/keys [turnstile-site-key]}]
  [:div.cf-turnstile {:data-sitekey turnstile-site-key}])

;; === Google reCAPTCHA v2/v3 ===

(def recaptcha-url "https://www.google.com/recaptcha/api/siteverify")

(fx/defmachine recaptcha-verify
  :start
  (fn [{:keys [params biff.auth/recaptcha-secret]}]
    {:response     [:biff.fx/http
                    {:method           :post
                     :url              recaptcha-url
                     :form-params      {:secret   (recaptcha-secret)
                                        :response (:g-recaptcha-response params)}
                     :as               :json
                     :coerce           :always
                     :throw-exceptions false}]
     :biff.fx/next :check-response})

  :check-response
  (fn [{:keys [response biff.auth/recaptcha-threshold]
        :or   {recaptcha-threshold 0.5}}]
    (let [{:keys [success score]} (:body response)]
      ;; Supports both v2 (no score, just success) and v3 (success + score)
      {:success (boolean (and success
                              (or (nil? score)
                                  (<= recaptcha-threshold score))))})))

(defn recaptcha-head [_ctx]
  [:<>
   [:script {:src "https://www.google.com/recaptcha/api.js" :async true :defer true}]
   [:script (str "function biffAuthRecaptchaSubmit(token){"
                 "var b=document.querySelector('.g-recaptcha');"
                 "if(b&&b.form)b.form.submit();"
                 "}")]])

(defn recaptcha-button-attrs [{:biff.auth/keys [recaptcha-site-key]}]
  {:class         "g-recaptcha"
   :data-sitekey  recaptcha-site-key
   :data-callback "biffAuthRecaptchaSubmit"
   :data-action   "signin"})

;; === hCaptcha ===

(def hcaptcha-url "https://hcaptcha.com/siteverify")

(fx/defmachine hcaptcha-verify
  :start
  (fn [{:keys [biff.auth/hcaptcha-secret params]}]
    {:response     [:biff.fx/http
                    {:method           :post
                     :url              hcaptcha-url
                     :form-params      {:secret   (hcaptcha-secret)
                                        :response (:h-captcha-response params)}
                     :as               :json
                     :coerce           :always
                     :throw-exceptions false}]
     :biff.fx/next :check-response})

  :check-response
  (fn [{:keys [response]}]
    {:success (boolean (get-in response [:body :success]))}))

(defn hcaptcha-head [_ctx]
  [:script {:src "https://js.hcaptcha.com/1/api.js" :async true :defer true}])

(defn hcaptcha-widget [{:biff.auth/keys [hcaptcha-site-key]}]
  [:div.h-captcha {:data-sitekey hcaptcha-site-key}])
