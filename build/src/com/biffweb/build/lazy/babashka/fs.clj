(ns com.biffweb.build.lazy.babashka.fs
  (:require [com.biffweb.build.util.lazy :as lazy]))

(lazy/refer-many babashka.fs [exists? which set-posix-file-permissions])
