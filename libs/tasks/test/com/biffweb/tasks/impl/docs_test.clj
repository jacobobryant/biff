(ns com.biffweb.tasks.impl.docs-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [com.biffweb.tasks.impl.docs :as docs]))

(deftest escapes-asterisks-in-var-headings
  (is (= "### \\*value\\*"
         (first
          (str/split-lines
           (#'docs/var-section
            {:name '*value* :doc "A value."}
            "source.clj"))))))
