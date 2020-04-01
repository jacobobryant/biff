(ns nimbus.crux
  (:require
    [crux.api :as crux :refer [submit-tx entity db]]
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [trident.util :as u]
    [mount.core :as mount :refer [defstate]]
    [nimbus.core :as nimbus :refer [api]]))

(defn start-standalone-node ^crux.api.ICruxAPI [storage-dir]
  (crux/start-node {:crux.node/topology '[crux.standalone/topology
                                          crux.kv.rocksdb/kv-store]
                    :crux.kv/db-dir (str (io/file storage-dir "db"))}))

(defn op-allowed? [{:keys [op node uid rules]}]
  true)

(def rules nil)

(defmethod api ::tx
  [{:keys [uid] ::keys [node]} tx]
  (if-not (every? #(op-allowed? {:op % :node node :uid uid :rules rules}) tx)
    ::tx-not-allowed
    (do
      (submit-tx node tx)
      ::success)))

(defstate node
  :start (start-standalone-node "nimbus.crux")
  :stop (.close node))

(defn wrap-api [event]
  (assoc event ::node node))

(defn init []
  (mount/start #'node))


; (entity (db node) :foo)

;(def result
;  (s
;[:nimbus.crux/tx
;                                                   [[:crux.tx/put
;                                                     {:crux.db/id :foo
;                                                      :name "hey"}]]]
