(ns com.example.repl
  (:require [com.biffweb :as biff :refer [q]]))

(defn get-sys []
  (biff/assoc-db @biff/system))

(comment

  ;; If I eval (biff/refresh) with Conjure, it starts sending stdout to Vim.
  ;; fix-print makes sure stdout keeps going to the terminal.
  (fix-print (biff/refresh))

  (let [{:keys [biff/db] :as sys} (get-sys)]
    (q db
       '{:find (pull user [*])
         :where [[user :user/email]]})
    )

  (sort (keys @biff/system))

  )
