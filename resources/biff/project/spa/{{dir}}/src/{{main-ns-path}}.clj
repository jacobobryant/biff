(ns {{main-ns}}
  (:require
    [biff.core :as biff]
    [biff.project :as project]
    [clojure.pprint :as pp]
    [{{parent-ns}}.handlers :refer [api]]
    [{{parent-ns}}.routes :refer [routes]]
    [{{parent-ns}}.rules :refer [rules]]
    [{{parent-ns}}.static :refer [pages]]
    [{{parent-ns}}.triggers :refer [triggers]]))

(defn send-email [opts]
  (pp/pprint [:send-email (select-keys opts [:to :template :data])]))

(defn update-files [sys]
  (when (or (:biff/dev sys) (:biff/update-project-files sys))
    (project/update-spa-files sys))
  sys)

(defn start [first-start]
  (biff/start-spa
    #:biff{:first-start first-start
           :routes routes
           :static-pages pages
           :event-handler #(api % (:?data %))
           :rules #'rules
           :triggers #'triggers
           :send-email send-email
           :update-project-files update-files
           :after-refresh `after-refresh}))

(defn -main []
  (start true))

(defn after-refresh []
  (start false))

(comment
  ; Useful REPL commands:
  (biff.core/refresh)
  (->> @biff.core/system keys sorted (run! prn))
  )
