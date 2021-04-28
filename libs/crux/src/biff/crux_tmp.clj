(ns biff.crux-tmp
  (:require
    [biff.util-tmp :as bu]
    [biff.malli :as bmalli]
    [clojure.java.io :as io]
    [crux.api :as crux]))

(defn start-node* [{:biff.crux/keys [topology dir opts] :as sys}]
  (let [rocksdb (fn [basename]
                  {:kv-store {:crux/module 'crux.rocksdb/->kv-store
                              :db-dir (io/file dir basename)}})
        node (crux/start-node
               (merge
                 (case topology
                   :standalone
                   {:crux/index-store    (rocksdb "index")
                    :crux/document-store (rocksdb "docs")
                    :crux/tx-log         (rocksdb "tx-log")}

                   :jdbc
                   {:crux/index-store (rocksdb "index")
                    :crux.jdbc/connection-pool
                    {:dialect {:crux/module 'crux.jdbc.psql/->dialect}
                     :pool-opts (bu/select-ns-as sys 'biff.crux.jdbc-pool nil)
                     :db-spec (bu/select-ns-as sys 'biff.crux.jdbc nil)}
                    :crux/tx-log {:crux/module 'crux.jdbc/->tx-log
                                  :connection-pool :crux.jdbc/connection-pool}
                    :crux/document-store {:crux/module 'crux.jdbc/->document-store
                                          :connection-pool :crux.jdbc/connection-pool}})
                 opts))]
    (crux/sync node)
    node))

(defn start-node [sys]
  (let [node (start-node* sys)]
    (-> sys
      (assoc :biff.crux/node node)
      (update :biff/stop conj #(.close node)))))

(defn wrap-db [handler {:keys [node use-open-db]
                        :or {use-open-db false}}]
  (fn [req]
    (let [db (delay ((if use-open-db crux/open-db crux/db) node))
          resp (handler (assoc req :biff.crux/db db))]
      (when (and use-open-db (realized? db))
        (.close @db))
      resp)))

(defn lazy-q [db query f]
  (with-open [results (crux/open-q db query)]
    (f (iterator-seq results))))

(defn q-entity [db kvs]
  (ffirst
    (crux/q db
      {:find '[(pull doc [*])]
       :where (vec (for [kv kvs]
                     (into ['doc] kv)))})))

(defn normalize-tx [malli-opts biff-tx]
  (for [args biff-tx]
    (if (keyword? (first args))
      args
      (let [[[schema id] doc & args] args
            id (or id (java.util.UUID/randomUUID))
            doc (cond-> doc
                  true (assoc :crux.db/id id)
                  (map? id) (merge id))]
        (bmalli/assert schema doc malli-opts)
        (into [:crux.tx/put doc] args)))))

(defn submit-tx [{:keys [biff.crux/node] :as opts} biff-tx]
  (crux/submit-tx node
    (normalize-tx (bu/select-ns-as opts 'biff.malli nil) biff-tx)))

(defn submit-await-tx [{:keys [biff.crux/node] :as opts} biff-tx]
  (crux/await-tx node (submit-tx opts biff-tx)))
