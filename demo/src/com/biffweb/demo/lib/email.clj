(ns com.biffweb.demo.lib.email
  (:require [clojure.tools.logging :as log]
            [hato.client :as hato]))

(defn- send-mailersend
  [{:mailersend/keys [api-key from from-name reply-to]}
   {:keys [to subject html text]}]
  (let [response (hato/post
                  "https://api.mailersend.com/v1/email"
                  {:headers          {"Authorization"
                                      (str "Bearer " (force api-key))}
                   :content-type     :json
                   :throw-exceptions false
                   :as               :json
                   :form-params      {:from     {:email from
                                                 :name  from-name}
                                      :reply_to {:email reply-to
                                                 :name  from-name}
                                      :to       [{:email to}]
                                      :subject  subject
                                      :html     html
                                      :text     text}})]
    (when (<= 400 (:status response))
      (log/warn "MailerSend error:" (:body response)))
    (< (:status response) 400)))

(defn send-email [{:keys [mailersend/api-key] :as ctx}
                  {:keys [to subject text html]}]
  (if api-key
    (send-mailersend ctx {:to      to
                          :subject subject
                          :html    html
                          :text    text})
    (do
      (println)
      (println "---")
      (println "To:     " to)
      (println "Subject:" subject)
      (println)
      (println text)
      (println "---")
      (println)
      true)))
