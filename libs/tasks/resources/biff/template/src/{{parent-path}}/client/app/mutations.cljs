(ns {{parent-ns}}.client.app.mutations
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async :refer [<!]]
            [{{parent-ns}}.client.app.db :as db]
            [{{parent-ns}}.client.app.system :as s]))

; See https://biff.findka.com/#transactions

(defn send-event [& args]
  (apply (:send-fn @s/system) args))

(defn send-tx [tx]
  (send-event [:{{parent-ns}}/tx tx]))

(defn send-message [text]
  (send-tx {[:msg] {:msg/user @db/uid
                    :msg/sent-at :db/server-timestamp
                    :msg/text text}}))

(defn delete-message [doc-id]
  (send-tx {[:msg doc-id] nil}))

(defn set-foo [x]
  (send-tx {[:user @db/uid] {:db/update true
                             :user/foo x}}))

(defn set-bar [x]
  (println "Return value from :{{parent-ns}}/set-bar back end event handler:")
  (go (prn (<! (send-event [:{{parent-ns}}/set-bar {:value x}])))))
