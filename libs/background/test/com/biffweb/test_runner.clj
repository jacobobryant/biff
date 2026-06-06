(ns com.biffweb.test-runner
  (:require [clojure.test :as t]
            [com.biffweb.background-test]))

(defn -main [& _args]
  (let [{:keys [fail error]} (t/run-tests 'com.biffweb.background-test)]
    (when (pos? (+ fail error))
      (System/exit 1))))
