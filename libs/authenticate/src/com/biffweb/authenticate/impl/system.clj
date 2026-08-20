(ns com.biffweb.authenticate.impl.system
  (:require [com.biffweb.authenticate.impl.backend :as backend]
            [com.biffweb.authenticate.impl.frontend :as frontend]
            [com.biffweb.authenticate.impl.captcha :as captcha]
            [com.biffweb.authenticate.impl.routes :as routes]
            [com.biffweb.authenticate.impl.util :as util]
            [com.biffweb.core :as biff.core]
            [hato.client :as hato]
            [ring.middleware.anti-forgery :as anti-forgery]))

(def ^:private fx-handler-keys
  [:biff.auth/get-user-id
   :biff.auth/create-user
   :biff.auth/send-email
   :biff.auth/captcha-verify
   :biff.core/kv-get
   :biff.core/kv-set])

(def ^:private default-options
  #:biff.auth{:app-path            routes/default-app-page
              :email-validator     util/email-valid?
              :primary-color       "#4F46E5"
              :accent-color        "#818CF8"
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
                     {:fx/new-code       util/new-code
                      :fx/new-link-token util/new-link-token
                      :fx/http           hato/request}))]
      (handler (cond-> (update ctx :biff.fx/handlers merge fx-handlers)
                 skip-captcha (merge captcha/noop-config))))))

(defn routes [options]
  (biff.core/validate options)
  (let [options    (merge default-options options)
        middleware (vec (concat
                         [[wrap-options options]
                          [wrap-fx-handlers]]
                         (when-not (:biff.auth/skip-csrf-protection options)
                           [[anti-forgery/wrap-anti-forgery]])))]
    [["" {:middleware middleware}
      backend/routes
      (when (get options :biff.auth/include-signin-page true)
        frontend/routes)]]))

(defn module [options]
  {:biff.ring/routes (routes options)})
