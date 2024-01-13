(ns com.biffweb.build.lazy.clojure.java.shell
  (:refer-clojure :exclude [replace])
  (:require [com.biffweb.build.util.lazy :as lazy]))

(lazy/refer-many clojure.java.shell [sh])
