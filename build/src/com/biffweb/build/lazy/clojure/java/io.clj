(ns com.biffweb.build.lazy.clojure.java.io
  (:require [com.biffweb.build.util.lazy :as lazy]))

(lazy/refer-many clojure.java.io [copy file make-parents reader])
