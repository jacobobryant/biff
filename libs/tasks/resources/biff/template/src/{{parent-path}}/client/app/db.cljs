(ns {{parent-ns}}.client.app.db
  (:require [biff.rum :as br]))

; See also:
; - https://biff.findka.com/#subscriptions
; - https://biff.findka.com/codox/biff.rum.html#var-defatoms
; - https://biff.findka.com/codox/biff.rum.html#var-defderivations

(br/defatoms
  ; sub-results contains a map of query->doc-type->id->doc.
  sub-results {}
  message-cutoff (js/Date. (- (js/Date.) (* 1000 60 5)))
  route {})

(br/defderivations
  ; data is an atom that contains a map of doc-type->id->doc. It will be updated
  ; whenever sub-results changes.
  data (apply merge-with merge (vals @sub-results))

  ; :status is a singleton, so the document ID is nil.
  uid (get-in @data [:status nil :uid])
  user (get-in @data [:user @uid])
  email (:user/email @user)
  foo (:user/foo @user)
  bar (:user/bar @user)
  messages (->> @data
                :msg
                vals
                (sort-by :msg/sent-at #(compare %2 %1)))

  tab (get-in @route [:data :name] :crud)

  ; :status is a special query that returns data about the currently authenticated user.
  sub-queries [:status
               {:doc-type :msg
                :where [[:msg/sent-at 't]
                        [(list '< @message-cutoff 't)]]}
               (when @uid
                 {:doc-type :user
                  :id @uid})]

  subscriptions (->> @sub-queries
                     flatten
                     (filter some?)
                     (map (fn [query]
                            [:{{parent-ns}}/sub query]))
                     set))
