(ns {{parent-ns}}.handlers
  (:require [biff.crux :as bcrux]
            [biff.util :as bu]
            [crux.api :as crux]))

(defmulti api (fn [event _] (:id event)))

(defmethod api :default
  [{:keys [id]} _]
  (bu/anom :not-found (str "No method for " id)))

(defmethod api :{{parent-ns}}/set-bar
  [{:keys [biff.crux/node biff.crux/db biff/uid] :as sys} {:keys [value]}]
  ; uid is taken from the user's session cookie.
  (when-some [user (crux/entity @db uid)]
    (println "Current bar value:" (:user/bar user))

    ; See https://biff.findka.com/#transactions
    ; This bypasses authorization rules, but it still checks schema.
    (bcrux/submit-tx
      sys
      {[:user uid] {:db/update true
                    :user/bar value}})

    {:some-return-value
     (str "This map will get sent to the client. "
          "Use nil if you don't want to return anything.")}))

(defmethod api :{{parent-ns}}/tx
  [sys tx]
  (bcrux/submit-tx (assoc sys :biff.crux/authorize true) tx)
  nil)

(defmethod api :{{parent-ns}}/sub
  [sys _]
  (bcrux/handle-subscribe-event! sys)
  nil)
