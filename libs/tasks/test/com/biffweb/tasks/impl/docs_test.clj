(ns com.biffweb.tasks.impl.docs-test
  (:require [clojure.test :refer [deftest is]]
            [com.biffweb.tasks.impl.docs :as docs]))

(deftest escapes-asterisks-in-var-headings
  (is (= "### \\*value\\*"
         (first
          (clojure.string/split-lines
           (#'docs/var-section
            {:name '*value* :doc "A value."}
            "source.clj"))))))
