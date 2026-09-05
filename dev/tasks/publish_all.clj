(ns tasks.publish-all
  (:require [com.biffweb.tasks :as biff.tasks]))

(def ^:private libraries
  ["stuff"
   "core"
   "config"
   "fx"
   "graph"
   "sqlite"
   "xtdb"
   "ring"
   "datastar"
   "run"
   "tasks"
   "background"
   "authenticate"
   "admin"
   "defaults"])

(defn publish-all [& args]
  (doseq [library libraries]
    (binding [biff.tasks/*extra-config*
              {:biff.tasks/lib-name     library
               :biff.tasks/project-root (str "libs/" library)}]
      (apply biff.tasks/publish args)))
  nil)
