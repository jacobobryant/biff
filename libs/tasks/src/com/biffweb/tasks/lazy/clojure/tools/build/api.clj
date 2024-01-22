(ns com.biffweb.tasks.lazy.clojure.tools.build.api
  (:require [com.biffweb.task-runner.lazy :as lazy]))

(lazy/refer-many clojure.tools.build.api [delete copy-dir compile-clj uber create-basis])
