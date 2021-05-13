(ns {{parent-ns}}.handlers
  (:require [biff.util :as bu]
            [biff.crux :as bcrux]
            [crux.api :as crux]))

; See https://findka.com/biff/#web-sockets
(defmulti api (fn [event _] (:id event)))

(defmethod api :default
  [{:keys [id]} _]
  (bu/anom :not-found (str "No method for " id)))

(defmethod api :{{parent-ns}}/set-bar
  [{:keys [biff.crux/node biff.crux/db biff/uid] :as sys} {:keys [value]}]
  ; uid is taken from the user's session cookie.
  (when-some [user (crux/entity @db uid)]
    (println "Current bar value:" (:user/bar user))

    ; See https://findka.com/biff/#transactions
    ; This bypasses authorization functions, but it still checks
    ; document specs.
    (bcrux/submit-tx
      sys
      {[:user uid] {:db/update true
                    :user/bar value}})

    {:some-return-value
     (str "This map will get sent to the client. "
          "Use nil if you don't want to return anything.")}))

(defmethod api :{{parent-ns}}/tx
  [sys tx]
  (bcrux/submit-tx (assoc sys :biff.crux/authorize true) tx))

(defmethod api :{{parent-ns}}/sub
  [sys _]
  (bcrux/handle-subscribe-event! sys))
