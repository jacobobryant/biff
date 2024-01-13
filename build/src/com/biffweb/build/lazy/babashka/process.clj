(ns com.biffweb.build.lazy.babashka.process
  (:require [com.biffweb.build.util.lazy :as lazy]))

(lazy/refer-many babashka.process [shell process])
