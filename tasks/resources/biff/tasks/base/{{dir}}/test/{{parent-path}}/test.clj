(ns {{parent-ns}}.test
  (:require
    [clojure.test :as t :refer [deftest is]]))

(deftest test-foo
  (is (= 4 (+ 2 2))))

(defn run [_]
  (t/run-all-tests #"{{parent-ns}}.test.*"))
