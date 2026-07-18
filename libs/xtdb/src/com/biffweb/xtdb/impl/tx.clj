(ns com.biffweb.xtdb.impl.tx
  (:require [clojure.string :as str]
            [com.biffweb.core :as biff.core]
            [com.biffweb.xtdb.impl.util :as util]
            [next.jdbc :as jdbc]
            [xtdb.api :as xt]
            [xtdb.basis :as xt.basis]
            [xtdb.protocols :as xtp]
            [xtdb.tx-ops :as tx-ops])
  (:import [java.sql BatchUpdateException]
           [java.time Instant]
           [java.util UUID]
           [java.util.concurrent LinkedBlockingQueue TimeUnit]
           [xtdb.api DataSource]
           [xtdb.util NormalForm]))

;; Query helpers

(defn q
  ([ctx query]
   (q ctx query nil))
  ([{:biff.xtdb/keys [node snapshot-token]} query opts]
   (xt/q node
         (util/format-query query)
         (cond-> (or opts {})
           snapshot-token (assoc :snapshot-token snapshot-token)))))

(defn latest-system-time [node]
  (some-> (get-in (xt/status node) [:latest-completed-txs "xtdb" 0])
          :system-time
          Instant/from))

(defn snapshot-token [node]
  (when-some [system-time (latest-system-time node)]
    (xt.basis/->time-basis-str {"xtdb" [system-time]})))

;; Transaction notifications

(defn- notify-on-tx [ctx system-time]
  (when-let [on-tx (:biff.core/on-tx ctx)]
    (on-tx (assoc ctx :biff.xtdb/latest-system-time system-time))))

(defn- await-token [node token]
  (xt/q node ["SELECT 1 AS ok"] {:await-token token})
  (latest-system-time node))

(defn start-listener [node ctx]
  (let [continue (atom true)
        done     (promise)
        queue    (LinkedBlockingQueue.)
        seen     (atom (latest-system-time node))]
    (future
      (while @continue
        (let [item (.poll queue 1 TimeUnit/SECONDS)]
          (cond
            (inst? item)
            (reset! seen item)

            (:await-token item)
            (when-let [system-time (await-token node (:await-token item))]
              (when (not= system-time @seen)
                (reset! seen system-time)
                (notify-on-tx ctx system-time)))

            :else
            (when-let [latest (latest-system-time node)]
              (when (not= latest @seen)
                (reset! seen latest)
                (notify-on-tx ctx latest))))))
      (deliver done nil))
    {:poll-now (fn
                 ([] (.offer queue true))
                 ([x]
                  (when (inst? x)
                    (reset! seen x))
                  (.offer queue x)))
     :stop     (fn []
                 (reset! continue false)
                 (.offer queue true)
                 (deref done 10000 nil))}))

;; Custom transaction operations

(defn- where-sql [kvs]
  (if (seq kvs)
    (str " WHERE " (util/where-and kvs))
    ""))

(defn- assert-count-at-most-one [table kvs]
  [:sql (str "ASSERT (SELECT COUNT(*) <= 1 FROM "
             (util/sql-table table)
             (where-sql kvs)
             ")")
   (util/sql-args kvs)])

(defn- assert-not-exists [table kvs]
  [:sql (str "ASSERT NOT EXISTS (SELECT 1 FROM "
             (util/sql-table table)
             (where-sql kvs)
             ")")
   (util/sql-args kvs)])

(defn- assert-exists [table id]
  [:sql (str "ASSERT EXISTS (SELECT 1 FROM "
             (util/sql-table table)
             " WHERE _id = ?)")
   [id]])

(defmulti expand-op (fn [_ctx op]
                      (when (and (vector? op)
                                 (qualified-keyword? (first op)))
                        (first op))))

(defmethod expand-op :default [_ctx op]
  [op])

(defmethod expand-op :biff/assert-unique [_ctx [_ table kvs]]
  [(assert-count-at-most-one table kvs)])

