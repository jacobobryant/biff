(ns com.biffweb.build.lazy.clojure.string
  (:refer-clojure :exclude [replace])
  (:require [com.biffweb.build.util.lazy :as lazy]))

(lazy/refer-many clojure.string [includes? join lower-case split split-lines trim replace])
