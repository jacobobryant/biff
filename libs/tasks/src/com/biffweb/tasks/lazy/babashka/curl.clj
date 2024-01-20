(ns com.biffweb.tasks.lazy.babashka.curl
  (:refer-clojure :exclude [get])
  (:require [com.biffweb.task-runner.lazy :as lazy]))

(lazy/refer-many babashka.curl [get])
