(ns com.biffweb.authenticate.impl.system
  (:require [com.biffweb.authenticate.impl.backend :as backend]
            [com.biffweb.authenticate.impl.frontend :as frontend]
            [com.biffweb.authenticate.impl.captcha :as captcha]
            [com.biffweb.authenticate.impl.routes :as routes]
            [com.biffweb.core :as biff.core]
            [hato.client :as hato]
            [ring.middleware.anti-forgery :as anti-forgery])
  (:import [java.util.concurrent.locks ReentrantLock]))

(def ^:private fx-handler-keys
  [:biff.auth/get-user-id
   :biff.auth/create-user
   :biff.auth/send-email
   :biff.auth/captcha-verify
   :biff.core/kv-get
   :biff.core/kv-set])

(def ^:private default-options
  #:biff.auth{:app-path            routes/default-app-page
              :email-validator     backend/email-valid?
              :primary-color       "#4F46E5"
              :accent-color        "#818CF8"
              :font-family         "'Inter', system-ui, sans-serif"
              :max-failed-attempts 5
              :code-expiry-minutes 10
              :link-expiry-minutes 60
              :code-signin-path    routes/default-signin-page
              :link-signin-path    routes/default-signin-page})

(defn- wrap-options [handler options]
  (fn [ctx]
    (handler (biff.core/validate (merge captcha/noop-config options ctx)
                                 {:required (into fx-handler-keys
                                                  [:biff.auth/app-name
                                                   :biff.auth/base-url])}))))

(defn- wrap-fx-handlers [handler]
  (fn [{:biff.auth/keys [captcha-configured? skip-captcha]
        :as             ctx}]
    (assert (or skip-captcha
                (and captcha-configured?
                     (captcha-configured? ctx)))
            "Captcha is not configured")
    (let [fx-handlers
          (-> (select-keys ctx fx-handler-keys)
              (update-keys #(keyword "fx" (name %)))
              (merge (when skip-captcha
                       {:fx/captcha-verify (constantly {:success true})})
                     {:fx/new-code       backend/new-code
                      :fx/new-link-token backend/new-link-token
                      :fx/http           hato/request}))]
      (handler (cond-> (update ctx :biff.fx/handlers merge fx-handlers)
                 skip-captcha (merge captcha/noop-config))))))

;; Prevent attackers from getting around the max-failed-attempts limit by
;; submitting a bunch of concurrent requests. If you have N web servers, an
;; attacker could get up to (N - 1) extra attempts. Not a big deal.
;; Also helps to avoid race conditions with :biff.auth/create-user, although
;; that function is supposed to handle that.
(defn wrap-lock [lock]
  (fn [handler]
    (fn [request]
      (.lock lock)
      (try
        (handler request)
        (finally
          (.unlock lock))))))

(defn routes [options]
  (biff.core/validate options)
  (let [options    (merge default-options options)
        middleware (vec (concat
                         [[wrap-options options]
                          [wrap-fx-handlers]]
                         (when-not (:biff.auth/skip-csrf-protection options)
                           [[anti-forgery/wrap-anti-forgery]])))]
    (vec
     (concat
      [["" {:middleware middleware}
        [(routes/send-code)   {:post backend/send-code-handler}]
        [(routes/send-link)   {:post backend/send-link-handler}]
        [(routes/verify-code) {:middleware [(wrap-lock (ReentrantLock.))]
                               :post       backend/verify-code-handler}]
        [(routes/verify-link) {:middleware [(wrap-lock (ReentrantLock.))]
                               :get        backend/verify-link-handler}]

        [(routes/verify-link-confirm)
         {:middleware [(wrap-lock (ReentrantLock.))]
          :post       backend/verify-link-handler-confirm}]

        [routes/signout-link          {:post backend/signout-handler}]]]
      (when (get options :biff.auth/include-signin-page true)
        [[routes/default-signin-page {:middleware middleware
                                      :get        frontend/signin-page}]])))))

(defn module [options]
  {:biff.ring/routes (routes options)})
