(ns ^:nimbus biff.crux
  (:require
    [crux.api :as crux :refer [submit-tx entity db]]
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [trident.util :as u]
    [mount.core :as mount :refer [defstate]]))

(defn start-standalone-node ^crux.api.ICruxAPI [storage-dir]
  (crux/start-node {:crux.node/topology '[crux.standalone/topology
                                          crux.kv.rocksdb/kv-store]
                    :crux.kv/db-dir (str (io/file storage-dir "db"))}))

(defstate node
  :start (start-standalone-node "data/biff.crux/db/")
  :stop (.close node))

;(defn op-allowed? [{:keys [op db uid rules]}]
;  true)
;
;(def rules nil)
;
;(defmethod api ::tx
;  [{:keys [uid]} tx]
;  (if-not (every? #(op-allowed? {:op % :db (db node) :uid uid :rules rules}) tx)
;    ::tx-not-allowed
;    (do
;      (submit-tx node tx)
;      ::success)))
