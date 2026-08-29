(ns com.biffweb.background.impl
  (:require [chime.core :as chime]
            [clojure.tools.logging :as log]
            [com.biffweb.core :as biff.core])
  (:import [java.util.concurrent
            Callable
            Executors
            PriorityBlockingQueue
            TimeUnit]))

;;;; scheduled tasks ===========================================================

(defn- start-scheduled-tasks [{:keys [biff.background/tasks] :as ctx}]
  (assoc ctx ::schedulers
         (mapv (fn [{:keys [schedule task] :as config}]
                 (chime/chime-at (schedule)
                                 (fn [_] (task ctx))
                                 (select-keys config
                                              [:error-handler :on-finished])))
               tasks)))

(defn- stop-scheduled-tasks [{::keys [schedulers]}]
  (run! #(.close %) schedulers))

;;;; queues ====================================================================

(defn- default-queue []
  (PriorityBlockingQueue. 11 (fn [a b]
                               (compare (:biff.background/priority a 10)
                                        (:biff.background/priority b 10)))))

(defn- consume [ctx {:keys [queue consumer state]} index]
  (while (:continue @state)
    (when-some [job (.poll queue 1 TimeUnit/SECONDS)]
      (try
        (swap! state update :processing conj index)
        (consumer (assoc ctx
                         :biff.background/job job
                         :biff.background/queue queue))
        (catch Exception e
          (log/error e "Exception while consuming background job"))
        (finally
          (swap! state update :processing disj index)))
      (flush))))

(defn- stop-queues [{:biff.background/keys [stop-timeout queues]
                     :or                   {stop-timeout 10000}}]
  (let [queue-maps (vals queues)
        timeout    (+ (System/nanoTime) (* stop-timeout (Math/pow 10 6)))]
    (run! #(swap! (:state %) assoc :continue false) queue-maps)
    (run! #(.shutdown (:executor %)) queue-maps)
    (doseq [{:keys [executor]} queue-maps
            :let               [time-left (- timeout (System/nanoTime))]
            :when              (< 0 time-left)]
      (.awaitTermination executor time-left TimeUnit/NANOSECONDS))
    (run! #(.shutdownNow (:executor %)) queue-maps)))

(defn- init-queue [queue-map]
  (let [n-threads (get queue-map :n-threads 1)]
    (merge {:n-threads n-threads
            :queue     (default-queue)}
           queue-map
           {:executor (Executors/newFixedThreadPool n-threads)
            :state    (atom {:continue   true
                             :processing #{}})})))

(defn- start-queues [{:keys [biff.background/queues] :as ctx}]
  (let [queues (update-vals queues init-queue)
        ctx    (assoc ctx :biff.background/queues queues)]
    (doseq [{:keys [executor n-threads] :as queue-map} (vals queues)

            index (range n-threads)]
      (.submit executor ^Callable #(consume ctx queue-map index)))
    ctx))

(defn submit-jobs [{:biff.background/keys [queues]} queue-id jobs]
  (biff.core/validate {:biff.background/queues   queues
                       :biff.background/queue-id queue-id
                       :biff.background/jobs     jobs})
  (if-some [queue (get-in queues [queue-id :queue])]
    (do
      (run! #(.add queue %) jobs)
      jobs)
    (throw (ex-info "Queue not found"
                    {:biff.background/queue-id  queue-id
                     :biff.background/jobs      jobs
                     :biff.background/queue-ids (vec (keys queues))}))))

;;;; integration ===============================================================

(def fx-handlers
  {:biff.background.fx/submit-jobs submit-jobs})

(defn- init-scheduled-tasks [modules-var]
  {:biff.background/tasks (into []
                                (mapcat :biff.background/tasks)
                                @modules-var)})

(defn- init-queues [modules-var]
  {:biff.background/queues (into {}
                                 (mapcat :biff.background/queues)
                                 @modules-var)})

(defn tasks-module []
  {:biff.core/id    :biff.background/tasks-module
   :biff.core/start start-scheduled-tasks
   :biff.core/stop  stop-scheduled-tasks
   :biff.core/init  init-scheduled-tasks})

(defn queues-module []
  {:biff.core/id     :biff.background/queues-module
   :biff.core/start  start-queues
   :biff.core/stop   stop-queues
   :biff.core/init   init-queues
   :biff.fx/handlers fx-handlers})

(defn module []
  {:biff.core/id     :biff.background/module
   :biff.core/start  (comp start-scheduled-tasks start-queues)
   :biff.core/stop   (fn [ctx]
                       (stop-scheduled-tasks ctx)
                       (stop-queues ctx))
   :biff.core/init   (fn [modules-var]
                       (merge (init-scheduled-tasks modules-var)
                              (init-queues modules-var)))
   :biff.fx/handlers fx-handlers})
