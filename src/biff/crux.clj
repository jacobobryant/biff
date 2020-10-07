(ns biff.crux
  (:require
    [biff.protocols :as proto]
    [clojure.core.async :refer [go <!!]]
    [clojure.java.io :as io]
    [clojure.spec.alpha :as s]
    [clojure.walk :as walk]
    [clojure.set :as set]
    [crux.api :as crux]
    [expound.alpha :refer [expound]]
    [taoensso.timbre :refer [log spy]]
    [trident.util :as u]))

(defn dissoc-clean [m k1 k2]
  (let [m (update m k1 dissoc k2)]
    (cond-> m
      (empty? (get m k1)) (dissoc k1))))

(defn tx-log* [{:keys [node after-tx with-ops]
                :or {after-tx nil with-ops false}}]
  (crux/open-tx-log node (some-> after-tx long) with-ops))

(defmacro with-tx-log [[sym opts] & body]
  `(let [log# (tx-log* ~opts)
         ~sym (iterator-seq log#)
         result# (do ~@body)]
     (.close log#)
     result#))

(defn time-before [date]
  (-> date
    .toInstant
    (.minusMillis 1)
    java.util.Date/from))

(defn time-before-txes [txes]
  (-> txes
    first
    :crux.tx/tx-time
    time-before))

(defn attr-clause? [clause]
  (not (coll? (first clause))))

(defn normalize-query [{:keys [table where args id] :as query}]
  (if (some? id)
    {:id id}
    (u/assoc-some
      {:where where}
      :args (dissoc args 'doc))))

(defn coll-not-map? [x]
  (and
    (coll? x)
    (not (map? x))))

(defn crux== [& args]
  (let [[colls xs] (u/split-by coll-not-map? args)
        sets (map set colls)]
    (if (empty? xs)
      (not-empty (apply set/intersection sets))
      (and (apply = xs)
        (every? #(contains? % (first xs)) sets)))))

(defn crux!= [& args]
  (not (apply crux== args)))

(defn resolve-fn [sym]
  (requiring-resolve
    (if (qualified-symbol? sym)
      sym
      (symbol "clojure.core" (name sym)))))

; ==============================================================================

(defn start-node ^crux.api.ICruxAPI [{:keys [topology storage-dir]
                                      :or {topology :standalone} :as opts}]
  (let [opts (merge
               {:crux.kv/db-dir (str (io/file storage-dir "db"))}
               (case topology
                 :standalone {:crux.node/topology '[crux.standalone/topology crux.kv.rocksdb/kv-store]
                              :crux.standalone/event-log-dir (str (io/file storage-dir "eventlog"))
                              :crux.standalone/event-log-kv-store 'crux.kv.rocksdb/kv}
                 :jdbc (merge
                         {:crux.node/topology '[crux.jdbc/topology crux.kv.rocksdb/kv-store]
                          :crux.jdbc/dbtype "postgresql"}
                         (u/select-ns opts 'crux.jdbc))))
        node (crux/start-node opts)]
    (crux/sync node)
    node))

(u/sdefs
  ::ident (s/cat :table keyword? :id (s/? any?))
  ::tx (s/coll-of (s/tuple ::ident (s/nilable map?))))

; ==============================================================================

(defn doc-valid? [{:keys [verbose db-client doc specs]}]
  (->> (proto/doc->id+doc db-client doc)
    (map (fn [x-spec x]
           (try
             (doto (s/valid? x-spec x)
               #(when (and verbose (not %))
                  (expound x-spec x)))
             (catch Exception e
               (println "Exception while checking spec:"
                 (pr-str x-spec) (pr-str x))
               (.printStackTrace e))))
      specs)
    (every? true?)))

(defn authorize-read [{:keys [table doc query biff/rules biff/db-client] :as env}]
  (let [query-type (if (contains? query :id)
                     :get
                     :query)
        auth-fn (get-in rules [table query-type])
        specs (get-in rules [table :spec])
        anom-message (cond
                       (nil? auth-fn) "No auth fn."
                       (nil? specs) "No specs."
                       (not (doc-valid? {:verbose true
                                         :db-client db-client
                                         :specs specs
                                         :doc doc})) "Doc doesn't meet specs."
                       (not (u/catchall (auth-fn env))) "Doc rejected by auth fn.")]
    (if anom-message
      (u/anom :forbidden anom-message
        :norm-query query
        :table table)
      doc)))

; ==============================================================================

(defn get-query->id->doc [{:keys [queries id->before+after query-contains?]}]
  (->> queries
    (keep (fn [q]
            (some->>
              (for [[id docs] id->before+after
                    :let [[before after]
                          (map #(when (some->> % (query-contains? q))
                                  %)
                            docs)]
                    :when (not= before after)]
                [id after])
              not-empty
              (into {})
              (vector q))))
    (into {})))

(defn run-triggers [{:keys [biff/db-client batch] :as env}]
  (doseq [{:keys [table op triggers doc] :as env} (proto/get-trigger-data db-client env batch)]
    (try
      ((get-in triggers [table op]) env)
      (catch Exception e
        (.printStackTrace e)
        (log :error e "Couldn't run trigger")))))

(defn client-results [{:keys [biff/db-client batch client-id subscriptions] :as env}]
  (let [; todo: delay the values if/when needed.
        query->id->doc (get-query->id->doc
                         {:queries (distinct (mapcat keys (vals subscriptions)))
                          :id->before+after (proto/get-id->before+after db-client batch)
                          :query-contains? #(proto/query-contains-doc? db-client %1 %2)})]
    (for [[client-id query->info] (sort-by #(not= client-id (first %)) subscriptions)
          [query {:keys [table event-id session/uid] :as info}] query->info
          :when (contains? query->id->doc query)
          :let [id->doc (query->id->doc query)
                auth-anomaly (->> (vals id->doc)
                               (filter some?)
                               (map #(authorize-read
                                       (merge env
                                         {:session/uid uid
                                          :client-id client-id
                                          :table table
                                          :query query
                                          :doc %}
                                         (proto/batch->auth-fn-opts db-client batch))))
                               (filter u/anomaly?)
                               first)
                ident->doc (u/map-keys #(vector table %) id->doc)]]
      {:auth-anomaly auth-anomaly
       :client-id client-id
       :query (assoc query :table table)
       :event-id event-id
       :ident->doc ident->doc})))

(defn notify-tx* [{:biff/keys [send-event db-client]
                   :keys [biff.crux/subscriptions] :as env}]
  (when-some [batch (proto/get-batch! db-client env)]
    (future (u/fix-stdout (run-triggers (assoc env :batch batch))))
    (doseq [{:keys [auth-anomaly client-id query event-id ident->doc]}
            (client-results (assoc env
                              :subscriptions @subscriptions
                              :batch batch))]
      (if (some? auth-anomaly)
        (do
          (u/pprint auth-anomaly)
          (swap! subscriptions dissoc-clean client-id query)
          (send-event client-id [:biff/error (u/anom
                                               :forbidden "Query not allowed."
                                               :query query)]))
        (send-event client-id [event-id {:query query
                                         :changeset ident->doc}])))))

; ==============================================================================

(deftype CruxClient []
  proto/DbClient
  (get-batch! [_ {:keys [biff/node last-tx-id]}]
    (when-let [txes (not-empty
                      (with-tx-log [log {:node node
                                         :after-tx @last-tx-id}]
                        (doall (take 20 log))))]
      (let [{:crux.tx/keys [tx-id tx-time]} (last txes)]
        (reset! last-tx-id tx-id)
        {:txes txes
         :db-before (crux/db node (time-before-txes txes))
         :db-after (crux/db node tx-time)})))
  (batch->auth-fn-opts [_ {:keys [db-after]}] {:biff/db db-after})
  (get-id->before+after [_ {:keys [txes db-before db-after]}]
    (->> (for [{:crux.tx.event/keys [tx-events]} txes
               [_ doc-id] tx-events]
           doc-id)
      distinct
      (map (fn [doc-id]
             [doc-id (mapv #(crux/entity % doc-id) [db-before db-after])]))
      (remove #(apply = (second %)))
      (into {})))
  (query-contains-doc? [_ {:keys [id where args]} doc]
    (if (some? id)
      (= id (:crux.db/id doc))
      (let [args (assoc args 'doc (:crux.db/id doc))
            where (walk/postwalk #(get args % %) where)
            [attr-clauses rule-clauses] (u/split-by (comp keyword? first) where)
            [binding-clauses constant-clauses] (u/split-by (comp symbol? second) attr-clauses)
            {:keys [args fail]} (reduce (fn [{:keys [args fail]} [attr sym]]
                                          (let [value (get doc attr)]
                                            {:args (assoc args attr value)
                                             :fail (or fail
                                                     (not (contains? doc attr))
                                                     (and
                                                       (contains? args sym)
                                                       (crux!= (get args sym) value)))}))
                                  {:args args}
                                  binding-clauses)
            fail (reduce (fn [fail [attr value :as clause]]
                           (or fail
                             (not (contains? doc attr))
                             (and
                               (not= 1 (count clause))
                               (crux!= (get doc attr) value))))
                   fail
                   constant-clauses)
            rule-clauses (walk/postwalk #(get args % %) rule-clauses)]
        (not (reduce (fn [fail [[f & params]]]
                       (or fail
                         (not (apply (resolve-fn (condp = f
                                                   '== `crux==
                                                   '!= `crux!=
                                                   f)) params))))
               fail
               rule-clauses)))))
  (get-trigger-data [db-client {:biff/keys [rules triggers node] :as env} {:keys [txes]}]
    (for [{:keys [crux.tx/tx-time crux.tx.event/tx-events] :as tx} txes
          :let [db (crux/db node tx-time)
                db-before (crux/db node (time-before tx-time))]
          [tx-op doc-id] tx-events
          :when (#{:crux.tx/put :crux.tx/delete} tx-op)
          :let [doc (crux/entity db doc-id)
                doc-before (crux/entity db-before doc-id)
                doc-op (cond
                         (nil? doc) :delete
                         (nil? doc-before) :create
                         :default :update)]
          [table op->fn] triggers
          [trigger-op f] op->fn
          :let [specs (get-in rules [table :spec])]
          :when (and (= trigger-op doc-op)
                  (some #(doc-valid? {:specs specs
                                      :db-client db-client
                                      :doc %}) [doc doc-before]))]
      (assoc env
        :table table
        :op trigger-op
        :doc doc
        :doc-before doc-before
        :db db
        :db-before db-before)))
  (doc->id+doc [client {:crux.db/keys [id] :as doc}]
    [id (apply dissoc doc
          (cond-> [:crux.db/id]
            (map? id) (concat (keys id))))]))

