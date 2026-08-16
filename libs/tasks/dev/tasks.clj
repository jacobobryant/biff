(ns tasks
  (:require [com.biffweb.run :as biff.run]
            [com.biffweb.tasks :as biff.tasks]))

(def tasks
  (assoc biff.tasks/lib-tasks
         "test-e2e"
         {:task 'com.biffweb.tasks.e2e/test-e2e
          :doc  "Run the task library's Incus end-to-end tests."}))

(defn -main [& args]
  (apply biff.run/main tasks args))
