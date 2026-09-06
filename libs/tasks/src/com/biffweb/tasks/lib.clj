(ns com.biffweb.tasks.lib
  (:require [com.biffweb.run :as biff.run]
            [com.biffweb.tasks.impl.base :as base]))

(def tasks
  (merge base/base-tasks
         {"docs"    {:task 'com.biffweb.tasks/docs
                     :doc  "Generate API docs."}
          "publish" {:task 'com.biffweb.tasks/publish
                     :doc  "Publish library to Clojars with deps-deploy."}

          "code-quality"
          {:task 'com.biffweb.tasks/lib-code-quality
           :doc  "Format, lint, and test code, and generate API docs."}}))

(defn -main [& args]
  (apply biff.run/main tasks args))
