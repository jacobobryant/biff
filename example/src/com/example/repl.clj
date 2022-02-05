(ns com.example.repl
  (:require [com.biffweb :as biff :refer [q]]))

(defn get-sys []
  (biff/assoc-db @biff/system))

(comment

  ;; template
  (let [{:keys [biff/db] :as sys} (get-sys)]
    )

  )
