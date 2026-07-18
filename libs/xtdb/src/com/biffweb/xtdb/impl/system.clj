(ns com.biffweb.xtdb.impl.system
  (:require [com.biffweb.xtdb.impl.authorize :as authorize]
            [com.biffweb.xtdb.impl.kv :as kv]
            [com.biffweb.xtdb.impl.tx :as tx]
            [xtdb.node :as xt.node]))

(defn expand-config
  [{:biff.xtdb/keys [config storage log
                     storage-bucket storage-endpoint storage-access-key
                     storage-secret-key storage-max-cache-bytes
                     log-bootstrap-servers log-topic log-epoch]
    :or             {storage                 :local
                     log                     :local
                     log-bootstrap-servers   "localhost:9092"
                     log-topic               "xtdb-log"
                     log-epoch               0
                     storage-max-cache-bytes (* 10 1024 1024 1024)}}]
  (or config
      (merge
       (when (and (= storage :local) storage-max-cache-bytes)
         {:memory-cache {:max-size-bytes storage-max-cache-bytes}})
       (when (= storage :remote)
         {:disk-cache (cond-> {:path "storage/xtdb2/storage-cache"}
                        storage-max-cache-bytes
                        (assoc :max-size-bytes storage-max-cache-bytes))})
       (when-not (= storage :memory)
         {:storage (case storage
                     :local [:local {:path "storage/xtdb2/storage"}]
                     :remote [:remote {:object-store [:s3
                                                      {:bucket      storage-bucket
                                                       :endpoint    storage-endpoint
                                                       :credentials {:access-key storage-access-key
                                                                     :secret-key storage-secret-key}}]}])})
       (when-not (= log :memory)
         {:log (case log
                 :local [:local {:path  "storage/xtdb2/log"
                                 :epoch log-epoch}]
                 :kafka [:kafka {:bootstrap-servers log-bootstrap-servers
                                 :topic-name        log-topic
                                 :epoch             log-epoch}])}))))

(defn use-xtdb [ctx]
  (let [config   (expand-config ctx)
        node     (if (seq config)
                   (xt.node/start-node config)
                   (xt.node/start-node))
        ctx      (assoc ctx :biff.xtdb/node node)
        listener (tx/start-listener node ctx)
        ctx      (assoc ctx
                        :biff.xtdb/poll-now (:poll-now listener))]
    (update ctx :biff.core/stop conj (fn []
                                       ((:stop listener))
                                       (.close node)))))

(defn- wrap-read-tx [f]
  (fn [ctx]
    (f (assoc ctx
              :biff.xtdb/snapshot-token
              (tx/snapshot-token (:biff.xtdb/node ctx))))))

(def fx-handlers
  {:biff.xtdb.fx/execute-tx       tx/execute-tx
   :biff.xtdb.fx/submit-tx        tx/submit-tx
   :biff.xtdb.fx/authorized-write authorize/authorized-write})

(defn module []
  {:biff.fx/handlers fx-handlers
   :biff.core/init
   (fn [_modules-var]
     {:biff.core/kv-get       kv/get-value
      :biff.core/kv-list      kv/list-keys
      :biff.core/kv-set       kv/set-value
      :biff.core/wrap-read-tx wrap-read-tx})})
