(ns example.core
  (:require
    [biff.core :as biff]
    [clojure.tools.namespace.repl :as tn]
    [example.handlers :refer [api]]
    [example.routes :refer [routes]]
    [example.rules :refer [rules]]
    [example.static :refer [pages]]
    [example.triggers :refer [triggers]]))

(defn send-email [opts]
  (clojure.pprint/pprint [:send-email (select-keys opts [:to :template :data])]))

(defn start [opts]
  (tn/set-refresh-dirs "src" "../../src") ; For hacking on Biff
  (biff/start-spa
    (merge opts
      #:biff{:routes routes
             :static-pages pages
             :event-handler #(api % (:?data %))
             :rules #'rules
             :triggers #'triggers
             :send-email send-email
             :after-refresh `after-refresh})))

(defn -main []
  (start {:biff/first-start true}))

(defn after-refresh []
  (start nil))

(comment
  ; Useful REPL commands:
  (biff.core/refresh)
  (->> @biff.core/system keys sorted (run! prn))
  )