(defn notify-tx [env]
  (notify-tx* (assoc env :biff/db-client (CruxClient.))))

; ==============================================================================

(defn crux-resubscribe*
  [{:keys [biff/db biff/fn-whitelist session/uid] event-id :id} {:keys [table] :as query}]
  (let [fn-whitelist (into #{'= 'not= '< '> '<= '>= '== '!=} fn-whitelist)
        {:keys [where id args] :as norm-query} (normalize-query query)]
    (u/letdelay [fns-authorized (every? #(or (attr-clause? %) (fn-whitelist (ffirst %))) where)
                 crux-query (cond->
                              {:find '[doc]
                               :where (mapv #(cond->> %
                                               (attr-clause? %) (into ['doc]))
                                        where)}
                              args (assoc :args [args]))]
      (cond
        (not= query (assoc norm-query :table table)) (u/anom :incorrect "Invalid query format."
                                                       :query query)
        (not fns-authorized) (u/anom :forbidden "Function call not allowed."
                               :query query)
        :default {:norm-query norm-query
                  :query-info {:table table
                               :event-id event-id
                               :session/uid uid}}))))

; todo dry with crux-resubscribe*
(defn crux-subscribe*
  [{:keys [biff/db biff/fn-whitelist session/uid] event-id :id :as env} {:keys [table] :as query}]
  (let [fn-whitelist (into #{'= 'not= '< '> '<= '>= '== '!=} fn-whitelist)
        {:keys [where id args] :as norm-query} (normalize-query query)]
    (u/letdelay [fns-authorized (every? #(or (attr-clause? %) (fn-whitelist (ffirst %))) where)
                 crux-query (cond->
                              {:find '[doc]
                               :where (mapv #(cond->> %
                                               (attr-clause? %) (into ['doc]))
                                        where)}
                              args (assoc :args [args]))
                 docs (if (some? id)
                        (some-> (crux/entity db id) vector)
                        (map #(crux/entity db (first %))
                          (crux/q db crux-query)))
                 authorize-anom (->> docs
                                  (map #(->> {:doc %
                                              :table table
                                              :biff/db-client (CruxClient.)
                                              :query norm-query}
                                          (merge env)
                                          authorize-read))
                                  (filter u/anomaly?)
                                  first)
                 changeset (u/map-from
                             (fn [{:crux.db/keys [id]}]
                               [table id])
                             docs)]
      (cond
        (not= query (assoc norm-query :table table)) (u/anom :incorrect "Invalid query format."
                                                       :query query)
        (not fns-authorized) (u/anom :forbidden "Function call not allowed."
                               :query query)
        authorize-anom authorize-anom
        :default {:norm-query norm-query
                  :query-info {:table table
                               :event-id event-id
                               :session/uid uid}
                  :sub-data {:query query
                             :changeset changeset}}))))

(defn crux-subscribe!
  [{:keys [biff/send-event biff.crux/subscriptions client-id id] :as env} query]
  (let [{:keys [norm-query query-info sub-data] :as result} (crux-subscribe* env query)]
    (if-not (u/anomaly? result)
      (do
        (send-event client-id [id sub-data])
        (swap! subscriptions assoc-in [client-id norm-query] query-info))
      result)))

(defn crux-resubscribe!
  [{:keys [biff.crux/subscriptions session/uid client-id id] :as env}
   {:keys [table] :as query}]
  (let [{:keys [norm-query query-info] :as result} (crux-resubscribe* env query)]
    (if-not (u/anomaly? result)
      (swap! subscriptions assoc-in [client-id norm-query] query-info)
      result)))

(defn crux-unsubscribe!
  [{:keys [biff.crux/subscriptions client-id session/uid]} query]
  (swap! subscriptions update client-id dissoc (normalize-query query)))

(defn wrap-sub [handler]
  (fn [{:keys [id biff/send-event client-id session/uid] {:keys [query action]} :?data :as env}]
    (if (not= :biff/sub id)
      (handler env)
      (let [result (cond
                     (and (= query :uid)
                       (#{:subscribe :resubscribe} action))
                     (send-event client-id
                       [:biff/sub {:changeset {[:uid nil]
                                               (if (some? uid)
                                                 {:uid uid}
                                                 {:uid client-id
                                                  :tmp true})}
                                   :query query}])

                     (= action :subscribe) (crux-subscribe! env query)
                     (= action :unsubscribe) (crux-unsubscribe! env query)
                     (= action :resubscribe) (crux-resubscribe! env query)
                     :default (u/anom :incorrect "Invalid action." :action action))]
        (when (u/anomaly? result)
          result)))))

; ==============================================================================

(defn prep-doc [{:biff/keys [db rules]}
                [[table id] {merge-doc :db/merge update-doc :db/update :as doc}]]
  (let [generated-id (nil? id)
        merge-update (or merge-doc update-doc)
        id' (or id (java.util.UUID/randomUUID))
        old-doc (crux/entity db id')
        doc' (cond->> doc
               merge-update (merge old-doc))
        doc'' (when (some? doc')
                (->>
                  (when (map? id') (keys id'))
                  (concat [:db/merge :db/update :crux.db/id])
                  (apply dissoc doc')
                  (remove (comp #{:db/remove} second))
                  (map (fn [[k v]]
                         [k (if (and (vector? v) (#{:db/union :db/disj} (first v)))
                              (let [[op x] v
                                    old-xs (get old-doc k)]
                                (case op
                                  :db/union ((fnil conj #{}) old-xs x)
                                  :db/disj ((fnil disj #{}) old-xs x)))
                              v)]))
                  (into {})))]
    (cond
      (and generated-id merge-update)
      (u/anom :incorrect "Attempted to merge or update on a new document."
        :doc doc
        :ident [table id])

      (and update-doc (nil? old-doc))
      (u/anom :incorrect "Attempted to update on a new document."
        :doc doc
        :ident [table id'])

      (and (some? doc'')
        (some not
          (map s/valid?
            (get-in rules [table :spec])
            [id' doc''])))
      (u/anom :incorrect "Document doesn't meet spec."
        :doc doc
        :ident [table id'])

      :default
      [[table id'] {:table table
                    :id id'
                    :generated-id generated-id
                    :old-doc old-doc
                    :doc (cond-> (assoc doc'' :crux.db/id id')
                           (map? id') (merge id'))
                    :op (cond
                          (nil? doc'') :delete
                          (nil? old-doc) :create
                          :default :update)}])))

(defn authorize-write [{:keys [biff/rules admin] :as env}
                       {:keys [table op] :as doc-tx-data}]
  (if admin
    doc-tx-data
    (u/letdelay [auth-fn (get-in rules [table op])
                 result (auth-fn (merge env doc-tx-data))
                 anom-fn #(merge (u/anom :forbidden %)
                            (select-keys doc-tx-data [:table :id]))]
      (cond
        (nil? auth-fn) (anom-fn "No auth function.")
        (not result) (anom-fn "Document rejected by auth fn.")
        :default (merge doc-tx-data (when (map? result) result))))))

(defn authorize-tx [{:keys [tx current-time] :as env
                     :or {current-time (java.util.Date.)}}]
  (if-not (s/valid? ::tx tx)
    (u/anom :incorrect "Invalid transaction shape."
      :tx tx)
    (u/letdelay [tx* (->> tx
                       (walk/postwalk
                         #(case %
                            :db/current-time current-time
                            %))
                       (map #(prep-doc env %)))
                 tx (into {} tx*)
                 env (assoc env :tx tx :current-time current-time)
                 auth-result (mapv #(authorize-write env (second %)) tx)
                 crux-tx (u/forv [{:keys [op cas old-doc doc id]} auth-result]
                           (cond
                             cas            [:crux.tx/cas old-doc doc]
                             (= op :delete) [:crux.tx/delete id]
                             :default       [:crux.tx/put doc]))]
      (or
        (first (filter u/anomaly? tx*))
        (first (filter u/anomaly? auth-result))
        crux-tx))))

(defn wrap-tx [handler]
  (fn [{:keys [id biff/node] :as env}]
    (if (not= id :biff/tx)
      (handler env)
      (let [tx (authorize-tx (set/rename-keys env {:?data :tx}))]
        (if (u/anomaly? tx)
          tx
          (crux/submit-tx node tx))))))

; ==============================================================================

; Deprecated, use submit-tx
(defn submit-admin-tx [{:biff/keys [node db rules] :as sys} tx]
  (let [db (or db (crux/db node))
        tx (authorize-tx {:tx tx
                          :biff/db db
                          :biff/rules rules
                          :admin true})
        anom (u/anomaly? tx)]
    (when anom
      (u/pprint tx))
    (if anom
      tx
      (crux/submit-tx node tx))))

(defn submit-tx [{:biff/keys [node db rules] :as sys} tx]
  (let [db (or db (crux/db node))
        tx (authorize-tx {:tx tx
                          :biff/db db
                          :biff/rules rules
                          :admin true})]
    (when (u/anomaly? tx)
      (throw (ex-info "Invalid transaction." tx)))
    (crux/submit-tx node tx)))
