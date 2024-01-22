(ns com.biffweb.tasks.lazy.clojure.java.shell
  (:refer-clojure :exclude [replace])
  (:require [com.biffweb.task-runner.lazy :as lazy]))

(lazy/refer-many clojure.java.shell [sh])
