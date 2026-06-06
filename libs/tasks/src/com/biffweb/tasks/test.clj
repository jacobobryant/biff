(ns com.biffweb.tasks.test
  (:refer-clojure :exclude [test])
  (:require [clojure.test :as test]
            [com.biffweb.tasks.util :as util]
            [cognitect.test-runner :as test-runner]))

(defn run-tests []
  (util/read-config) ; set the system properties
  (let [failures        (atom [])
        original-report test/report
        result          (binding [test/report (fn [m]
                                                (when (#{:fail :error} (:type m))
                                                  (swap! failures conj m))
                                                (original-report m))]
                          (test-runner/test {:dir (into #{} (util/deps-paths))}))]
    (merge {:fail 0 :error 0}
           result
           {:failures @failures})))

(defn test
  "Runs project tests."
  []
  (let [{:keys [fail error] :as result} (run-tests)]
    (when-not (zero? (+ fail error))
      (throw (ex-info "Tests failed" result)))))
