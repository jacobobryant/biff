(ns biff.xtdb
  "Helper functions for xtdb.

  Also includes \"Biff transactions\" and \"Biff queries\", both of which are
  patterned after Firebase's. Biff transactions provide a higher level
  interface over xtdb.api/start-node. Biff queries are less
  powerful than xtdb.api/q, but they support subscriptions (efficiently)."
  (:require
    [biff.util :as bu]
    [biff.util.protocols :as proto]
    [clojure.java.io :as io]
    [clojure.set :as set]
    [clojure.stacktrace :as st]
    [clojure.walk :as walk]
    [clojure.spec.alpha :as s]
    [xtdb.api :as xt]
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
                          :xt/id]
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
                          :xt-tx]
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

; === vanilla xtdb ===

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
                              :db-dir (io/file dir basename)}})]
    (doto (xt/start-node
            (merge
              (case topology
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
      xt/sync)))

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
                :jdbc-spec (bu/select-ns-as sys 'biff.xtdb.jdbc nil)
                :pool-opts (bu/select-ns-as sys 'biff.xtdb.jdbc-pool nil)})]
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

(defn q-entity
  "Pulls the first document that matches a set of kv pairs.

  Example:

  (q-entity db [[:user/email \"foo@example.com\"]])
  => {:xt/id #uuid \"some-uuid\"
      :user/email \"foo@example.com\"}

  pull-expr defaults to '[*]"
  [db kvs & [pull-expr]]
  (ffirst
    (xt/q db
          {:find [(list 'pull 'doc (or pull-expr '[*]))]
           :where (vec (for [kv kvs]
                         (into ['doc] kv)))})))

; === authorize ===

(defmulti authorize
  "Extend this multimethod to provide authorization rules.

  See https://biff.findka.com/#authorization-rules"
  (fn [& [{:keys [doc-type operation]}]]
    [doc-type operation]))

(defmethod authorize :default
  [& _]
  false)

(defn check-read
  "Checks if a read operation passes authorization rules.

  Returns nil on success, otherwise returns the first unauthorized document.

  query: See https://biff.findka.com/#subscription-query-format"
  [sys {:keys [docs query db]}]
  (first
    (remove (fn [doc]
              (some (fn [op]
                      (authorize
                        (assoc sys
                               :doc-type (:doc-type query)
                               :operation op
                               :db db
                               :doc doc
                               :doc-id (:xt/id doc))
                        doc))
                    [(if (some? (:id query)) :get :query)
                     :read
                     :rw]))
            docs)))

(defn check-write
  "Checks if a write operation passes authorization rules.

  Returns nil on success, otherwise returns the first unauthorized change (see
  get-changes).

  tx-info: See get-tx-info."
  [sys tx-info]
  (let [{:keys [changes db-before db-after server-timestamp]} tx-info]
    (first
      (remove (fn [{:keys [before after doc-id doc-type]}]
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
              changes))))

; === biff tx ===

(defn normalize-tx-doc
  "Converts a TX doc to a xtdb doc.

  server-timestamp: A Date object.
  doc-id:           The xtdb document ID.
  before:           The xtdb document's current value (i.e. before the
                    transaction). nil if the document is being created.
  tx-doc:           See https://biff.findka.com/#transactions."
  [{:keys [server-timestamp
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
                                  (if (= x :db/server-timestamp)
                                    server-timestamp
                                    x)))
                 (keep (fn [[k v]]
                         (when (not= v :db/remove)
                           [k (if (and (coll? v)
                                       (<= 2 (count v))
                                       (#{:db/union
                                          :db/difference
                                          :db/add
                                          :db/default} (first v)))
                                (let [[op & xs] v
                                      v-before (get before k)]
                                  ((case op
                                     :db/union #(set/union (set %1) (set %2))
                                     :db/difference #(set/difference (set %1) (set %2))
                                     :db/add (fn [x xs]
                                               (apply + (or x 0) xs))
                                     :db/default (fn [v-before [default-value]]
                                                   (if (contains? before k)
                                                     v-before
                                                     default-value)))
                                   v-before
                                   xs))
                                v)])))
                 (into {}))]
    doc))

(defn get-changes
  "Return a list of changes that will occur after a transaction.

  See https://biff.findka.com/#tx-docs.

  server-timestamp: A Date object.
  random-uuids:     A list of UUIDs to use for new documents.
  biff-tx:          See https://biff.findka.com/#transactions.
  db:               A xtdb DB.

  Each change is a map with the following keys:
  :before   - The affected document's current value.
  :after    - The affected document's value after the transaction occurs.
  :tx-doc   - An element of biff-tx.
  :doc-type
  :doc-id"
  [{:keys [db server-timestamp biff-tx random-uuids]}]
  (for [[{:keys [db/doc-type xt/id db/delete] :as tx-doc}
         random-uuid] (map vector biff-tx random-uuids)
        :let [id (or id random-uuid)
              before (xt/entity db id)
              after (when (not delete)
                      (normalize-tx-doc
                        {:doc-id id
                         :before before
                         :tx-doc tx-doc
                         :server-timestamp server-timestamp}))]]
    {:tx-doc tx-doc
     :doc-id id
     :doc-type doc-type
     :before before
     :after after}))

(defn get-tx-info
  "Return a map with information needed to authorize and run a transaction.

  schema:  An implementation of biff.util.protocols/Schema.
  db:      An XTDB DB.
  biff-tx: See https://biff.findka.com/#transactions.

  Returns the following keys:
  :xt-tx            - A xtdb transaction.
  :changes            - See get-changes.
  :server-timestamp   - A Date object.
  :db-before          - db.
  :db-after           - (xtdb.api/with-tx db xt-tx)."
  [{:keys [biff/schema biff/db]} biff-tx]
  (when-not (malc/validate [:sequential [:map-of keyword? any?]] (vec biff-tx))
    ; Ideally we'd include Malli's explain + humanize output, but it had some
    ; weird results (including an exception) when I tested it on a few
    ; examples. TODO see if this is still the case. (Though with simplified
    ; format is it even necessary?))
    (bu/throw-anom :incorrect "TX doesn't match schema."
                   {:tx biff-tx}))
  (let [schema (bu/realize schema)
        server-timestamp (java.util.Date.)
        changes (get-changes {:db db
                              :server-timestamp server-timestamp
                              :biff-tx biff-tx
                              :random-uuids (repeatedly
                                              (fn []
                                                (java.util.UUID/randomUUID)))})
        xt-tx (into
                  (mapv (fn [{:keys [doc-id before]}]
                          [::xt/match doc-id before])
                        changes)
                  (mapv (fn [{:keys [after doc-id]}]
                          (if after
                            [::xt/put after]
                            [::xt/delete doc-id]))
                        changes))]
    (doseq [{:keys [before doc-type tx-doc]
             :as change-item} changes]
      (when (and (nil? before) (:db/update tx-doc))
        (bu/throw-anom :incorrect "Attempted to update on a new doc."
                       {:tx-doc tx-doc}))
      (doseq [k [:before :after]
              :let [doc (k change-item)]]
        (when (and (some? doc) (not (proto/valid? schema doc-type doc)))
          (bu/throw-anom :incorrect "Doc doesn't match doc-type."
                         {:tx-doc tx-doc
                          k doc
                          :explain (proto/explain-human schema doc-type doc)}))))
    {:changes changes
     :server-timestamp server-timestamp
     :db-before db
     :db-after (xt/with-tx db xt-tx)
     :xt-tx xt-tx}))

(defn submit-tx
  "Submits a Biff transaction.

  node:      A xtdb node.
  authorize: true if the transaction is required to pass authorization rules
             (default false).
  biff-tx:   See https://biff.findka.com/#transactions."
  [{:biff.xtdb/keys [node authorize] :as sys} biff-tx]
  (let [sys (assoc-db sys)
        n-tried (:biff.xtdb/n-tried sys 0)
        {:keys [xt-tx] :as tx-info} (get-tx-info sys biff-tx)
        _ (when-let [bad-change (and authorize (check-write sys tx-info))]
            (bu/throw-anom :forbidden "TX not authorized."
                           bad-change))
        submitted-tx (xt/submit-tx node xt-tx)]
    (xt/await-tx node submitted-tx)
    (cond
      (xt/tx-committed? node submitted-tx) submitted-tx
      (< n-tried 4) (let [seconds (int (Math/pow 2 n-tried))]
                      (printf "TX failed due to contention, trying again in %d seconds...\n"
                              seconds)
                      (flush)
                      (Thread/sleep (* 1000 seconds))
                      (-> sys
                          (update :biff.xtdb/n-tried (fnil inc 0))
                          (submit-tx biff-tx)))
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
    (= id (:xt/id doc))
    (not
      (empty?
        (xt/q (xt/with-tx empty-db [[::xt/put doc]])
                {:find '[doc]
                 :where (mapv #(cond->> %
                                 (attr-clause? %) (into ['doc]))
                              where)})))))

(defn- subscription+updates
  [{:keys [txes db-before db-after subscriptions]}]
  (let [norm-queries (distinct (map (comp normalize-query :query) subscriptions))
        id->before+after
        (->> (for [{::xt/keys [tx-events]} txes
                   [_ doc-id] tx-events]
               doc-id)
             distinct
             (keep (fn [doc-id]
                     (let [[before after] (mapv #(xt/entity % doc-id)
                                                [db-before db-after])]
                       (when (not= before after)
                         [(some :xt/id [before after])
                          [before after]]))))
             (into {}))

        norm-query->id->doc
        (with-open [node (xt/start-node {})]
          (with-open [empty-db (xt/open-db node)]
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

(defn- notify-subscribers [{:keys [biff.xtdb/node
                                   biff.xtdb/latest-tx
                                   biff.sente/send-fn
                                   biff.xtdb/subscriptions] :as sys}]
  (with-open [log (xt/open-tx-log node
                                    (some-> @latest-tx
                                            ::xt/tx-id
                                            long)
                                    false)]
    (when-some [txes (->> log
                          iterator-seq
                          (take 20)
                          doall
                          not-empty)]
      (with-open [db-before (xt/open-db node @latest-tx)
                  db-after (xt/open-db node (last txes))]
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
                                          :ident->doc ident->doc}])))))))

(defn use-xt-sub-notifier
  "Sends new query results to subscribed clients.

  See https://biff.findka.com/#subscription-interface.

  Sets :biff.xtdb/subscriptions to (atom #{}). To add subscriptions, insert
  maps with these keys:
  :biff/uid  - The subscriber's UID.
  :client-id - The subscriber's Sente client ID.
  :event-id  - The Sente event ID used to send this transaction.
  :query     - The Biff query (see https://biff.findka.com/#subscription-query-format)

  Adds a watch to connected-uids and removes subscriptions if their clients
  disconnect."
  [{:keys [biff.sente/connected-uids
           biff.xtdb/node] :as sys}]
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
        latest-tx (atom (or (xt/latest-completed-tx node)
                            {::xt/tx-id -1}))
        sys (-> sys
                (assoc :biff.xtdb/subscriptions subscriptions
                       :biff.xtdb/latest-tx latest-tx)
                (update :biff/stop conj #(remove-watch subscriptions ::rm-subs)))
        lock (Object.)
        listener (xt/listen
                   node
                   {::xt/event-type ::xt/indexed-tx}
                   (fn [_]
                     (locking lock
                       (try
                         (notify-subscribers sys)
                         (catch Throwable t
                           (st/print-stack-trace t)
                           (flush))))))]
    (update sys :biff/stop conj #(.close listener))))

(defn biff-q
  "Executes a Biff query.

  db:           A delayed xtdb DB.
  fn-whitelist: A collection of symbols for functions that are allowed to run
                in the xtdb query. Symbols for non-core functions must be
                fully-qualified.
  query:        See https://biff.findka.com/#subscription-query-format."
  [{:keys [biff/db
           biff.xtdb/fn-whitelist]
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
        xt-query {:find '[(pull doc [*])]
                    :where (mapv #(cond->> %
                                    (attr-clause? %) (into ['doc]))
                                 where)}
        docs (if (some? id)
               (some-> (xt/entity db id) vector)
               (map first (xt/q db xt-query)))]
    (when-some [bad-doc (check-read
                          sys
                          {:docs docs
                           :doc-type doc-type
                           :query query
                           :db db})]
      (throw
        (ex-info "Read not authorized."
                 {:query query :doc bad-doc})))
    (into {}
          (map (fn [doc]
                 [[doc-type (:xt/id doc)] doc]))
          docs)))

(defn handle-subscribe-event!
  "Sends query results to the client and subscribes them to future changes.

  See use-xt-sub-notifier and https://biff.findka.com/#subscriptions."
  [{event-id :id
    {:keys [query action]} :?data
    :keys [client-id
           biff.xtdb/subscriptions
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
        (try
          (send-fn client-id [event-id {:ident->doc (biff-q sys query)
                                        :query query}])
          (catch Exception e
            ; biff.client/maintain-subscriptions waits until initial query
            ; results have been received before it continues processing
            ; subscriptions, so we need to send something.
            (send-fn client-id [event-id {:ident->doc {}
                                          :query query}])
            (throw e))))
      (when-not (:static query)
        (case action
          :subscribe   (swap! subscriptions conj subscription)
          :reconnect   (swap! subscriptions conj subscription)
          :unsubscribe (swap! subscriptions disj subscription))))))
