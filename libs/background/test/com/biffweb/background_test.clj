(ns com.biffweb.background-test
  (:require [clojure.test :refer [deftest is testing]]
            [com.biffweb.background :as background])
  (:import [java.time Instant]
           [java.util.concurrent CountDownLatch TimeUnit]))

(defn- await-latch [^CountDownLatch latch]
  (.await latch 2 TimeUnit/SECONDS))

(deftest scheduled-tasks-test
  (let [called   (promise)
        finished (promise)
        ctx      {:biff.background/tasks
                  [{:schedule    #(vector (Instant/now))
                    :task        #(deliver called %)
                    :on-finished #(deliver finished true)}]

                  :foo :bar}
        module   (background/tasks-module)
        result   ((:biff.core/start module) ctx)]
    (try
      (is (= :bar (:foo (deref called 2000 nil))))
      (is (some? (deref finished 2000 nil)))
      (finally
        ((:biff.core/stop module) result)))))

(deftest queues-process-jobs-in-priority-order-test
  (let [started  (CountDownLatch. 1)
        release  (CountDownLatch. 1)
        consumed (CountDownLatch. 3)
        seen     (atom [])
        consumer (fn [{:keys [biff.background/job
                              biff.background/queue]}]
                   (when (= :blocking (:id job))
                     (.countDown started)
                     (await-latch release))
                   (swap! seen conj [(:id job) queue])
                   (.countDown consumed))
        module   (background/queues-module)
        result   ((:biff.core/start module)
                  {:biff.background/queues
                   {:queue/email {:consumer consumer}}

                   :biff.background/stop-timeout 100})
        queue    (get-in result [:biff.background/queues :queue/email :queue])]
    (try
      (is (= [{:id :blocking}]
             (background/submit-jobs result :queue/email [{:id :blocking}])))
      (is (await-latch started))
      (is (= [{:id :low :biff.background/priority 20}
              {:id :high :biff.background/priority 1}]
             (background/submit-jobs
              result
              :queue/email
              [{:id :low :biff.background/priority 20}
               {:id :high :biff.background/priority 1}])))
      (.countDown release)
      (is (await-latch consumed))
      (is (= [:blocking :high :low] (mapv first @seen)))
      (is (every? #(identical? queue (second %)) @seen))
      (is (= {:continue true :processing #{}}
             @(get-in result [:biff.background/queues :queue/email :state])))
      (finally
        ((:biff.core/stop module) result)))))

(deftest submit-jobs-errors-test
  (testing "an unknown queue reports the available queues and submitted jobs"
    (let [jobs [{:id 1}]
          ex   (try
                 (background/submit-jobs
                  {:biff.background/queues
                   {:queue/email {:consumer identity}}}
                  :queue/missing
                  jobs)
                 nil
                 (catch clojure.lang.ExceptionInfo e
                   e))]
      (is (= "Queue not found" (ex-message ex)))
      (is (= {:biff.background/queue-id  :queue/missing
              :biff.background/jobs      jobs
              :biff.background/queue-ids [:queue/email]}
             (ex-data ex)))))
  (testing "inputs are validated"
    (is (thrown? AssertionError
                 (background/submit-jobs
                  {:biff.background/queues {}}
                  :unqualified
                  [])))
    (is (thrown? AssertionError
                 (background/submit-jobs
                  {:biff.background/queues {}}
                  :queue/email
                  [{:biff.background/priority "high"}])))))

(deftest integration-test
  (is (fn? (:biff.background.fx/submit-jobs background/fx-handlers)))
  (let [module      (background/module)
        modules-var (atom [{:biff.background/tasks  [:task-1]
                            :biff.background/queues {:queue/one :one}}
                           {:biff.background/tasks  [:task-2 :task-3]
                            :biff.background/queues {:queue/two :two}}])]
    (is (= background/fx-handlers (:biff.fx/handlers module)))
    (is (= {:biff.background/tasks  [:task-1 :task-2 :task-3]
            :biff.background/queues {:queue/one :one :queue/two :two}}
           ((:biff.core/init module) modules-var)))))
