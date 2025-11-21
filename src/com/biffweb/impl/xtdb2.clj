(ns com.biffweb.impl.xtdb2
  (:require
   [clojure.walk :as walk]
   [com.biffweb.aliases.xtdb2 :as xta]
   [com.biffweb.impl.util :as util]
   [honey.sql :as hsql]
   [malli.core :as malli]
   [malli.error :as malli.e]
   [malli.util :as malli.u])
  (:import
   [com.zaxxer.hikari HikariConfig HikariDataSource]
   [java.util UUID]
   [java.util.concurrent LinkedBlockingQueue TimeUnit]))

(def have-dep (some? (util/try-resolve 'xtdb.api/execute-tx)))

(defn- get-conn [node]
  (.build (.createConnectionBuilder node)))

(defmacro ensure-dep [& body]
  (if-not have-dep
    `(throw (UnsupportedOperationException.
             "To call this function, you must add com.xtdb/xtdb-core v2 to your dependencies."))
    `(do ~@body)))

(defn format-query [query]
  (if (map? query)
    (hsql/format
     (walk/postwalk (fn [x]
                      (cond-> x
                        (qualified-keyword? x) xta/kw->normal-form-kw))
                    query))
    query))

(defn q [node query & args]
  (apply xta/q node (format-query query) args))

(defn assert-unique [table kvs]
  (format-query
   {:assert [:>= 1 {:select [[[:count '*]]]
                    :from (symbol table)
                    :where (into [:and]
                                 (map (fn [[k v]]
                                        [:= k v]))
                                 kvs)}]}))

(defn use-xtdb2-config [{:keys [biff/secret]
                         :biff.xtdb2/keys [storage log]
                         :biff.xtdb2.storage/keys [bucket endpoint access-key secret-key]
                         :or {storage :local log :local}}]
  (let [secret-key (secret :biff.xtdb2.storage/secret-key)]
    {:log [log
           (case log
             :local {:path "storage/xtdb2/log"}
             :kafka {:bootstrap-servers "localhost:9092"
                     :topic "xtdb-log"
                     ;; The default prod config for Biff apps uses remote storage and
                     ;; local log, so if kafka is being used, it'll probably be in the
                     ;; context of migrating from a local log. So might as well bump this
                     ;; pre-emptively.
                     :epoch 1})]
     :storage [storage
               (case storage
                 :local {:path "storage/xtdb2/storage"}
                 :remote {:object-store [:s3
                                         {:bucket bucket
                                          :endpoint endpoint
                                          :credentials {:access-key access-key
                                                        :secret-key secret-key}}]
                          :local-disk-cache "storage/xtdb2/storage-cache"})]}))

(defn use-xtdb2 [{:keys [biff.xtdb2/hikari-config] :as ctx}]
  (ensure-dep
   (let [node (xta/start-node (use-xtdb2-config ctx))
         datasource (HikariDataSource.
                     (doto (or hikari-config (HikariConfig.))
                       (.setDataSource node)))]
     (-> ctx
         (assoc :biff/node node)
         (assoc :biff/conn datasource)
         (update :biff/stop conj (fn []
                                   (.close datasource)
                                   (.close node)))))))

(defn all-system-times
  ([node]
   (all-system-times node #xt/instant "1970-01-01T00:00:00Z"))
  ([node after-inst]
   (lazy-seq
    (let [results (into []
                        (map :system-time)
                        (xta/plan-q node
                                    ["select system_time from xt.txs
                                      where committed = true
                                      and system_time > ?
                                      order by system_time asc limit 1000"
                                     after-inst]))]
      (concat results
              (some->> (peek results)
                       (all-system-times node)))))))

(defn all-tables [node]
  (->> (xta/q node (str "SELECT table_schema, table_name "
                        "FROM information_schema.tables "
                        "WHERE table_type = 'BASE TABLE' AND "
                        "table_schema NOT IN ('pg_catalog', 'information_schema');"))
       (filterv #(= "public" (:table-schema %)))
       (mapv :table-name)))

(defn tx-log [node & {:keys [tables after-inst]}]
  (let [after-inst (or after-inst #xt/instant "1970-01-01T00:00:00Z")
        tables     (or tables (all-tables node))]
    (->> (all-system-times node after-inst)
         (partition-all 1000)
         (mapcat (fn [system-times]
                   (let [start (first system-times)
                         end (last system-times)]
                     (->> tables
                          (pmap (fn [table]
                                  (mapv #(assoc % :biff.xtdb/table table)
                                        (xta/q node [(str "select *, _system_from, _system_to "
                                                          "from " table  " for all system_time "
                                                          "where _system_from >= ? "
                                                          "and _system_from <= ? "
                                                          "order by _system_from")
                                                     start
                                                     end]))))
                          (apply concat)
                          (sort-by :xt/system-from))))))))

(defn latest-system-time [node]
  (some-> (get-in (xta/q node "select max(system_time) from xt.txs where committed = true")
                  [0 :xt/column-1])
          (.toInstant)))

(defn use-xtdb2-listener [{:biff/keys [node conn modules]
                           :keys [biff.xtdb.listener/tables]
                           :as ctx}]
  (let [conn (or conn node)
        continue (atom true)
        done (promise)
        ;; Wait for system time to settle
        _ (Thread/sleep 1000)
        system-time (atom (loop [old-t nil
                                 new-t (latest-system-time conn)]
                            (if (= old-t new-t)
                              new-t
                              (do
                                (Thread/sleep 1000)
                                (recur new-t (latest-system-time conn))))))
        stop-fn (fn []
                  (reset! continue false)
                  (deref done 10000 nil))
        queue (LinkedBlockingQueue. 1)
        poll-now #(.offer queue true)]
    (future
      (util/catchall-verbose
       (while @continue
         (.poll queue 1 TimeUnit/SECONDS)
         (let [listeners (not-empty (keep :on-tx @modules))
               prev-t @system-time
               latest-t (when listeners
                          (latest-system-time conn))]
           (when (and listeners (not= prev-t latest-t))
             (reset! system-time latest-t)
             (doseq [record (tx-log conn {:after-inst prev-t :tables tables})
                     listener listeners]
               (util/catchall-verbose (listener ctx record)))))))
      (deliver done nil))
    (-> ctx
        (assoc :biff.xtdb.listener/poll-now poll-now)
        (update :biff/stop conj stop-fn))))

(defn prefix-uuid [uuid-prefix uuid-rest]
  (UUID/fromString (str (subs (str uuid-prefix) 0 4)
                        (subs (str uuid-rest) 4))))

(defn validate-tx [tx malli-opts]
  (doseq [tx-op tx
          :when (#{:put-docs :patch-docs} (first tx-op))
          :let [[op opts & records] tx-op
                table (if (keyword? opts)
                        opts
                        (:into opts))
                schema* (malli/schema table malli-opts)
                schema (cond-> schema*
                         (= op :patch-docs) malli.u/optional-keys)
                optional-keys (into #{}
                                    (comp (filter (comp :optional :properties val))
                                          (map key))
                                    (:keys (malli/ast schema*)))]
          record records]
    (when-not (some? (:xt/id record))
      (throw (ex-info "Record is missing an :xt/id value."
                      {:table table
                       :record record})))
    (when-not (malli/validate schema
                              (into {}
                                    (remove (fn [[k v]]
                                              (and (nil? v)
                                                   (optional-keys k))))
                                    record)
                              malli-opts)
      (throw (ex-info "Record doesn't match schema."
                      {:table table
                       :record record
                       :explain (malli.e/humanize (malli/explain schema record))}))))
  true)

(defn submit-tx [{:biff/keys [node conn malli-opts]
                  :keys [biff.xtdb.listener/poll-now]} tx & args]
  (validate-tx tx @malli-opts)
  (let [tx     (mapv format-query tx)
        result (apply xta/submit-tx (or conn node) tx args)]
    (when poll-now (poll-now))
    result))
