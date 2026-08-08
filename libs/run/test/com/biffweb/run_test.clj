(ns com.biffweb.run-test
  (:require [clojure.test :as t :refer [deftest is testing]]
            [com.biffweb.run :as biff.run]))

(defn preserve-tasks [f]
  (let [tasks-var (ns-resolve 'com.biffweb.run 'tasks)
        original  @tasks-var]
    (try
      (f)
      (finally
        (alter-var-root tasks-var (constantly original))))))

(t/use-fixtures :each preserve-tasks)

(def calls (atom []))

(defn echo-task [& args]
  (swap! calls conj args)
  :done)

(alter-meta! #'echo-task assoc
             :doc (str "First line\n    second line\n      third line\n\n"
                       "    last line"))

(defn alpha-task [])

(defn fail-task []
  (throw (ex-info "failed" {:kind ::failure})))

(def tasks
  {"fail"  {:task 'com.biffweb.run-test/fail-task
            :doc  "throw an exception"}
   "echo"  {:task 'com.biffweb.run-test/echo-task
            :doc  "print arguments"}
   "alpha" {:task 'com.biffweb.run-test/alpha-task
            :doc  "first"}})

(defn- call-main-with [tasks & args]
  (let [out    (java.io.StringWriter.)
        err    (java.io.StringWriter.)
        result (binding [*out*              out
                         *err*              err
                         biff.run/*testing* true]
                 (apply biff.run/main tasks args))]
    {:result result
     :out    (str out)
     :err    (str err)}))

(defn- call-main [& args]
  (apply call-main-with tasks args))

(deftest main-prints-command-help
  (doseq [args [[] ["help"] ["--help"] ["-h"]]]
    (testing (pr-str args)
      (is (= {:result 0
              :out    (str "Available commands:\n"
                           "\n"
                           "  alpha - first\n"
                           "  echo  - print arguments\n"
                           "  fail  - throw an exception\n")
              :err    ""}
             (apply call-main args))))))

(deftest main-runs-task-with-arguments
  (reset! calls [])
  (is (= {:result 0 :out "" :err ""}
         (call-main "echo" "one" "two")))
  (is (= '(("one" "two")) @calls)))

(deftest main-prints-task-help
  (reset! calls [])
  (doseq [help-arg ["help" "--help" "-h"]]
    (testing help-arg
      (is (= {:result 0
              :out    (str "First line\n"
                           "second line\n"
                           "  third line\n"
                           "\n"
                           "last line\n")
              :err    ""}
             (call-main "echo" help-arg)))))
  (is (empty? @calls)))

(deftest main-invokes-task-with-help-arguments-when-configured
  (reset! calls [])
  (let [invoke-tasks (assoc-in tasks ["echo" :help] :invoke)]
    (doseq [help-arg ["help" "--help" "-h"]]
      (testing help-arg
        (is (= {:result 0 :out "" :err ""}
               (call-main-with invoke-tasks "echo" help-arg))))))
  (is (= '(("help") ("--help") ("-h")) @calls)))

(deftest main-reports-unrecognized-task
  (is (= {:result 1
          :out    ""
          :err    "Unrecognized task: missing\n"}
         (call-main "missing"))))

(deftest main-does-not-handle-task-exceptions-as-unrecognized
  (let [exception (binding [biff.run/*testing* true]
                    (try
                      (biff.run/main tasks "fail")
                      nil
                      (catch clojure.lang.ExceptionInfo e
                        e)))]
    (is (= "failed" (ex-message exception)))
    (is (= ::failure (:kind (ex-data exception))))))

(deftest run-task-uses-tasks-from-main
  (call-main "alpha")
  (reset! calls [])
  (is (= :done (biff.run/run-task "echo" "one" "two")))
  (is (= '(("one" "two")) @calls)))
