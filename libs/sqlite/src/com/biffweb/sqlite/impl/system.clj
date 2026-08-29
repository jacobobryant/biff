(ns com.biffweb.sqlite.impl.system
  (:require [com.biffweb.sqlite.impl.authorize :as authorize]
            [com.biffweb.sqlite.impl.execute :as exec]
            [com.biffweb.sqlite.impl.kv :as kv]
            [com.biffweb.sqlite.impl.litestream :as litestream]
            [com.biffweb.sqlite.impl.pool :as pool]
            [com.biffweb.sqlite.impl.sqldef :as impl.sqldef]
            [next.jdbc :as jdbc]))

(defn- wrap-db-snapshot [f]
  (fn [ctx]
    (jdbc/with-transaction [tx (:biff.sqlite/read-pool ctx)]
      (f (assoc ctx :biff.sqlite/read-pool tx)))))

(defn- start
  [ctx]
  (-> ctx
      litestream/start
      impl.sqldef/start
      pool/start))

(defn- stop [ctx]
  (-> ctx
      pool/stop
      litestream/stop))

(def fx-handlers
  {:biff.sqlite.fx/execute             exec/execute
   :biff.sqlite.fx/execute-tx          exec/execute-tx
   :biff.sqlite.fx/authorized-write    authorize/authorized-write
   :biff.sqlite.fx/authorized-write-tx authorize/authorized-write-tx})

(defn litestream-module []
  {:biff.core/id    :biff.sqlite/litestream-module
   :biff.core/start litestream/start
   :biff.core/stop  litestream/stop})

(defn sqldef-module []
  {:biff.core/id    :biff.sqlite/sqldef-module
   :biff.core/start impl.sqldef/start})

(defn conn-module []
  {:biff.core/id    :biff.sqlite/conn-module
   :biff.core/start pool/start
   :biff.core/stop  pool/stop})

(defn module []
  {:biff.core/id        :biff.sqlite/module
   :biff.core/start     start
   :biff.core/stop      stop
   :biff.fx/handlers    fx-handlers
   :biff.sqlite/columns kv/columns

   :biff.core/init
   (fn [modules-var]
     {:biff.core/kv-get           kv/get-value
      :biff.core/kv-list          kv/list-keys
      :biff.core/kv-set           kv/set-value
      :biff.core/wrap-db-snapshot wrap-db-snapshot
      :biff.sqlite/columns        (into {}
                                        (mapcat :biff.sqlite/columns)
                                        @modules-var)})})
