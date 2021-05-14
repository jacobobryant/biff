(ns biff.crux
  "Helper functions for Crux.
  Also implements \"Biff transactions\" and \"Biff queries\", both of which are
  patterned after Firebase's. Biff transactions provide a higher level
  interface over crux.api/start-node. See submit-tx. Biff queries are less
  powerful than crux.api/q, but they support subscriptions (efficiently). See
  handle-subscribe-event!."
  (:require
    [biff.util :as bu]
    [biff.util.protocols :as proto]
    [clojure.java.io :as io]
    [clojure.set :as set]
    [clojure.stacktrace :as st]
    [clojure.walk :as walk]
    [clojure.spec.alpha :as s]
    [crux.api :as crux]
    [malli.core :as malc]))

; This is just for reference.
(def ^:no-doc glossary
  {:subscription         [:map
                          :biff/uid
                          :client-id
                          :event-id
                          :query]
   :query                [:or
                          [:map
                           :doc-type
                           [:id {:optional true}]
                           [:where {:optional true}]
                           [:static {:optional true}]]
                          keyword]
   :updates              [:map-of :ident :doc]
   :ident                [:tuple :doc-type :doc-id]
   :doc                  [:map
                          :crux.db/id]
   :change               [:map
                          :tx-item
                          :doc-id
                          :doc-type
                          :before
                          :after]
   :tx-info              [:map
                          :changes
                          :server-timestamp
                          :db-before
                          :db-after
                          :crux-tx]
   :authorize-read-opts  [:map
                          :biff/uid
                          :doc-type
                          :operation
                          :db
                          :doc
                          :doc-id]
   :authorize-write-opts [:map
                          :biff/uid
                          :doc-type
                          :operation
                          :db-before
                          :db-after
                          :before
                          :after
                          :doc-id
                          :server-timestamp]})

; === vanilla crux ===

