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

; You'll need to provide your own send-email implementation. Soon I'll provide
; a default implementation using Mailgun.
(defn send-email [opts]
  (pp/pprint [:send-email (select-keys opts [:to :template :data])]))

; This function lets Biff manage non-Clojure files for you (e.g.
; all-tasks/10-biff, and the contents of infra/). If you need more control,
; replace the body of this function with the contents of project/update-spa-files
; (see https://github.com/jacobobryant/biff/blob/master/src/biff/project.clj).
; Repeat as far as needed.
(defn update-files [sys]
  (when (or (:biff/dev sys) (:biff/update-project-files sys))
    (project/update-spa-files sys))
  sys)

; This is your app's main entry point. As with update-files, you can copy the
; contents of biff/start-spa into this function for more control. See
; https://github.com/jacobobryant/biff/blob/master/src/biff/core.clj.
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
