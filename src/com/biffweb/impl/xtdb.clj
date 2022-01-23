(ns com.biffweb.impl.xtdb
  (:require [better-cond.core :as b]
            [com.biffweb.impl.util :as util]
            [clojure.java.io :as io]
            [clojure.set :as set]
            [clojure.stacktrace :as st]
            [clojure.walk :as walk]
            [xtdb.api :as xt]
            [malli.error :as male]
            [malli.core :as malc]))

(defn start-node
  "A higher-level version of xtdb.api/start-node.

  Calls xtdb.api/sync before returning the node.

  topology   - One of #{:standalone :jdbc}.
  dir        - A path to store RocksDB instances in.
  jdbc-spec,
  pool-opts  - Maps to pass as
               {:xtdb.jdbc/connection-pool
                {:db-spec jdbc-spec :pool-opts pool-opts ...}}.
               (Used only when topology is :jdbc).
  opts       - Additional options to pass to xtdb.api/start-node."
  [{:keys [topology dir opts jdbc-spec pool-opts]}]
  (let [rocksdb (fn [basename]
                  {:kv-store {:xtdb/module 'xtdb.rocksdb/->kv-store
                              :db-dir (io/file dir basename)}})
        node (xt/start-node
               (merge (case topology
                        :standalone
                        {:xtdb/index-store    (rocksdb "index")
                         :xtdb/document-store (rocksdb "docs")
                         :xtdb/tx-log         (rocksdb "tx-log")}

                        :jdbc
                        {:xtdb/index-store (rocksdb "index")
                         :xtdb.jdbc/connection-pool {:dialect {:xtdb/module
                                                               'xtdb.jdbc.psql/->dialect}
                                                     :pool-opts pool-opts
                                                     :db-spec jdbc-spec}
                         :xtdb/tx-log {:xtdb/module 'xtdb.jdbc/->tx-log
                                       :connection-pool :xtdb.jdbc/connection-pool}
                         :xtdb/document-store {:xtdb/module 'xtdb.jdbc/->document-store
                                               :connection-pool :xtdb.jdbc/connection-pool}})
                      opts))
        f (future (xt/sync node))]
    (println "Indexing transactions...")
    (while (not (realized? f))
      (Thread/sleep 2000)
      (when-some [indexed (xt/latest-completed-tx node)]
        (println "Indexed" (pr-str indexed))))
    (println "Done indexing.")
    node))

(defn use-xt
  "A Biff component for xtdb.

  Sets :biff.xtdb/node to the xtdb node.

  topology,
  dir,
  opts                  - passed to start-node.
  biff.xtdb.jdbc/*      - passed to start-node as jdbc-spec, without the namespace.
  biff.xtdb.jdbc-pool/* - passed to start-node as pool-opts, without the namespace."
  [{:biff.xtdb/keys [topology dir opts]
    :as sys}]
  (let [node (start-node
               {:topology topology
                :dir dir
                :opts opts
                :jdbc-spec (util/select-ns-as sys 'biff.xtdb.jdbc nil)
                :pool-opts (util/select-ns-as sys 'biff.xtdb.jdbc-pool nil)})]
    (-> sys
        (assoc :biff.xtdb/node node)
        (update :biff/stop conj #(.close node)))))

(defn assoc-db [{:keys [biff.xtdb/node] :as sys}]
  (assoc sys :biff/db (xt/db node)))

(defn q [db query & args]
  "Convenience wrapper for xtdb.api/q.

  If the :find value is not a vector, results will be passed through
  (map first ...). Also throws an exception if (count args) doesn't match
  (count (:in query))."
  (when-not (= (count (:in query))
               (count args))
    (throw (ex-info (str "Incorrect number of query arguments. Expected "
                         (count (:in query))
                         " but got "
                         (count args)
                         ".")
                    {})))
  (let [return-tuples (vector? (:find query))
        query (cond-> query
                (not return-tuples) (update :find vector))
        results (apply xt/q db query args)]
    (cond->> results
      (not return-tuples) (map first))))

(defn lazy-q
  "Calls xtdb.api/open-q and passes a lazy seq of the results to a function.

  Accepts the same arguments as xtdb.api/open-q, except the last argument is a
  function which must process the results eagerly. Also includes the same
  functionality as biff.xtdb/q."
  [db query & args]
  (when-not (= (count (:in query))
               (dec (count args)))
    (throw (ex-info (str "Incorrect number of query arguments. Expected "
                         (count (:in query))
                         " but got "
                         (count args)
                         ".")
                    {})))
  (let [f (last args)
        query-args (butlast args)
        return-tuples (vector? (:find query))
        query (cond-> query
                (not return-tuples) (update :find vector))]
    (with-open [results (apply xt/open-q db query query-args)]
      (f (cond->> (iterator-seq results)
           (not return-tuples) (map first))))))

(defn lookup [db k v]
  (ffirst (xt/q db {:find '[(pull doc '[*])]
                    :where [['doc k v]]})))

(defn special-op? [x]
  (and (coll? x)
       (<= 2 (count x))
       (#{:db/union
          :db/difference
          :db/add
          :db/default} (first x))))

(defn normalize-tx-doc
  "Converts a TX doc to a xtdb doc.

  now: A Date object.
  doc-id:           The xtdb document ID.
  before:           The xtdb document's current value (i.e. before the
                    transaction). nil if the document is being created.
  tx-doc:           See https://biff.findka.com/#transactions."
  [{:keys [now
           doc-id
           tx-doc
           before]}]
  (let [doc (cond-> tx-doc
              (map? doc-id) (merge doc-id)
              (some tx-doc [:db/merge :db/update]) (->> (merge before)))
        doc (-> doc
                (assoc :xt/id doc-id)
                (dissoc :db/merge :db/update :db/doc-type :db/delete))
        doc (->> doc
                 (walk/postwalk (fn [x]
                                  (if (= x :db/now)
                                    now
                                    x)))
                 (keep (fn [[k v]]
                         (b/cond
                           :when (not= v :db/remove)
                           :let [special (special-op? v)]
                           (not special) [k v]
                           :let [[op & xs] v
                                 v-before (get before k)]
                           (= op :db/union) [k (set/union (set v-before) (set xs))]
                           (= op :db/difference) [k (set/difference (set v-before) (set xs))]
                           (= op :db/add) [k (apply + (or v-before 0) xs)]
                           :let [[default-value] xs]
                           (= op :db/default) (if (contains? before k)
                                                v-before
                                                default-value))))
                 (into {}))]
    doc))

(defn get-changes
  "Return a list of changes that will occur after a transaction.

  See https://biff.findka.com/#tx-docs.

  now: A Date object.
  biff-tx:          See https://biff.findka.com/#transactions.
  db:               A xtdb DB.

  Each change is a map with the following keys:
  :before   - The affected document's current value.
  :after    - The affected document's value after the transaction occurs.
  :tx-doc   - An element of biff-tx.
  :doc-type
  :doc-id"
  [{:keys [db now malli-opts biff-tx]}]
  (for [{:keys [db/doc-type xt/id db/delete] :as tx-doc} biff-tx
        :let [before (xt/entity db id)
              after (when (not delete)
                      (normalize-tx-doc
                        {:doc-id id
                         :before before
                         :tx-doc tx-doc
                         :now now}))
              match (or (some tx-doc [:db/update :db/merge])
                        (some special-op? (vals tx-doc)))]]
    {:tx-doc tx-doc
     :ops (cond
            delete [[::xt/delete id]]
            match [[::xt/match id before]
                   [::xt/put after]]
            :else [[::xt/put after]])
     :errors (->> [(when (and (nil? before) (:db/update tx-doc))
                     {:msg "Attempted to update on a new doc."})
                   (when (and (some? before) (not (malc/validate doc-type before malli-opts)))
                     {:msg (str "Doc wasn't a valid " doc-type " before transaction.")
                      :explain (male/humanize (malc/explain doc-type before malli-opts))})
                   (when (and (some? after) (not (malc/validate doc-type after malli-opts)))
                     {:msg (str "Doc won't be a valid " doc-type " after transaction.")
                      :explain (male/humanize (malc/explain doc-type after malli-opts))})]
                  (remove nil?))}))

(b/defnc submit-tx
  "Submits a Biff transaction.

  node:       A xtdb node.
  biff-tx:    See https://biff.findka.com/#transactions.
  malli-opts: A var for Malli opts."
  [{:keys [biff.xtdb/node
           biff.xtdb/n-tried
           biff/malli-opts]
    :or {n-tried 0}
    :as sys}
   biff-tx]
  :let [db (xt/db node)
        now (java.util.Date.)
        changes (get-changes {:db db
                              :now now
                              :biff-tx biff-tx
                              :malli-opts @malli-opts})
        xt-tx (mapcat :ops changes)
        errors (for [{:keys [errors tx-doc]} changes
                     error errors]
                 (assoc error :tx-doc tx-doc))]
  (not-empty errors) (throw (ex-info "Invalid transaction."
                                     {:errors errors}))
  :let [submitted-tx (xt/submit-tx node xt-tx)]
  (xt/tx-committed? node submitted-tx) submitted-tx
  (< n-tried 4) (let [seconds (int (Math/pow 2 n-tried))]
                  (printf "TX failed due to contention, trying again in %d seconds...\n"
                          seconds)
                  (flush)
                  (Thread/sleep (* 1000 seconds))
                  (-> sys
                      (update :biff.xtdb/n-tried (fnil inc 0))
                      (submit-tx biff-tx)))
  :else (throw (ex-info "TX failed, too much contention."
                        {:tx biff-tx})))
