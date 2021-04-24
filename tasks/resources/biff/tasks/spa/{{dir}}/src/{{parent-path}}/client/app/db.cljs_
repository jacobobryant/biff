(ns {{parent-ns}}.client.app.db
  (:require
    [biff.rum :as br]))

; See https://findka.com/biff/#subscriptions

; Same as (do
;           (def sub-results (atom {}))
;           (def message-cutoff (atom (js/Date.)))
;           ...)
(br/defatoms
  ; sub-results contains a map of query->table->id->doc.
  sub-results {}
  message-cutoff (js/Date.)
  route {})

; defderivations lets you use rum.core/derived-atom without the boilerplate.
; If you change the code for a derivation, you'll have to refresh window for
; the change to take effect.
(br/defderivations
  ; data is an atom that contains a map of table->id->doc. It will be updated
  ; whenever sub-results changes.
  data (apply merge-with merge (vals @sub-results))

  ; :uid is a singleton, so the document ID is nil.
  uid (get-in @data [:uid nil :uid])
  user (get-in @data [:users {:user/id @uid}])
  email (:user/email @user)
  foo (:foo @user)
  bar (:bar @user)
  messages (->> @data
             :messages
             vals
             (sort-by :timestamp #(compare %2 %1)))

  tab (get-in @route [:data :name] :crud)

  subscriptions (disj #{[:biff/sub :uid]
                        [:biff/sub {:table :messages
                                    :args {'t0 @message-cutoff}
                                    :where '[[:timestamp t]
                                             [(< t0 t)]]}]
                        (when @uid
                          [:biff/sub {:table :users
                                      :id {:user/id @uid}}])}
                  nil))
