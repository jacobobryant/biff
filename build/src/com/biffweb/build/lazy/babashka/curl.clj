(ns com.biffweb.build.lazy.babashka.curl
  (:refer-clojure :exclude [get])
  (:require [com.biffweb.build.util.lazy :as lazy]))

(lazy/refer-many babashka.curl [get])
