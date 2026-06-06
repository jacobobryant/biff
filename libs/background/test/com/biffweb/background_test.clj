(ns com.biffweb.background-test
  (:require [chime.core :as chime]
            [clojure.test :refer [deftest is testing]]
            [com.biffweb.background :as background])
  (:import [java.util.concurrent PriorityBlockingQueue]))

(deftest module-collects-task-and-queue-config
  (let [task-a {:schedule (constantly [:a]) :task identity}
        task-b {:schedule (constantly [:b]) :task identity}
        init   ((:biff.core/init (background/module))
                (atom [{:biff.background/tasks  [task-a]
                        :biff.background/queues {:default {:consumer identity}}}
                       {:biff.background/tasks  [task-b]
                        :biff.background/queues {:slow {:consumer  identity
                                                        :n-threads 2}}}]))]
    (is (= [task-a task-b] (:biff.background/tasks init)))
    (is (= 1 (get-in init [:biff.background/queues :default :n-threads])))
    (is (= 2 (get-in init [:biff.background/queues :slow :n-threads])))
    (is (instance? PriorityBlockingQueue
                   (get-in init [:biff.background/queues :default :queue])))))

(deftest use-scheduled-tasks-passes-chime-options
  (let [captured      (atom nil)
        ran-with      (promise)
        closed?       (atom false)
        scheduler     (reify java.io.Closeable
                        (close [_] (reset! closed? true)))
        error-handler (fn [_ _])
        on-finished   (fn [_ _])]
    (with-redefs [chime/chime-at (fn [schedule f opts]
                                   (reset! captured {:schedule schedule
                                                     :opts     opts})
                                   (f nil)
                                   scheduler)]
      (let [ctx (background/use-scheduled-tasks
                 {:biff.core/stop        []
                  :biff.background/tasks [{:schedule      (constantly [:tick])
                                           :task          #(deliver ran-with %)
                                           :error-handler error-handler
                                           :on-finished   on-finished}]})]
        (is (= {:schedule [:tick]
                :opts     {:error-handler error-handler
                           :on-finished   on-finished}}
               @captured))
        (is (= 1 (count (:biff.background/tasks @ran-with))))
        ((first (:biff.core/stop ctx)))
        (is @closed?)))))

(defn- queue-ctx [seen done]
  (background/use-queues
   {:biff.core/stop []
    :biff.background/queues
    {:default
     {:consumer (fn [{:keys [biff.background/job]}]
                  (let [jobs (swap! seen conj job)]
                    (when (= 2 (count jobs))
                      (deliver done jobs))))}}}))

(deftest queues-run-jobs
  (let [jobs [{:value 2 :biff.background/priority 2}
              {:value 1 :biff.background/priority 1}]]
    (testing "direct helper"
      (let [seen (atom [])
            done (promise)
            ctx  (queue-ctx seen done)]
        (try
          (is (= jobs
                 (background/submit-jobs ctx :default jobs)))
          (is (= (set jobs)
                 (set @done)))
          (finally
            (run! #(%)
                  (:biff.core/stop ctx))))))
    (testing "biff.fx handler"
      (let [seen (atom [])
            done (promise)
            ctx  (queue-ctx seen done)]
        (try
          (is (= jobs
                 ((:biff.background/submit-jobs background/fx-handlers)
                  ctx
                  :default
                  jobs)))
          (is (= (set jobs)
                 (set @done)))
          (finally
            (run! #(%)
                  (:biff.core/stop ctx))))))))
