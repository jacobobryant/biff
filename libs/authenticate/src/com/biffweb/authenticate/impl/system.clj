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
  (merge
   captcha/noop-config
   #:biff.auth{:app-path            routes/default-app-page
               :email-validator     util/email-valid?
               :primary-color       "#4F46E5"
               :accent-color        "#818CF8"
               :include-signin-page true
               :max-failed-attempts 5
               :code-expiry-minutes 10
               :link-expiry-minutes 60
               :code-page           routes/default-code-page
               :link-page           routes/default-link-page
               :verify-link-page    routes/default-verify-link-page}))

(defn- wrap-options [handler options]
  (fn [ctx]
    (handler (biff.core/validate (merge options ctx)
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
    (let [ctx (merge ctx (when skip-captcha captcha/noop-config))

          fx-handlers
          (-> (select-keys ctx fx-handler-keys)
              (merge {:biff.auth/new-code       util/new-code
                      :biff.auth/new-link-token util/new-link-token
                      :biff.auth/http           hato/request}))]
      (handler (update ctx :biff.fx/handlers merge fx-handlers)))))

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
      (when (get options :biff.auth/include-signin-page)
        frontend/routes)]]))

(defn module [options]
  {:biff.ring/routes (routes options)})
