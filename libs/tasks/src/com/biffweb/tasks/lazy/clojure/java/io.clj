(ns com.biffweb.tasks.lazy.clojure.java.io
  (:require [com.biffweb.task-runner.lazy :as lazy]))

(lazy/refer-many clojure.java.io [copy file make-parents reader resource])
