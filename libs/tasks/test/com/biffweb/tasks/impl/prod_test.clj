(ns com.biffweb.tasks.impl.prod-test
  (:require [clojure.test :refer [deftest is]]
            [com.biffweb.tasks.impl.prod :as prod]
            [com.biffweb.tasks.impl.util :as util]))

(deftest prod-logs-follows-journal-directly
  (let [ctx   {:biff.tasks/deployment-name "my-app"
               :biff.tasks/domain          "example.com"}
        calls (atom [])]
    (with-redefs [util/read-config (constantly ctx)
                  util/ssh-run     (fn [& args] (swap! calls conj (vec args)))]
      (prod/prod-logs "20"))
    (is (= [[ctx "journalctl" "-u" "my-app" "-n" "20" "-f" "--no-pager"]]
           @calls))))

(deftest prod-setup-validates-prod-alias-first
  (with-redefs [util/ensure-prod-alias!
                #(throw (ex-info "missing prod alias" {}))]
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"missing prod alias"
                          (prod/prod-setup)))))
