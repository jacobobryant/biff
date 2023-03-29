;; The code in this file has been copied from https://github.com/jakemcc/reload
;; and is licensed under the EPL version 1.0 (or any later version).
(ns com.biffweb.impl.util.reload
  (:require [clojure.repl :as repl]
            [clojure.tools.namespace.dir :as dir]
            [clojure.tools.namespace.reload :as reload]
            clojure.tools.namespace.repl
            [clojure.tools.namespace.track :as track]))

(defonce global-tracker (atom (track/tracker)))

(def remove-disabled #'clojure.tools.namespace.repl/remove-disabled)

(defn- print-pending-reloads [tracker]
  (when-let [r (seq (::track/load tracker))]
    (prn :reloading r)))

(defn print-and-return [tracker]
  (if-let [e (::reload/error tracker)]
    (do (when (thread-bound? #'*e)
          (set! *e e))
        (prn :error-while-loading (::reload/error-ns tracker))
        (repl/pst e)
        e)
    :ok))

(defn refresh [tracker directories]
  (let [new-tracker (apply dir/scan tracker directories)
        new-tracker (remove-disabled new-tracker)]
    (print-pending-reloads new-tracker)
    (let [new-tracker (reload/track-reload (assoc new-tracker ::track/unload []))]
      (print-and-return new-tracker)
      new-tracker)))
