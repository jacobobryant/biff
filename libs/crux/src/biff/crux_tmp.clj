(ns biff.crux-tmp
  (:require
    [biff.util-tmp :as bu]
    [biff.util.protocols :as proto]
    [clojure.java.io :as io]
    [crux.api :as crux]))

(defn start-node [{:keys [topology dir opts jdbc-spec pool-opts]}]
  (let [rocksdb (fn [basename]
                  {:kv-store {:crux/module 'crux.rocksdb/->kv-store
                              :db-dir (io/file dir basename)}})]
    (doto (crux/start-node
            (merge
              (case topology
                :standalone
                {:crux/index-store    (rocksdb "index")
                 :crux/document-store (rocksdb "docs")
                 :crux/tx-log         (rocksdb "tx-log")}

                :jdbc
                {:crux/index-store (rocksdb "index")
                 :crux.jdbc/connection-pool {:dialect {:crux/module
                                                       'crux.jdbc.psql/->dialect}
                                             :pool-opts pool-opts
                                             :db-spec jdbc-spec}
                 :crux/tx-log {:crux/module 'crux.jdbc/->tx-log
                               :connection-pool :crux.jdbc/connection-pool}
                 :crux/document-store {:crux/module 'crux.jdbc/->document-store
                                       :connection-pool :crux.jdbc/connection-pool}})
              opts))
      crux/sync)))

(defn wrap-db [handler {:keys [node use-open-db]}]
  (fn [req]
    (let [db (delay ((if use-open-db crux/open-db crux/db) node))
          resp (handler (assoc req :biff.crux/db db))]
      (when (and use-open-db (realized? db))
        (.close @db))
      resp)))

(defn use-crux [{:biff.crux/keys [topology
                                  dir
                                  opts
                                  use-open-db]
                 :as sys}]
  (let [node (start-node
               {:topology topology
                :dir dir
                :opts opts
                :jdbc-spec (bu/select-ns-as sys 'biff.crux.jdbc nil)
                :pool-opts (bu/select-ns-as sys 'biff.crux.jdbc-pool nil)})]
    (-> sys
        (assoc :biff.crux/node node)
        (update :biff/stop conj #(.close node))
        (update :biff/handler wrap-db
                {:node node :use-open-db use-open-db}))))

(defn lazy-q [db query f]
  (with-open [results (crux/open-q db query)]
    (f (iterator-seq results))))

(defn q-entity [db kvs]
  (ffirst
    (crux/q db
      {:find '[(pull doc [*])]
       :where (vec (for [kv kvs]
                     (into ['doc] kv)))})))

(defn normalize-tx [{:keys [biff/schema]} biff-tx]
  (for [args biff-tx]
    (if (keyword? (first args))
      args
      (let [[[doc-type id] doc & args] args
            id (or id (java.util.UUID/randomUUID))
            doc (cond-> doc
                  true (assoc :crux.db/id id)
                  (map? id) (merge id))]
        (proto/assert-valid (bu/realize schema) doc-type doc)
        (into [:crux.tx/put doc] args)))))

(defn submit-tx [{:keys [biff.crux/node] :as opts} biff-tx]
  (crux/submit-tx node (normalize-tx opts biff-tx)))

(defn submit-await-tx [{:keys [biff.crux/node] :as opts} biff-tx]
  (crux/await-tx node (submit-tx opts biff-tx)))
