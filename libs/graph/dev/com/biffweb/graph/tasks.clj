(ns com.biffweb.graph.tasks
  (:require [com.biffweb.run :as biff.run]
            [com.biffweb.tasks.lib :as lib]))

(def tasks
  (assoc lib/tasks
         "errors" {:task 'com.biffweb.graph.tasks.errors/error-examples
                   :doc  "Generate docs/error-examples.md"}))

(defn -main [& args]
  (apply biff.run/main tasks args))
