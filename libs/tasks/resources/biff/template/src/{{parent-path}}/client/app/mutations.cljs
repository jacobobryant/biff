(ns {{parent-ns}}.client.app.mutations
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async :refer [<!]]
            [clojure.pprint :as pp]
            [{{parent-ns}}.client.app.db :as db]
            [{{parent-ns}}.client.app.system :as s]))

; See https://findka.com/biff/#web-sockets

(defmulti handler (comp first :?data))
(defmethod handler :default
  [{[event-id] :?data} data]
  (println "unhandled event:" event-id))

(defmethod handler :biff/error
  [_ anom]
  (pp/pprint anom))

(defn api [& args]
  (apply (:send-fn @s/system) args))

; See https://findka.com/biff/#transactions

(defn send-tx [tx]
  (api [:{{parent-ns}}/tx tx]))

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
  ; See the console
  (println "Return value from :{{parent-ns}}/set-bar back end event handler:")
  (go (prn (<! (api [:{{parent-ns}}/set-bar {:value x}])))))
