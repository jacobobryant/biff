(ns {{parent-ns}}.admin
  (:require [biff.util :as bu]
            [crux.api :as crux]))

(defn sys []
  (let [{:keys [biff.crux/node] :as sys} @bu/system]
    (assoc sys :biff.crux/db (delay (crux/db node)))))

(comment

  ; # Todo
  ;
  ; Add code for experimenting to admin ns
  ;  - auth rules
  ;  - girouette
  ;  - db (already)
  ;  - biff-q

  (let [{:keys [biff.crux/db
                biff.crux/node
                biff.crux/subscriptions] :as sys} (sys)]
    (crux/q @db
            '{:find [(pull user [*])]
              :where [[user :user/email]]})
    )
  )
