(ns com.biffweb.impl.xtdb.util
  (:require [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [xtdb.api :as xt]))

(defn verbose-sync [node-id node]
  (let [done (atom false)]
    (future
      (loop []
        (Thread/sleep 2000)
        (when-not @done
          (log/info node-id "indexed" (xt/latest-completed-tx node))
          (recur))))
    (xt/sync node)
    (reset! done true)))

(defn kv-store [{:biff.xtdb/keys [dir kv-store]} basename]
  {:kv-store {:xtdb/module (if (= kv-store :lmdb)
                             'xtdb.lmdb/->kv-store
                             'xtdb.rocksdb/->kv-store)
              :db-dir (io/file dir (str basename (when (= kv-store :lmdb)
                                                   "-lmdb")))}})
