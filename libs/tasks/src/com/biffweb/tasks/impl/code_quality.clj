(ns com.biffweb.tasks.impl.code-quality
  (:require [com.biffweb.run :refer [run-task]]))

(defn app-code-quality []
  (run-task "update" "--clj-kondo-files-only")
  (run-task "format")
  (run-task "lint")
  (run-task "test"))

(defn lib-code-quality []
  (run-task "update" "--clj-kondo-files-only")
  (run-task "format")
  (run-task "docs")
  (run-task "lint")
  (run-task "test"))
