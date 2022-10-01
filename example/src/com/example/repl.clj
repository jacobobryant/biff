(ns com.example.repl
  (:require [com.biffweb :as biff :refer [q]]))

(defn get-sys []
  (biff/assoc-db @biff/system))

(comment

  ;; As of writing this, calling (biff/refresh) with Conjure causes stdout to
  ;; start going to Vim. fix-print makes sure stdout keeps going to the
  ;; terminal. It may not be necessary in your editor.
  (biff/fix-print (biff/refresh))

  (let [{:keys [biff/db] :as sys} (get-sys)]
    (q db
       '{:find (pull user [*])
         :where [[user :user/email]]}))

  (sort (keys @biff/system))

  ;; Check the terminal for output.
  (biff/submit-job (get-sys) :echo {:foo "bar"})
  (deref (biff/submit-job-for-result (get-sys) :echo {:foo "bar"})))
