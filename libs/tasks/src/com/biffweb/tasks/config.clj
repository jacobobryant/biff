(ns com.biffweb.tasks.config
  (:refer-clojure :exclude [read])
  (:require [com.biffweb.config :as config]))

(def read
  (memoize (fn [] (config/use-aero-config {}))))
