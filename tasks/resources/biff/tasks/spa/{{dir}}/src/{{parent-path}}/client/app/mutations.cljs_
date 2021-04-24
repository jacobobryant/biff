(ns {{parent-ns}}.client.app.mutations
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require
    [cljs.core.async :refer [<!]]
    [clojure.pprint :as pp]
    [{{parent-ns}}.client.app.db :as db]
    [{{parent-ns}}.client.app.system :as s]))

; See https://findka.com/biff/#web-sockets

(defmulti api (comp first :?data))
(defmethod api :default
  [{[event-id] :?data} data]
  (println "unhandled event:" event-id))

(defmethod api :biff/error
  [_ anom]
  (pp/pprint anom))

(defn api-send [& args]
  (apply (:api-send @s/system) args))

; See https://findka.com/biff/#transactions

(defn send-tx [tx]
  (api-send [:biff/tx tx]))

(defn send-message [text]
  (send-tx {[:messages] {:user/id @db/uid
                         :timestamp :db/current-time
                         :text text}}))

(defn delete-message [doc-id]
  (send-tx {[:messages doc-id] nil}))

(defn set-foo [x]
  (send-tx {[:users {:user/id @db/uid}] {:db/update true
                                         :foo x}}))

(defn set-bar [x]
  ; See the console
  (println "Return value from :example/set-bar back-end event handler:")
  (go (prn (<! (api-send [:example/set-bar {:value x}])))))
