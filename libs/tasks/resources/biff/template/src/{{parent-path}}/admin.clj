(ns {{parent-ns}}.admin
  (:require [biff.crux :as bcrux]
            [biff.util :as bu]
            [crux.api :as crux]))

(defn sys []
  (let [{:keys [biff.crux/node] :as sys} @bu/system]
    (assoc sys :biff.crux/db (delay (crux/db node)))))

(comment

  ; This namespace isn't required from anywhere. You can use it as a
  ; repl-driven admin console and for experimenting.

  ; Inspect the db
  (let [{:keys [biff.crux/db]} (sys)]
    (crux/q @db
            '{:find [(pull user [*])]
              :where [[user :user/email]]}))

  ; Submit transactions (this will bypass authorization rules since we don't
  ; set :biff.crux/authorize true. i.e. it's a trusted transaction)
  (bcrux/submit-tx
    (sys)
    {[:user] {:user/email "foo@example.com"}})

  ; Inspect transactions before running them
  (let [s (sys)
        uid "some-uuid" ; In actual usage, you would query this from the db first.
        {:keys [crux-tx]
         :as tx-info} (bcrux/get-tx-info
                        (assoc s :biff/uid uid)
                        {[:user] {:user/email "foo@example.com"}})]
    (bu/pprint crux-tx)
    ; See if this transaction would pass authorization rules
    (when-some [bad-doc (bcrux/check-write s tx-info)]
      (println "The transaction is unauthorized:")
      (bu/pprint bad-doc)))

  ; Test out subscribable queries (including authorization)
  (bcrux/biff-q
    (sys)
    {:doc-type :msg
     :where '[[:msg/sent-at t]
              [(<= #inst "1970" t)]]})

  ; Test out Girouette classes (only works in dev, not prod)
  ((requiring-resolve '{{parent-ns}}.dev.css/class-name->garden)
   "bg-blue-200")

  )
