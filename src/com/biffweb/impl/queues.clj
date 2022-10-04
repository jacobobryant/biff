(ns com.biffweb.impl.queues
  (:require [com.biffweb.impl.misc :as misc]
            [com.biffweb.impl.util :as util])
  (:import [java.util.concurrent
            PriorityBlockingQueue
            TimeUnit
            Executors
            Callable]))

(defn- consume [sys {:keys [id queue consumer continue]}]
  (while @continue
    (when-some [job (.poll queue 1 TimeUnit/SECONDS)]
      (util/catchall-verbose
       (consumer (merge (misc/merge-context sys)
                        {:biff/job job
                         :biff/queue queue})))
      (flush))))

(defn- stop [{:keys [biff.queues/stop-timeout]
              :or {stop-timeout 10000}} configs]
  (let [timeout (+ (System/nanoTime) (* stop-timeout (Math/pow 10 6)))]
    (some-> (first configs)
            :continue
            (reset! false))
    (run! #(.shutdown (:executor %)) configs)
    (doseq [{:keys [executor]} configs
            :let [time-left (- timeout (System/nanoTime))]
            :when (< 0 time-left)]
      (.awaitTermination executor time-left TimeUnit/NANOSECONDS))
    (run! #(.shutdownNow (:executor %)) configs)))

(defn- default-queue []
  (PriorityBlockingQueue. 11 (fn [a b]
                               (compare (:biff/priority a 10)
                                        (:biff/priority b 10)))))

(defn- init [{:keys [biff/features
                     biff.queues/enabled-ids] :as sys}]
  (let [continue (atom true)]
    (->> @features
         (mapcat :queues)
         (filter (fn [q]
                   (or (nil? enabled-ids) (contains? enabled-ids (:id q)))))
         (map (fn [{:keys [id n-threads consumer queue-fn]
                    :or {n-threads 1
                         queue-fn default-queue}}]
                {:id id
                 :n-threads n-threads
                 :consumer consumer
                 :queue (queue-fn)
                 :executor (Executors/newFixedThreadPool n-threads)
                 :continue continue})))))

(defn use-queues [sys]
  (let [configs (init sys)
        queues (into {} (map (juxt :id :queue) configs))
        sys (-> sys
                (assoc :biff/queues queues)
                (update :biff/stop conj #(stop sys configs)))]
    (doseq [{:keys [executor n-threads] :as config} configs
            _ (range n-threads)]
      (.submit executor ^Callable #(consume sys config)))
    sys))

(defn submit-job [sys queue-id job]
  (.add (get-in sys [:biff/queues queue-id]) job))

(defn submit-job-for-result [{:keys [biff.queues/result-timeout]
                              :or {result-timeout 20000}
                              :as sys}
                             queue-id
                             job]
  (let [p (promise)
        result (if result-timeout
                 (delay (deref p result-timeout ::timeout))
                 p)]
    (submit-job sys queue-id (assoc job :biff/callback #(deliver p %)))
    (delay (cond
            (= @result ::timeout)
            (throw (ex-info "Timed out while waiting for job result" {:queue-id queue-id :job job}))

            (instance? Exception @result)
            (throw @result)

            :else
            @result))))
