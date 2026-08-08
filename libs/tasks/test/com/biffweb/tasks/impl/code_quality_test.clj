(ns com.biffweb.tasks.impl.code-quality-test
  (:require [clojure.test :refer [deftest is]]
            [com.biffweb.run :as biff.run]
            [com.biffweb.tasks :as tasks]))

(deftest app-code-quality-runs-tasks-in-order
  (let [calls (atom [])]
    (with-redefs [biff.run/run-task (fn [& args]
                                      (swap! calls conj (vec args)))]
      (tasks/app-code-quality))
    (is (= [["update" "--clj-kondo-files-only"]
            ["format"]
            ["lint"]
            ["test"]]
           @calls))))

(deftest lib-code-quality-runs-tasks-in-order
  (let [calls (atom [])]
    (with-redefs [biff.run/run-task (fn [& args]
                                      (swap! calls conj (vec args)))]
      (tasks/lib-code-quality))
    (is (= [["update" "--clj-kondo-files-only"]
            ["format"]
            ["docs"]
            ["lint"]
            ["test"]]
           @calls))))
