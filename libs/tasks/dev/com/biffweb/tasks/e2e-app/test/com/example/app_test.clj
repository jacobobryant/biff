(ns com.example.app-test
  (:require [clojure.test :refer [deftest is]]))

(deftest sample-test
  (is (= 4 (+ 2 2))))
