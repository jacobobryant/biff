(ns com.biffweb.tasks.lazy.com.biffweb.config
  (:require [com.biffweb.task-runner.lazy :as lazy]))

(lazy/refer-many com.biffweb.config [use-aero-config])
