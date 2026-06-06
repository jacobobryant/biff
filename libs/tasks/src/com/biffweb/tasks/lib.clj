(ns com.biffweb.tasks.lib
  "A collection of tasks for library projects."
  (:require [com.biffweb.tasks :as biff.tasks]
            [com.biffweb.tasks.docs]
            [com.biffweb.run :as biff.run]))

(def tasks
  (merge biff.tasks/base-tasks
         {"docs"    {:task 'com.biffweb.tasks.docs/docs
                     :doc  "Generate API docs from docstrings"}
          "publish" {:task 'com.biffweb.tasks.publish/publish
                     :doc  "Publish library to Clojars"}}))

(defn -main [& args]
  (apply biff.run/main* tasks args))
