(ns tasks
  (:require [com.biffweb.run :as biff.run]
            [com.biffweb.tasks :as biff.tasks]))

(def tasks
  (merge biff.tasks/app-tasks
         (select-keys biff.tasks/lib-tasks ["docs"])))

(defn -main [& args]
  (apply biff.run/main tasks args))
