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

(defn use-scheduled-tasks [{:keys [biff.background/tasks] :as ctx}]
  (reduce
   (fn [ctx {:keys [schedule task] :as config}]
     (let [scheduler (chime/chime-at (schedule)
                                     (fn [_] (task ctx))
                                     (select-keys config
                                                  [:error-handler
                                                   :on-finished]))]
       (update ctx :biff.core/stop conj #(.close scheduler))))
   ctx
   tasks))

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

(defn- stop [{:keys [biff.background/stop-timeout]
              :or   {stop-timeout 10000}}
             queue-maps]
  (let [timeout (+ (System/nanoTime) (* stop-timeout (Math/pow 10 6)))]
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

(defn use-queues [{:keys [biff.background/queues] :as ctx}]
  (let [queues (update-vals queues init-queue)
        ctx    (-> ctx
                   (assoc :biff.background/queues queues)
                   (update :biff.core/stop conj #(stop ctx (vals queues))))]
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

(defn module []
  {:biff.core/init
   (fn [modules-var]
     {:biff.background/tasks  (into []
                                    (mapcat :biff.background/tasks)
                                    @modules-var)
      :biff.background/queues (into {}
                                    (mapcat :biff.background/queues)
                                    @modules-var)})

   :biff.fx/handlers fx-handlers})