(defn start-node
  "A higher-level version of crux.api/start-node.
  Calls crux.api/sync before returning the node.

  topology   - One of #{:standalone :jdbc}.
  dir        - A path to store RocksDB instances in.
  jdbc-spec,
  pool-opts  - Maps to pass as
               {:crux.jdbc/connection-pool
                {:db-spec jdbc-spec :pool-opts pool-opts ...}}.
               (Used only when topology is :jdbc).
  opts       - Additional options to pass to crux.api/start-node."
  [{:keys [topology dir opts jdbc-spec pool-opts]}]
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

(defn use-crux
  "A Biff component for Crux.
  Sets :biff.crux/node to the crux node.

  topology,
  dir,
  opts                  - passed to start-node.
  biff.crux.jdbc/*      - passed to start-node as jdbc-spec, without the namespace.
  biff.crux.jdbc-pool/* - passed to start-node as pool-opts, without the namespace."
  [{:biff.crux/keys [topology dir opts]
    :as sys}]
  (let [node (start-node
               {:topology topology
                :dir dir
                :opts opts
                :jdbc-spec (bu/select-ns-as sys 'biff.crux.jdbc nil)
                :pool-opts (bu/select-ns-as sys 'biff.crux.jdbc-pool nil)})]
    (-> sys
        (assoc :biff.crux/node node)
        (update :biff/stop conj #(.close node)))))

(defn wrap-db
  [handler]
  (fn [{:biff.crux/keys [node use-open-db] :as req}]
    (let [db (delay ((if use-open-db crux/open-db crux/db) node))]
      (try
        (handler (assoc req :biff.crux/db db))
        (finally
          (when (and use-open-db (realized? db))
            (.close @db)))))))

(defn lazy-q [db query f]
  (with-open [results (crux/open-q db query)]
    (f (iterator-seq results))))

(defn q-entity [db kvs]
  (ffirst
    (crux/q db
            {:find '[(pull doc [*])]
             :where (vec (for [kv kvs]
                           (into ['doc] kv)))})))

; === authorize ===

(defmulti authorize (fn [& [{:keys [doc-type operation]}]]
                      [doc-type operation]))

(defmethod authorize :default
  [& _]
  false)

(defn check-read [sys {:keys [docs query db]}]
  (first
    (remove (fn [doc]
              (some (fn [op]
                      (authorize
                        (assoc sys
                               :doc-type (:doc-type query)
                               :operation op
                               :db db
                               :doc doc
                               :doc-id (:crux.db/id doc))
                        doc))
                    [(if (some? (:id query)) :get :query)
                     :read
                     :rw]))
            docs)))

(defn check-write
  [sys {:keys [changes db-before db-after server-timestamp]}]
  (first
    (remove (fn [{:keys [before after doc-id doc-type tx-item]}]
              (some (fn [op]
                      (let [docs (cond
                                   (= op :update) [before after]
                                   (some? after)  [after]
                                   :default       [before])]
                        (apply authorize
                               (assoc sys
                                      :doc-type doc-type
                                      :operation op
                                      :db-before db-before
                                      :db-after db-after
                                      :before before
                                      :after after
                                      :doc-id doc-id
                                      :server-timestamp server-timestamp)
                               docs)))
                    [(case (mapv some? [before after])
                       [false true] :create
                       [true true] :update
                       :delete)
                     :write
                     :rw]))
            changes)))

; === biff tx ===

(defn normalize-tx-doc [{:keys [server-timestamp
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
                           [k (if (and (coll? v)
                                       (<= 2 (count v))
                                       (#{:db/union
                                          :db/difference
                                          :db/add} (first v)))
                                (let [[op & xs] v
                                      v-before (get before k)]
                                  ((case op
                                     :db/union #(set/union (set %1) (set %2))
                                     :db/difference #(set/difference (set %1) (set %2))
                                     :db/add (fn [x xs]
                                               (apply + (or x 0) xs)))
                                   v-before
                                   xs))
                                v)])))
                 (into {}))]
    doc))

(defn get-changes [{:keys [db server-timestamp biff-tx random-uuids]}]
  (for [[[[doc-type doc-id] tx-doc :as tx-item]
         random-uuid] (map vector biff-tx random-uuids)
        :let [doc-id (or doc-id random-uuid)
              before (crux/entity db doc-id)
              after (when (some? tx-doc)
                      (normalize-tx-doc
                        {:doc-id doc-id
                         :before before
                         :tx-doc tx-doc
                         :server-timestamp server-timestamp}))]]
    {:tx-item tx-item
     :doc-id doc-id
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

(defn get-tx-info [{:keys [biff/schema biff.crux/db]} biff-tx]
  (when-not (malc/validate biff-tx-schema (vec biff-tx))
    ; Ideally we'd include Malli's explain + humanize output, but it had some
    ; weird results (including an exception) when I tested it on a few
    ; examples.
    (bu/throw-anom :incorrect "TX doesn't match schema."
                   {:tx biff-tx}))
  (let [schema (bu/realize schema)
        server-timestamp (java.util.Date.)
        changes (get-changes {:db @db
                              :server-timestamp server-timestamp
                              :biff-tx biff-tx
                              :random-uuids (repeatedly
                                              (fn []
                                                (java.util.UUID/randomUUID)))})
        crux-tx (into
                  (mapv (fn [{:keys [doc-id before]}]
                          [:crux.tx/match doc-id before])
                        changes)
                  (mapv (fn [{:keys [after doc-id]}]
                          (if after
                            [:crux.tx/put after]
                            [:crux.tx/delete doc-id]))
                        changes))]
    (doseq [{:keys [before doc-type]
             [_ tx-doc :as tx-item] :tx-item
             :as change-item} changes]
      (when (and (nil? before) (:db/update tx-doc))
        (bu/throw-anom :incorrect "Attempted to update on a new doc."
                       {:tx-item tx-item}))
      (doseq [k [:before :after]
              :let [doc (k change-item)]]
        (when (and (some? doc) (not (proto/valid? schema doc-type doc)))
          (bu/throw-anom :incorrect "Doc doesn't match doc-type."
                         {:tx-item tx-item
                          k doc
                          :explain (proto/explain-human schema doc-type doc)}))))
    {:changes changes
     :server-timestamp server-timestamp
     :db-before @db
     :db-after (crux/with-tx @db crux-tx)
     :crux-tx crux-tx}))

(defn submit-tx [{:biff.crux/keys [node authorize n-tried]
                 :or {n-tried 0}
                 :as sys}
                 biff-tx]
  (let [{:keys [crux-tx] :as tx-info} (get-tx-info sys biff-tx)
        _ (when-let [bad-change (and authorize (check-write sys tx-info))]
            (bu/throw-anom :forbidden "TX not authorized."
                           bad-change))
        submitted-tx (crux/submit-tx node crux-tx)]
    (crux/await-tx node submitted-tx)
    (cond
      (crux/tx-committed? node submitted-tx) submitted-tx
      (< n-tried 4) (let [seconds (int (Math/pow 2 n-tried))]
                      (printf "TX failed due to contention, trying again in %d seconds...\n"
                              seconds)
                      (flush)
                      (Thread/sleep (* 1000 seconds))
                      ((wrap-db (fn [sys]
                                  (submit-tx sys biff-tx)))
                       (update sys :biff.crux/n-tried (fnil inc 0))))
      :default (bu/throw-anom :conflict "TX failed, too much contention."
                              {:biff-tx biff-tx}))))

; === subscribe ===

(defn- normalize-query [query]
  (select-keys query (if (contains? query :id)
                       [:id]
                       [:where])))

(defn- attr-clause? [clause]
  (not (coll? (first clause))))

(defn- query-contains? [{:keys [doc empty-db]
                         {:keys [id where]} :query}]
  (if (some? id)
    (= id (:crux.db/id doc))
    (not
      (empty?
        (crux/q (crux/with-tx empty-db [[:crux.tx/put doc]])
                {:find '[doc]
                 :where (mapv #(cond->> %
                                 (attr-clause? %) (into ['doc]))
                              where)})))))

(defn- subscription+updates
  [{:keys [txes db-before db-after subscriptions]}]
  (let [norm-queries (distinct (map (comp normalize-query :query) subscriptions))
        id->before+after
        (->> (for [{:crux.tx.event/keys [tx-events]} txes
                   [_ doc-id] tx-events]
               doc-id)
             distinct
             (keep (fn [doc-id]
                     (let [[before after] (mapv #(crux/entity % doc-id)
                                                [db-before db-after])]
                       (when (not= before after)
                         [(some :crux.db/id [before after])
                          [before after]]))))
             (into {}))

        norm-query->id->doc
        (with-open [node (crux/start-node {})]
          (with-open [empty-db (crux/open-db node)]
            (into {}
                  (keep (fn [query]
                          (some->>
                            (for [[id docs] id->before+after
                                  :let [[before after]
                                        (map (fn [doc]
                                               (when (and doc
                                                          (query-contains?
                                                            {:query query
                                                             :doc doc
                                                             :empty-db empty-db}))
                                                 doc))
                                             docs)]
                                  :when (not= before after)]
                              [id after])
                            not-empty
                            (into {})
                            (vector query))))
                  norm-queries)))]
    (for [{:keys [query] :as subscription} subscriptions
          :let [id->doc (get norm-query->id->doc (normalize-query query))]
          :when (some? id->doc)]
      [subscription (into {}
                          (map (fn [[id doc]]
                                 [[(:doc-type query) id] doc]))
                          id->doc)])))

(defn- notify-subscribers [{:keys [biff.crux/node
                                   biff.crux/latest-tx
                                   biff.sente/send-fn
                                   biff.crux/subscriptions] :as sys}]
  (with-open [log (crux/open-tx-log node
                                    (some-> @latest-tx
                                            :crux.tx/tx-id
                                            long)
                                    false)]
    (when-some [txes (->> log
                          iterator-seq
                          (take 20)
                          doall
                          not-empty)]
      (with-open [db-before (crux/open-db node @latest-tx)]
        (with-open [db-after (crux/open-db node (last txes))]
          (reset! latest-tx (last txes))
          (doseq [[subscription ident->doc] (subscription+updates
                                              {:txes txes
                                               :db-before db-before
                                               :db-after db-after
                                               :subscriptions @subscriptions})
                  :let [{:keys [client-id query event-id]} subscription]]
            (if-some [bad-doc (check-read
                                (assoc sys :biff/uid (:biff/uid subscription))
                                {:docs (filter some? (vals ident->doc))
                                 :query (:query subscription)
                                 :db db-after})]
              (do
                (st/print-stack-trace
                  (ex-info "Read not authorized."
                           {:query query
                            :doc bad-doc}))
                (flush)
                (swap! subscriptions disj subscription)
                (send-fn (:client-id subscription)
                         [:biff/error {:msg "Read not authorized."
                                       :event-id event-id
                                       :query query}]))
              (send-fn client-id [event-id {:query query
                                            :ident->doc ident->doc}]))))))))

(defn use-crux-sub [{:keys [biff.sente/connected-uids
                            biff.crux/node] :as sys}]
  (let [subscriptions (atom #{})
        watch (add-watch
                connected-uids
                ::rm-subs
                (fn [_ _ old-uids new-uids]
                  (let [disconnected (set/difference (:any old-uids) (:any new-uids))]
                    (when (not-empty disconnected)
                      (swap! subscriptions
                             (fn [subscriptions]
                               (into #{}
                                     (remove (fn [{:keys [client-id]}]
                                               (disconnected client-id)))
                                     subscriptions)))))))
        latest-tx (atom (or (crux/latest-completed-tx node)
                            {:crux.tx/tx-id -1}))
        sys (-> sys
                (assoc :biff.crux/subscriptions subscriptions
                       :biff.crux/latest-tx latest-tx)
                (update :biff/stop conj #(remove-watch subscriptions ::rm-subs)))
        lock (Object.)
        listener (crux/listen
                   node
                   {:crux/event-type :crux/indexed-tx}
                   (fn [_]
                     (locking lock
                       (try
                         (notify-subscribers sys)
                         (catch Throwable t
                           (st/print-stack-trace t)
                           (flush))))))]
    (update sys :biff/stop conj #(.close listener))))

(defn biff-q [{:keys [biff.crux/db
                      biff.crux/fn-whitelist]
               :as sys}
              {:keys [id where doc-type] :as query}]
  (let [fn-whitelist (into #{'= 'not= '< '> '<= '>= '== '!=} fn-whitelist)
        bad-fn (some (fn [clause]
                       (not (or (attr-clause? clause)
                                (fn-whitelist (ffirst clause)))))
                     where)
        _ (when bad-fn
            (throw (ex-info "fn in query not authorized."
                            {:fn bad-fn
                             :query query})))
        crux-query {:find '[(pull doc [*])]
                    :where (mapv #(cond->> %
                                    (attr-clause? %) (into ['doc]))
                                 where)}
        docs (if (some? id)
               (some-> (crux/entity @db id) vector)
               (map first (crux/q @db crux-query)))]
    (when-some [bad-doc (check-read
                          sys
                          {:docs docs
                           :doc-type doc-type
                           :query query
                           :db @db})]
      (throw
        (ex-info "Read not authorized."
                 {:query query :doc bad-doc})))
    (into {}
          (map (fn [doc]
                 [[doc-type (:crux.db/id doc)] doc]))
          docs)))

(defn handle-subscribe-event!
  [{event-id :id
    {:keys [query action]} :?data
    :keys [client-id
           biff.crux/subscriptions
           biff/uid
           biff.sente/send-fn]
    :as sys}]
  (if (and (= query :status) (#{:subscribe :reconnect} action))
    (send-fn client-id
             [event-id
              {:ident->doc {[:status nil] {:uid uid
                                           :client-id client-id}}
               :query :status}])
    (let [subscription {:biff/uid uid
                        :client-id client-id
                        :event-id event-id
                        :query query}]
      (when (= action :subscribe)
        (send-fn client-id [event-id {:ident->doc (biff-q sys query)
                                      :query query}]))
      (when-not (:static query)
        (case action
          :subscribe   (swap! subscriptions conj subscription)
          :reconnect   (swap! subscriptions conj subscription)
          :unsubscribe (swap! subscriptions disj subscription))))))
