(ns com.biffweb.tasks.impl.nrepl-test
  (:require [clojure.test :refer [deftest is]]
            [com.biffweb.tasks :as tasks]
            [com.biffweb.tasks.impl.util :as util]
            [nrepl.cmdline :as nrepl.cmdline]))

(deftest nrepl-adds-configured-options
  (let [args (atom nil)]
    (with-redefs [util/read-config
                  (constantly {:biff.tasks/nrepl-port 7888})

                  nrepl.cmdline/-main (fn [& actual-args]
                                        (reset! args (vec actual-args)))]
      (tasks/nrepl "--bind" "localhost"))
    (is (= ["--port" "7888"
            "--middleware" "[cider.nrepl/cider-middleware]"
            "--bind" "localhost"]
           @args))))

(deftest double-dash-passes-options-through
  (let [args (atom nil)]
    (with-redefs [util/read-config
                  (constantly {:biff.tasks/nrepl-port 7888})

                  nrepl.cmdline/-main (fn [& actual-args]
                                        (reset! args (vec actual-args)))]
      (tasks/nrepl "--" "--help"))
    (is (= ["--help"] @args))))
