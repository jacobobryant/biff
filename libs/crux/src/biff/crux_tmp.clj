(ns biff.crux-tmp
  (:require
    [biff.util-tmp :as bu]
    [biff.util.protocols :as proto]
    [clojure.java.io :as io]
    [clojure.walk :as walk]
    [crux.api :as crux]
    [malli.core :as malc]))

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

(defn normalize-doc [{:keys [server-timestamp
                             db
                             doc-id
                             tx-doc
                             before]}]
  (let [doc (cond-> tx-doc
              (map? doc-id) (merge doc-id)
              (some tx-doc [:db/merge :db/update]) (->> (merge before)))
        doc (-> doc
                (assoc :crux.db/id doc-id)
                (dissoc :db/merge :db/update))
        doc (->> doc
                 (walk/postwalk (fn [x]
                                  (if (= x :db/server-timestamp)
                                    server-timestamp
                                    x)))
                 (keep (fn [[k v]]
                         (when (not= v :db/remove)
                           [k (if (and (coll? v) (#{:db/union :db/disj} (first v)))
                                (let [[op & xs] v
                                      xs-before (get before k)]
                                  (reduce (case op
                                            :db/union conj
                                            :db/disj disj)
                                          (set xs-before)
                                          xs))
                                v)])))
                 (into {}))]
    doc))

(defn get-changeset [{:keys [db server-timestamp biff-tx]}]
  (for [[[doc-type doc-id] tx-doc] biff-tx
        :let [doc-id (or doc-id (java.util.UUID/randomUUID))
              before (crux/entity db doc-id)
              after (when (some? tx-doc)
                      (normalize-doc
                        {:db db
                         :doc-id doc-id
                         :before before
                         :tx-doc tx-doc
                         :server-timestamp server-timestamp}))]]
    {:doc-id doc-id
     :doc-type doc-type
     :before before
     :after after}))

(def biff-tx-schema
  [:sequential {:registry {:doc-type keyword?
                           :doc-id any?
                           :ident [:cat :doc-type [:? :doc-id]]
                           :doc [:maybe [:map-of keyword? any?]]
                           :tx-item [:tuple :ident :doc]}}
   :tx-item])

(defn normalize-tx [{:keys [biff/schema biff.crux/db]} biff-tx]
  (when-not (malc/validate biff-tx-schema (vec biff-tx))
    ; Ideally we'd include Malli's explain + humanize output, but it had some
    ; weird results (including an exception) when I tested it on a few
    ; examples.
    (throw
      (ex-info "TX doesn't match schema."
               {:tx biff-tx})))
  (let [schema (bu/realize schema)
        server-timestamp (java.util.Date.)
        changeset (get-changeset {:db @db
                                  :server-timestamp server-timestamp
                                  :biff-tx biff-tx})
        crux-tx (for [{:keys [doc-id before after]} changeset
                      crux-tx-item [(if after
                                      [:crux.tx/put after]
                                      [:crux.tx/delete doc-id])
                                    [:crux.tx/match doc-id before]]]
                  crux-tx-item)]
    (doseq [[{:keys [before doc-type] :as change-item}
             [_ tx-doc :as tx-item]] (map vector changeset biff-tx)]
      (when (and (nil? before) (:db/update tx-doc))
        (throw
          (ex-info "Attempted to update on a new doc."
                   {:tx-item tx-item})))
      (doseq [k [:before :after]
              :let [doc (k change-item)]]
        (when (and (some? doc) (not (proto/valid? schema doc-type doc)))
          (throw
            (ex-info "Doc doesn't match doc-type."
                     {:tx-item tx-item
                      k doc
                      :explain (proto/explain-human schema doc-type doc)})))))
    {:changeset changeset
     :server-timestamp server-timestamp
     :db-before @db
     :db-after (crux/with-tx @db crux-tx)
     :crux-tx crux-tx}))

(defn authorize-tx [sys {:keys [biff-tx changeset] :as opts}]
  (doseq [[{:keys [before after doc-type] :as change-item}
           tx-item] (map vector changeset biff-tx)
          :let [op (case (mapv some? [before after])
                     [false true] :create
                     [true true] :update
                     :delete)
                authorize-opts (merge sys
                                      (select-keys
                                        opts [:db-before
                                              :db-after
                                              :server-timestamp])
                                      change-item)]]
    (when-not (some (fn [op]
                      (let [docs (case op
                                   :create [after]
                                   :update [before after]
                                   :delete [before])]
                        (apply proto/authorize authorize-opts docs)))
                    [op :write :rw])
      (throw
        (ex-info "TX not authorized." {:tx-item tx-item
                                       :before before
                                       :after after})))))

(defn submit-tx [{:biff.crux/keys [node authorize]
                  :as sys} biff-tx]
  (loop [n-tried 0]
    (let [db (crux/open-db node)
          {:keys [crux-tx changeset]
           :as norm-result} (try
                              (normalize-tx
                                (assoc sys :biff.crux/db (delay db)) biff-tx)
                              (catch Exception e
                                (.close db)
                                (throw e)))
          _ (try
              (when authorize
                (authorize-tx sys (assoc norm-result :biff-tx biff-tx)))
              (finally
                (.close db)))
          submitted-tx (crux/submit-tx node crux-tx)]
      (crux/await-tx node submitted-tx)
      (cond
        (crux/tx-committed? node submitted-tx) submitted-tx
        (< n-tried 4) (let [seconds (int (Math/pow 2 n-tried))]
                        (print (str "TX failed due to contention, trying again in "
                                    seconds " seconds...\n"))
                        (Thread/sleep (* 1000 seconds))
                        (recur (inc n-tried)))
        :default (throw
                   (ex-info "TX failed, too much contention."
                            {:biff-tx biff-tx}))))))
