(ns com.biffweb.build.lazy.clojure.stacktrace
  (:require [com.biffweb.build.util.lazy :as lazy]))

(lazy/refer-many clojure.stacktrace [print-stack-trace])
