(ns com.biffweb.background
  (:require [chime.core :as chime]
            [clojure.tools.logging :as log]
            [com.biffweb.core :as biff.core])
  (:import [java.util.concurrent
            Callable
            Executors
            PriorityBlockingQueue
            TimeUnit]))

(biff.core/register
 {:biff.background/job         'map?
  :biff.background/jobs        [:sequential 'map?]
  :biff.background/priority    'number?
  :biff.background/queue       'some?
  :biff.background/queue-id    'any?
  :biff.background/queue-ids   [:sequential 'any?]
  :biff.background/queues      'map?
  :biff.background/submit-jobs 'ifn?
  :biff.background/tasks       [:sequential 'map?]})

(defn- default-queue []
  (PriorityBlockingQueue. 11 (fn [a b]
                               (compare (:biff.background/priority a 10)
                                        (:biff.background/priority b 10)))))

(defn- scheduler-options [{:keys [error-handler on-finished]}]
  (cond-> {}
    error-handler (assoc :error-handler error-handler)
    on-finished (assoc :on-finished on-finished)))

(defn use-scheduled-tasks [{:keys [biff.background/tasks] :as ctx}]
  (reduce
   (fn [ctx {:keys [schedule task] :as config}]
     (when-not schedule
       (throw (ex-info "Missing scheduled task schedule" {:task config})))
     (when-not task
       (throw (ex-info "Missing scheduled task function" {:task config})))
     (let [scheduler (chime/chime-at (schedule)
                                     (fn [_] (task ctx))
                                     (not-empty (scheduler-options config)))]
       (update ctx :biff.core/stop (fnil conj []) #(.close scheduler))))
   ctx
   tasks))

(defn- consume [ctx {:keys [queue consumer continue]}]
  (while @continue
    (when-some [job (.poll queue 1 TimeUnit/SECONDS)]
      (try
        (consumer (assoc ctx
                         :biff.background/job job
                         :biff.background/queue queue))
        (catch Exception e
          (log/error e "Exception while consuming background job")))
      (flush))))

(defn- stop [{:keys [biff.background/stop-timeout]
              :or   {stop-timeout 10000}}
             configs]
  (let [timeout (+ (System/nanoTime) (* stop-timeout (Math/pow 10 6)))]
    (some-> (first configs)
            :continue
            (reset! false))
    (run! #(.shutdown (:executor %)) configs)
    (doseq [{:keys [executor]} configs
            :let               [time-left (- timeout (System/nanoTime))]
            :when              (< 0 time-left)]
      (.awaitTermination executor time-left TimeUnit/NANOSECONDS))
    (run! #(.shutdownNow (:executor %)) configs)))

(defn- init-queues [{:keys [biff.background/queues]}]
  (let [continue (atom true)]
    (mapv (fn [[id {:keys [n-threads consumer queue]
                    :or   {n-threads 1
                           queue     (default-queue)}
                    :as   config}]]
            (when-not consumer
              (throw (ex-info "Missing queue consumer"
                              {:biff.background/queue-id id
                               :queue                    config})))
            {:id        id
             :n-threads n-threads
             :consumer  consumer
             :queue     queue
             :executor  (Executors/newFixedThreadPool n-threads)
             :continue  continue})
          queues)))

(defn use-queues [ctx]
  (let [configs (init-queues ctx)
        queues  (into {} (map (juxt :id :queue) configs))
        ctx     (-> ctx
                    (assoc :biff.background/queues queues)
                    (update :biff.core/stop (fnil conj []) #(stop ctx configs)))]
    (doseq [{:keys [executor n-threads] :as config} configs
            _                                       (range n-threads)]
      (.submit executor ^Callable #(consume ctx config)))
    ctx))

(defn submit-jobs [ctx queue-id jobs]
  (let [jobs (vec jobs)]
    (if-some [queue (get-in ctx [:biff.background/queues queue-id])]
      (do
        (run! #(.add queue %) jobs)
        jobs)
      (throw (ex-info "Queue not found"
                      {:biff.background/queue-id  queue-id
                       :biff.background/jobs      jobs
                       :biff.background/queue-ids (-> ctx
                                                      :biff.background/queues
                                                      keys
                                                      vec)})))))

(def fx-handlers
  {:biff.background/submit-jobs submit-jobs})

(defn module []
  {:biff.core/init
   (fn [modules-var]
     {:biff.background/tasks
      (->> @modules-var
           (mapcat (fn [module]
                     (:biff.background/tasks module [])))
           vec)
      :biff.background/queues
      (reduce (fn [acc module]
                (reduce-kv (fn [acc id config]
                             (assoc acc
                                    id
                                    (merge {:n-threads 1
                                            :queue     (default-queue)}
                                           config)))
                           acc
                           (:biff.background/queues module {})))
              {}
              @modules-var)})
   :biff.fx/handlers fx-handlers})
