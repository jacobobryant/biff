(ns com.example.repl
  (:require [com.biffweb :as biff :refer [q]]))

(defn get-sys []
  (biff/assoc-db @biff/system))

(comment

  (biff/refresh)

  ;; template
  (let [{:keys [biff/db] :as sys} (get-sys)]
    (q db
       '{:find (pull user [*])
         :where [[user :user/email]]})
    )

  (keys @biff/system)

  )
