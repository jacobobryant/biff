(ns com.biffweb.tasks.lazy.clojure.stacktrace
  (:require [com.biffweb.task-runner.lazy :as lazy]))

(lazy/refer-many clojure.stacktrace [print-stack-trace])
