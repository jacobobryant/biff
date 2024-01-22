(ns com.biffweb.tasks.lazy.nrepl.cmdline
  (:require [com.biffweb.task-runner.lazy :as lazy]))

(lazy/refer-many nrepl.cmdline [-main])
