(ns {{parent-ns}}.handlers
  (:require
    [biff.util :as bu]
    [biff.crux :as bcrux]
    [clojure.spec.alpha :as s]
    [crux.api :as crux]))

; See https://findka.com/biff/#web-sockets
(defmulti api :id)

(defmethod api :default
  [{:keys [id]} _]
  (bu/anom :not-found (str "No method for " id)))

(defmethod api :example/set-bar
  [{:keys [biff/node biff/db session/uid] :as sys} {:keys [value]}]
  ; uid is taken from the user's session cookie.
  (when (some? uid)
    (println "Current bar value:" (:bar (crux/entity db {:user/id uid})))

    ; biff.crux/submit-tx bypasses authorization functions, but it still checks
    ; document specs.
    (bcrux/submit-tx sys
      {[:users {:user/id uid}] {:db/update true
                                :bar value}})
    ; See https://findka.com/biff/#transactions

    {:some-return-value
     (str "This map will get sent to the client. "
       "Use nil if you don't want to return anything.")}))
