(ns com.biffweb.tasks.lazy.clojure.string
  (:refer-clojure :exclude [replace])
  (:require [com.biffweb.task-runner.lazy :as lazy]))

(lazy/refer-many clojure.string [includes? join lower-case split split-lines trim replace])
