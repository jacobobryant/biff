(ns com.biffweb.tasks.impl.test-test
  (:require [clojure.test :refer [deftest is testing]]
            [com.biffweb.tasks.impl.test :as tasks.test]))

(deftest test-task-test
  (testing "arguments are passed to Kaocha"
    (let [args (atom nil)]
      (with-redefs [requiring-resolve
                    (fn [resolved-symbol]
                      (is (= 'kaocha.runner/-main* resolved-symbol))
                      (fn [& actual-args]
                        (reset! args actual-args)
                        0))]
        (tasks.test/test "--focus" "unit"))
      (is (= ["--focus" "unit"] @args))))
  (testing "a nonzero Kaocha result throws"
    (with-redefs [requiring-resolve (constantly (constantly 7))]
      (let [exception (try
                        (tasks.test/test)
                        nil
                        (catch clojure.lang.ExceptionInfo e e))]
        (is (= "Tests failed" (ex-message exception)))
        (is (= {:exit 7} (ex-data exception)))))))
