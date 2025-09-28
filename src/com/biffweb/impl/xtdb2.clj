(ns com.biffweb.impl.xtdb2
  (:require [com.biffweb.impl.util :as util]
            [com.biffweb.impl.xtdb2.aliases :as xta]
            [clojure.string :as str]
            [malli.core :as malli]
            [malli.error :as malli.e]
            [malli.experimental.time :as malli.t]
            [malli.util :as malli.u]
            [malli.registry :as malli.r]))

(def have-dep (some? (util/try-resolve 'xtdb.api/execute-tx)))

(defmacro ensure-dep [& body]
  (if-not have-dep
    `(throw (UnsupportedOperationException.
             "To call this function, you must add com.xtdb/xtdb-core v2 to your dependencies."))
    `(do ~@body)))

(defn schema->table [schema]
  (or (:biff/table (malli/properties schema))
      (when (keyword? schema)
        (-> (str schema)
            (subs 1)
            str/lower-case
            (str/replace #"[^a-z]" "_")))
      (throw (ex-info (str "Unable to infer a table name. You must set the "
                           ":biff/table property on the given schema.")
                      {:schema schema}))))

(defn- write-records [op schema records]
  (doseq [record records]
    (when-not (some? (:xt/id record))
      (throw (ex-info "Record is missing an :xt/id value."
                      {:record record})))
    (when-not (malli/validate schema record)
      (throw (ex-info "Record doesn't match schema."
                      {:record record
                       :schema schema
                       :explain (malli.e/humanize (malli/explain schema record))}))))
  (into [(str op " into " (schema->table schema) " records "
              (str/join ", " (repeat (count records) "?")))]
        records))

(defn where-clause [ks]
  (ensure-dep
   (->> ks
        (mapv #(str (xta/->normal-form-str %) " = ?"))
        (str/join " and "))))

(defn insert [schema & records]
  (write-records "insert" schema records))

(defn patch [schema & records]
  (write-records "patch" (malli.u/optional-keys schema) records))

(defn assert-unique [schema kvs]
  (into [(str "assert 1 >= (select count(*) from " (schema->table schema) " where "
              (where-clause (keys kvs)))]
        (vals kvs)))

(defn select-where [schema kvs]
  (into [(str "select * from " (schema->table schema) " where " (where-clause (keys kvs)))]
        (vals kvs)))

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

(defn use-xtdb2 [ctx]
  (ensure-dep
   (let [node (xta/start-node (use-xtdb2-config ctx))]
     (-> ctx
         (assoc :biff/node node)
         (update ctx :biff/stop conj #(.close node))))))

(comment

  ;; Som examples that might come in handy:

  (def query-all-tables
    (str "SELECT table_schema, table_name "
         "FROM information_schema.tables "
         "WHERE table_type = 'BASE TABLE' AND "
         "table_schema NOT IN ('pg_catalog', 'information_schema');"))

  (xt/q node "select * from stuff" {:snapshot-time #xt/instant "2025-09-22T14:25:49.411837Z"})

  (xt/q node "select *, _valid_from, _system_from from stuff")
  (xt/q node "select *, _valid_from, _system_from from xt.txs order by _system_from")

  (xt/q node (str "select *, _system_from, _system_to, _valid_from, _valid_to from stuff "
                  "for all system_time order by _system_from"))

  (def registry
    (merge (malli/default-schemas)
           (malli.u/schemas)
           (malli.t/schemas)
           schema))
  (malli.r/set-default-registry! registry))
