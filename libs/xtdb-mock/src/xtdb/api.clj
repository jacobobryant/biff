(ns xtdb.api
  (:refer-clojure :exclude [sync]))

(def ^:private functions
  '[await-tx
    db
    entity
    latest-completed-tx
    listen
    open-q
    open-tx-log
    q
    start-node
    submit-tx
    sync
    tx-committed?
    with-tx])

(defn- fail [& args]
  (throw (ex-info (str "Unsupported operation. You're trying to call an XTDB function, but com.biffweb/xtdb-mock "
                       "is in your dependencies.")
                  {})))

(doseq [sym functions]
  (intern 'xtdb.api sym fail))
