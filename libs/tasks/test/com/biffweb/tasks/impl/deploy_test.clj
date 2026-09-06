(ns com.biffweb.tasks.impl.deploy-test
  (:require [clojure.test :refer [deftest is]]
            [com.biffweb.tasks.impl.deploy :as deploy]
            [com.biffweb.tasks.impl.util :as util]))

(deftest deploy-validates-prod-alias-first
  (with-redefs [util/ensure-prod-alias!
                #(throw (ex-info "missing prod alias" {}))]
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"missing prod alias"
                          (deploy/deploy)))))
