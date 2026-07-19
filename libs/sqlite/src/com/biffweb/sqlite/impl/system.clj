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

(defn use-sqlite
  [ctx]
  (-> ctx
      litestream/use-litestream
      impl.sqldef/use-sqldef
      pool/use-conn))

(def fx-handlers
  {:biff.sqlite.fx/execute             exec/execute
   :biff.sqlite.fx/execute-tx          exec/execute-tx
   :biff.sqlite.fx/authorized-write    authorize/authorized-write
   :biff.sqlite.fx/authorized-write-tx authorize/authorized-write-tx})

(defn module []
  {:biff.fx/handlers    fx-handlers
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
