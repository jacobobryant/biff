(ns com.biffweb.demo.app.archive
  (:require [com.biffweb.demo.lib.middleware :as mid]
            [com.biffweb.demo.lib.ui :as ui]
            [com.biffweb.fx :as biff.fx]
            [com.biffweb.ring :refer [defroute]]
            [com.biffweb.sqlite :as biff.sqlite])
  (:import [java.time Instant ZoneOffset ZonedDateTime]))

(def queue-id :todo/archive)

(defn- can-manually-archive? [{:keys [biff.admin/user-id session]}]
  (let [configured-admin-id (some-> user-id str not-empty)
        current-user-id     (some-> session :uid str)]
    (or (nil? configured-admin-id)
        (= configured-admin-id current-user-id))))

(defn- next-schedule-tick [now]
  (let [minute (.getMinute now)
        delta  (mod (- 5 (mod minute 5)) 5)
        delta  (if (zero? delta) 5 delta)]
    (-> now
        (.plusMinutes delta)
        (.withSecond 0)
        (.withNano 0))))

(defn every-five-minutes []
  (iterate #(.plusMinutes ^ZonedDateTime % 5)
           (next-schedule-tick (ZonedDateTime/now ZoneOffset/UTC))))

(def queue-archive-job-states
  {:start
   (fn [_]
     {:todo-rows    [:biff.sqlite.fx/execute
                     {:select   [:todo/id]
                      :from     :todo
                      :where    [:= :todo/archived false]
                      :order-by [[:todo/created-at :asc]
                                 [:todo/id :asc]]}]
      :biff.fx/next :submit})

   :submit
   (fn [{:keys [todo-rows]}]
     (let [jobs (->> todo-rows
                     (mapv :todo/id)
                     (partition-all 3)
                     (mapv (fn [todo-ids]
                             {:todo/archive-ids (vec todo-ids)})))]
       (if (seq jobs)
         {:archive-jobs           [:biff.background/submit-jobs queue-id jobs]
          :todo.archive/batches   (count jobs)
          :todo.archive/submitted (reduce + (map #(count (:todo/archive-ids %))
                                                 jobs))}
         {:todo.archive/batches   0
          :todo.archive/submitted 0})))})

(def queue-archive-jobs!
  (biff.fx/machine
   ::queue-archive-jobs
   :start (:start queue-archive-job-states)
   :submit (:submit queue-archive-job-states)))

(defn archive-batch!
  [{:keys [biff.background/job] :as ctx}]
  (let [todo-ids (:todo/archive-ids job)]
    (when (seq todo-ids)
      (let [now (Instant/now)]
        (biff.sqlite/execute ctx {:update :todo
                                  :set    {:todo/archived    true
                                           :todo/archived-at now
                                           :todo/updated-at  now}
                                  :where  [:and
                                           [:in :todo/id todo-ids]
                                           [:= :todo/archived false]]})))))

(defroute archive-now-route "/app/archive"
  :post
  (fn [req]
    (if (can-manually-archive? req)
      (merge ((:start queue-archive-job-states) req)
             {:biff.fx/next :archive-now-submit})
      {:status  403
       :headers {"content-type" "text/plain; charset=utf-8"}
       :body    "Forbidden"}))

  :archive-now-submit
  (fn [ctx]
    (merge ((:submit queue-archive-job-states) ctx)
           {:biff.fx/return (ui/no-content)})))

(def module
  {:biff.background/tasks
   [{:schedule every-five-minutes
     :task     queue-archive-jobs!}]

   :biff.background/queues
   {queue-id {:n-threads 1
              :consumer  archive-batch!}}

   :biff.ring/routes
   [["" {:middleware [mid/wrap-signed-in]}
     archive-now-route]]})
