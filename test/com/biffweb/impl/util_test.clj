(ns com.biffweb.impl.util-test
  (:require [clojure.test :refer [deftest is]]
            [com.biffweb.impl.util :as util]))

(def test-modules [:foo :bar])

(deftest ctx->modules
  (is (= [:foo :bar]
         (util/ctx->modules {:biff/modules #'test-modules}))))
