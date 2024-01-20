(ns com.biffweb.tasks.lazy.babashka.fs
  (:require [com.biffweb.task-runner.lazy :as lazy]))

(lazy/refer-many babashka.fs [exists? which set-posix-file-permissions delete-tree parent])