(defmethod expand-op :biff/upsert [ctx [_ table on & records]]
  (let [on      (if (map? on) (keys on) on)
        query   {:select (conj (vec on) :xt/id)
                 :from   [table]
                 :where  [:in
                          [:array (vec on)]
                          (mapv (fn [record]
                                  [:array (mapv record on)])
                                records)]}
        results (q ctx query)
        on-fn   (apply juxt on)
        on->id  (into {} (map (juxt on-fn :xt/id)) results)

        new-records
        (into []
              (keep (fn [record]
                      (when-not (contains? on->id (on-fn record))
                        (into {}
                              (filter (comp some? val))
                              (merge {:xt/id (or (:xt/id record) (random-uuid))}
                                     (dissoc record :biff/on-insert :biff/on-update)
                                     (:biff/on-insert record))))))
              records)

        existing
        (into []
              (keep (fn [record]
                      (when-some [id (on->id (on-fn record))]
                        (merge (dissoc record :biff/on-insert :biff/on-update)
                               (:biff/on-update record)
                               {:xt/id id}))))
              records)]
    (vec (concat
          (mapv #(assert-not-exists table (select-keys % on)) new-records)
          (mapv #(assert-exists table (:xt/id %)) existing)
          (when (seq new-records)
            [(into [:put-docs table] new-records)])
          (when (seq existing)
            [(into [:patch-docs table] existing)])
          (mapv #(assert-count-at-most-one table (select-keys % on)) records)))))

(defn expand-ops [ctx tx-ops]
  (loop [ops tx-ops
         acc []]
    (if-let [op (first ops)]
      (let [expanded (expand-op ctx op)]
        (if (= expanded [op])
          (recur (rest ops) (conj acc op))
          (recur (concat expanded (rest ops)) acc)))
      acc)))

;; Async submit internals

(defn- begin-rw-sql [{:keys [system-time default-tz metadata async?]}]
  (let [kvs      (->> [["TIMEZONE = ?" (some-> default-tz str)]
                       ["SYSTEM_TIME = ?" system-time]
                       ["METADATA = ?" metadata]]
                      (into [] (filter (comp some? second))))
        settings (conj (mapv first kvs)
                       (str "ASYNC = " (boolean async?)))]
    (into [(str "BEGIN READ WRITE WITH ("
                (str/join ", " settings)
                ")")]
          (map second)
          kvs)))

(defn- with-conn [{:keys [connectable database]} f]
  (let [database (cond-> database
                   (keyword? database) (-> symbol str NormalForm/normalForm))]
    (with-open [conn (-> (.createConnectionBuilder ^DataSource connectable)
                         (cond-> database
                           (.database database))
                         (.build))]
      (f conn))))

;; Like xt/submit-tx but returns an :await-token that can be used to block until
;; the transaction has been indexed.
(defn- submit-tx-for-token [ctx tx-ops tx-opts]
  (with-conn {:connectable (:biff.xtdb/node ctx)
              :database    (:database tx-opts)}
    (fn [conn]
      (try
        (jdbc/execute! conn (begin-rw-sql (assoc tx-opts :async? true)))
        (try
          (doseq [tx-op tx-ops
                  :let  [tx-op (cond-> tx-op
                                 (not (record? tx-op)) tx-ops/parse-tx-op)]]
            (xtp/execute-op! tx-op conn))
          (catch BatchUpdateException e
            (throw (ex-cause e))))
        (jdbc/execute! conn ["COMMIT"])
        (let [{:keys [tx_id await_token]}
              (jdbc/execute-one! conn ["SHOW LATEST_SUBMITTED_TX"])]
          {:tx-id       tx_id
           :await-token await_token})
        (catch Exception e
          (try
            (jdbc/execute! conn ["ROLLBACK"])
            (catch Throwable t
              (throw (doto e (.addSuppressed t)))))
          (throw e))))))

;; Public write wrappers

(defn- validate-tx! [tx-ops]
  (doseq [op tx-ops
          :when (vector? op)
          :let [[op _table-or-opts & docs] op]
          :when (#{:put-docs :patch-docs} op)]
    (biff.core/validate docs)))

(defn execute-tx
  ([ctx tx-ops]
   (execute-tx ctx tx-ops nil))
  ([ctx tx-ops tx-opts]
   (let [tx-ops      (expand-ops ctx tx-ops)
         _           (validate-tx! tx-ops)
         tx-key      (xt/execute-tx (:biff.xtdb/node ctx) tx-ops tx-opts)
         system-time (:system-time tx-key)]
     (when-let [poll-now (:biff.xtdb/poll-now ctx)]
       (poll-now system-time))
     (notify-on-tx ctx system-time)
     tx-key)))

(defn submit-tx
  ([ctx tx-ops]
   (submit-tx ctx tx-ops nil))
  ([ctx tx-ops tx-opts]
   (let [tx-ops (expand-ops ctx tx-ops)
         _      (validate-tx! tx-ops)
         tx-key (submit-tx-for-token ctx tx-ops tx-opts)]
     (when-let [poll-now (:biff.xtdb/poll-now ctx)]
       (poll-now tx-key))
     (select-keys tx-key [:tx-id]))))

(defn prefix-uuid [uuid-prefix uuid-rest]
  (UUID/fromString (str (subs (str uuid-prefix) 0 4)
                        (subs (str uuid-rest) 4))))
