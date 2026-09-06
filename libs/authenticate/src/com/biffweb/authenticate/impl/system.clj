(ns com.biffweb.authenticate.impl.system
  (:require [com.biffweb.authenticate.impl.backend :as backend]
            [com.biffweb.authenticate.impl.frontend :as frontend]
            [com.biffweb.authenticate.impl.captcha :as captcha]
            [com.biffweb.core :as biff.core]
            [com.biffweb.stuff :as stuff]
            [ring.middleware.anti-forgery :as anti-forgery]))

(defn email-valid? [_ctx email]
  (and (string? email)
       (re-matches #".+@.+\..+" email)
       (not (re-find #"\s" email))))

(defn new-code [_ctx length]
  (let [rng (java.security.SecureRandom.)]
    (format (str "%0" length "d")
            (.nextInt rng (dec (int (Math/pow 10 length)))))))

(def ^:private fx-handler-keys
  [:biff.auth/get-user-id
   :biff.auth/create-user
   :biff.auth/send-email
   :biff.auth/captcha-verify
   :biff.core/kv-get
   :biff.core/kv-set])

(def ^:private default-options
  (merge
   captcha/noop-config
   #:biff.auth{:app-path            "/app"
               :email-valid?        email-valid?
               :primary-color       "#4F46E5"
               :include-signin-page true
               :max-failed-attempts 5
               :code-expiry-minutes 10
               :signin-page         "/signin"}))

(defn- wrap-options [handler options]
  (fn [ctx]
    (handler (biff.core/validate (merge options ctx)
                                 {:required fx-handler-keys}))))

(defn- wrap-fx-handlers [handler]
  (fn [{:biff.auth/keys [captcha-configured? skip-captcha]
        :as             ctx}]
    (assert (or skip-captcha
                (and captcha-configured?
                     (captcha-configured? ctx)))
            "Captcha is not configured")
    (let [ctx (cond-> ctx
                skip-captcha
                (merge captcha/noop-config
                       {:biff.auth/captcha-verify (fn [_] true)}))

          fx-handlers
          (-> (select-keys ctx fx-handler-keys)
              (merge {:biff.auth/new-code new-code}))]
      (handler (update ctx :biff.fx/handlers merge fx-handlers)))))

(defn routes [options]
  (biff.core/validate options)
  (let [options    (merge default-options options)
        middleware (into [[stuff/wrap-params]
                          [wrap-options options]
                          [wrap-fx-handlers]]
                         (when-not (:biff.auth/skip-csrf-protection options)
                           [[anti-forgery/wrap-anti-forgery]]))]
    [["" {:middleware middleware}
      backend/routes
      (when (get options :biff.auth/include-signin-page)
        frontend/routes)]]))

(defn module [options]
  {:biff.ring/routes (routes options)})
