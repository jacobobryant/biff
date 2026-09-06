(ns com.biffweb.authenticate.impl.captcha
  (:require [com.biffweb.fx :refer [defpipeline]]))

(defn- configured? [ctx ks]
  (every? #(some? (get ctx %)) ks))

;;;; turnstile =================================================================

(def turnstile-url "https://challenges.cloudflare.com/turnstile/v0/siteverify")

(defpipeline turnstile-verify
  (fn [{:keys [biff.auth/turnstile-secret biff.stuff/params]}]
    [:biff.fx/http
     {:method           :post
      :url              turnstile-url
      :form-params      {:secret (force turnstile-secret)

                         :response
                         (:cf-turnstile-response params)}
      :as               :json
      :coerce           :always
      :throw-exceptions false}])

  (fn [_ctx response]
    (boolean (get-in response [:body :success]))))

(defn turnstile-head [_ctx]
  [:script {:src   "https://challenges.cloudflare.com/turnstile/v0/api.js"
            :async true
            :defer true}])

(defn turnstile-widget [{:biff.auth/keys [turnstile-site-key]}]
  [:div.cf-turnstile {:data-sitekey turnstile-site-key}])

(def turnstile-config
  {:biff.auth/captcha-verify      turnstile-verify
   :biff.auth/captcha-head        turnstile-head
   :biff.auth/captcha-widget      turnstile-widget
   :biff.auth/captcha-configured? #(configured?
                                    % [:biff.auth/turnstile-secret
                                       :biff.auth/turnstile-site-key])})

;;;; recaptcha v2/v3 ===========================================================

(def recaptcha-url "https://www.google.com/recaptcha/api/siteverify")

(defpipeline recaptcha-verify
  (fn [{:keys [biff.stuff/params biff.auth/recaptcha-secret]}]
    [:biff.fx/http
     {:method           :post
      :url              recaptcha-url
      :form-params      {:secret (force recaptcha-secret)

                         :response
                         (:g-recaptcha-response params)}
      :as               :json
      :coerce           :always
      :throw-exceptions false}])

  (fn [{:keys [biff.auth/recaptcha-threshold]
        :or   {recaptcha-threshold 0.5}}
       response]
    (let [{:keys [success score]} (:body response)]
      ;; Supports both v2 (no score, just success) and v3 (success + score)
      (boolean (and success
                    (or (nil? score)
                        (<= recaptcha-threshold score)))))))

(defn recaptcha-head [_ctx]
  [:<>
   [:script {:src   "https://www.google.com/recaptcha/api.js"
             :async true                                      :defer true}]
   [:script (str "function biffAuthRecaptchaSubmit(token){"
                 "var b=document.querySelector('.g-recaptcha');"
                 "if(b&&b.form)b.form.submit();"
                 "}")]])

(defn recaptcha-button-attrs [{:biff.auth/keys [recaptcha-site-key]}]
  {:class         "g-recaptcha"
   :data-sitekey  recaptcha-site-key
   :data-callback "biffAuthRecaptchaSubmit"
   :data-action   "signin"})

(def recaptcha-config
  {:biff.auth/captcha-verify       recaptcha-verify
   :biff.auth/captcha-head         recaptcha-head
   :biff.auth/captcha-button-attrs recaptcha-button-attrs
   :biff.auth/captcha-configured?  #(configured?
                                     % [:biff.auth/recaptcha-secret
                                        :biff.auth/recaptcha-site-key])})

;;;; hcaptcha ==================================================================

(def hcaptcha-url "https://hcaptcha.com/siteverify")

(defpipeline hcaptcha-verify
  (fn [{:keys [biff.auth/hcaptcha-secret biff.stuff/params]}]
    [:biff.fx/http
     {:method           :post
      :url              hcaptcha-url
      :form-params      {:secret   (force hcaptcha-secret)
                         :response (:h-captcha-response params)}
      :as               :json
      :coerce           :always
      :throw-exceptions false}])

  (fn [_ctx response]
    (boolean (get-in response [:body :success]))))

(defn hcaptcha-head [_ctx]
  [:script {:src "https://js.hcaptcha.com/1/api.js" :async true :defer true}])

(defn hcaptcha-widget [{:biff.auth/keys [hcaptcha-site-key]}]
  [:div.h-captcha {:data-sitekey hcaptcha-site-key}])

(def hcaptcha-config
  {:biff.auth/captcha-verify      hcaptcha-verify
   :biff.auth/captcha-head        hcaptcha-head
   :biff.auth/captcha-widget      hcaptcha-widget
   :biff.auth/captcha-configured? #(configured?
                                    % [:biff.auth/hcaptcha-secret
                                       :biff.auth/hcaptcha-site-key])})

;;;; no-op =====================================================================

(def noop-config
  {:biff.auth/captcha-verify       (constantly false)
   :biff.auth/captcha-head         (constantly nil)
   :biff.auth/captcha-widget       (constantly nil)
   :biff.auth/captcha-configured?  (constantly false)
   :biff.auth/captcha-button-attrs (constantly nil)})
