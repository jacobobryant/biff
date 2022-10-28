(ns com.example.util
  (:require [com.biffweb :as biff]
            [camel-snake-kebab.core :as csk]
            [camel-snake-kebab.extras :as cske]
            [clj-http.client :as http]))

(defn email-signin-enabled? [sys]
  (every? sys [:postmark/api-key :recaptcha/site-key :recaptcha/secret-key]))

(defn postmark [{:keys [postmark/api-key]} method endpoint & [form-params options]]
  (http/request
   (merge {:method method
           :url (str "https://api.postmarkapp.com" endpoint)
           :headers {"X-Postmark-Server-Token" api-key}
           :as :json
           :content-type :json
           :form-params (cske/transform-keys csk/->PascalCase form-params)}
          options)))

(defn send-email [{:keys [postmark/from] :as sys} form-params]
  (biff/catchall-verbose
   (postmark sys :post "/email" (merge {:from from} form-params))))
