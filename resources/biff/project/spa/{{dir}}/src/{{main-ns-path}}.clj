(ns {{main-ns}}
  (:require
    [biff.core :as biff]
    [biff.project :as project]
    [clojure.pprint :as pp]
    [{{parent-ns}}.handlers :refer [api]]
    [{{parent-ns}}.jobs :refer [jobs]]
    [{{parent-ns}}.routes :refer [routes]]
    [{{parent-ns}}.rules :refer [rules]]
    [{{parent-ns}}.static :refer [pages]]
    [{{parent-ns}}.triggers :refer [triggers]]))

; You'll need to provide your own send-email implementation. Soon I'll provide
; a default implementation using Mailgun.
(defn send-email [opts]
  (pp/pprint [:send-email (select-keys opts [:to :template :data])]))

; This is your app's main entry point. When you reach the point where you
; need more flexibility than what Biff provides out of the box, copy the
; contents of biff.core/start-spa and/or biff.project/update-spa-files
; into this function, modifying as needed. Repeat until you have the level
; of control you need.
; See https://github.com/jacobobryant/biff/blob/master/src/biff/core.clj
; and https://github.com/jacobobryant/biff/blob/master/src/biff/project.clj.
(defn start [first-start]
  (let [sys (biff/start-spa
              #:biff{:first-start first-start
                     :routes routes
                     :static-pages pages
                     :event-handler #(api % (:?data %))
                     :rules #'rules
                     :triggers #'triggers
                     :jobs jobs
                     :send-email send-email
                     :after-refresh `after-refresh})]
    (when (:biff/dev sys)
      ; This function lets Biff manage non-Clojure files for you (e.g.
      ; all-tasks/10-biff, and the contents of infra/).
      (project/update-spa-files sys))
    (println "System started.")))

(defn -main []
  (start true))

(defn after-refresh []
  (start false))

(comment
  ; Useful REPL commands:
  (biff.core/refresh)
  (->> @biff.core/system keys sort (run! prn))
  )
