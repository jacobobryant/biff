(ns com.biffweb.tasks.lazy.hato.client
  (:refer-clojure :exclude [get])
  (:require [com.biffweb.task-runner.lazy :as lazy]))

(lazy/refer-many hato.client [get])
