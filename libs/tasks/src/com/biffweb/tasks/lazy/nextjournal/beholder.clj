(ns com.biffweb.tasks.lazy.nextjournal.beholder
  (:refer-clojure :exclude [get])
  (:require [com.biffweb.task-runner.lazy :as lazy]))

(lazy/refer-many nextjournal.beholder [watch])
