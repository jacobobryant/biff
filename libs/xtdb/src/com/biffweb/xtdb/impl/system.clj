(ns com.biffweb.xtdb.impl.system
  (:require [com.biffweb.core :as biff.core]
            [com.biffweb.xtdb.impl.authorize :as authorize]
            [com.biffweb.xtdb.impl.kv :as kv]
            [com.biffweb.xtdb.impl.resolver :as resolver]
            [com.biffweb.xtdb.impl.tx :as tx]
            [xtdb.node :as xt.node])
  (:import [com.zaxxer.hikari HikariConfig HikariDataSource]))

(defn expand-config
  [{:biff.xtdb/keys [config storage log
                     storage-bucket storage-endpoint storage-access-key
                     storage-secret-key disk-cache-max-bytes
                     memory-cache-max-bytes
                     log-bootstrap-servers log-topic log-epoch]
    :or             {storage               :local
                     log                   :local
                     log-bootstrap-servers "localhost:9092"
                     log-topic             "xtdb-log"
                     log-epoch             0
                     disk-cache-max-bytes  (* 10 1024 1024 1024)}}]
  (or config
      (let [credentials {:access-key storage-access-key
                         :secret-key (force storage-secret-key)}]
        (merge
         (when (and (#{:local :remote} storage) memory-cache-max-bytes)
           {:memory-cache {:max-size-bytes memory-cache-max-bytes}})
         (when (= storage :remote)
           {:disk-cache (cond-> {:path "storage/xtdb2/storage-cache"}
                          disk-cache-max-bytes
                          (assoc :max-size-bytes disk-cache-max-bytes))})
         (when-not (= storage :memory)
           {:storage (case storage
                       :local [:local {:path "storage/xtdb2/storage"}]
                       :remote [:remote
                                {:object-store
                                 [:s3 {:bucket      storage-bucket
                                       :endpoint    storage-endpoint
                                       :credentials credentials}]}])})
         (when-not (= log :memory)
           {:log (case log
                   :local [:local {:path  "storage/xtdb2/log"
                                   :epoch log-epoch}]
                   :kafka [:kafka {:bootstrap-servers log-bootstrap-servers
                                   :topic-name        log-topic
                                   :epoch             log-epoch}])})))))

(defn- start-connection-pool [node hikari-config]
  (HikariDataSource.
   (doto (or hikari-config (HikariConfig.))
     (.setDataSource node))))

(defn- wrap-db-snapshot [f]
  (fn [ctx]
    (f (assoc ctx
              :biff.xtdb/snapshot-token
              (tx/snapshot-token (:biff.xtdb/node ctx))))))

(defn- start [ctx]
  (let [config   (expand-config ctx)
        node     (if (seq config)
                   (xt.node/start-node config)
                   (xt.node/start-node))
        pool     (start-connection-pool node (:biff.xtdb/hikari-config ctx))
        ctx      (assoc ctx
                        :biff.xtdb/node node
                        :biff.xtdb/connection-pool pool
                        :biff.core/kv-get kv/get-value
                        :biff.core/kv-list kv/list-keys
                        :biff.core/kv-set kv/set-value
                        :biff.core/wrap-db-snapshot wrap-db-snapshot)
        listener (tx/start-listener node ctx)
        ctx      (assoc ctx
                        :biff.xtdb/poll-now (:poll-now listener)
                        ::listener listener)]
    ctx))

(defn- stop [{:keys  [biff.xtdb/node biff.xtdb/connection-pool]
              ::keys [listener]}]
  ((:stop listener))
  (.close connection-pool)
  (.close node))

(def fx-handlers
  {:biff.xtdb.fx/execute-tx       tx/execute-tx
   :biff.xtdb.fx/submit-tx        tx/submit-tx
   :biff.xtdb.fx/authorized-write authorize/authorized-write})

(defn module []
  {:biff.core/id     :biff.xtdb/module
   :biff.core/start  start
   :biff.core/stop   stop
   :biff.fx/handlers fx-handlers})

(defn schema-module [{:biff.xtdb/keys [authorize columns]}]
  (biff.core/register (resolver/columns->schema columns))
  {:biff.core/init       {:biff.xtdb/authorize authorize}
   :biff.graph/resolvers (resolver/make-resolvers columns)})
