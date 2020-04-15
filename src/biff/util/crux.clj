(ns biff.util.crux
  (:require
    [biff.util :as bu]
    [clojure.java.io :as io]
    [clojure.spec.alpha :as s]
    [clojure.walk :as walk]
    [clojure.set :as set]
    [crux.api :as crux]
    [trident.util :as u]))

(defn start-node ^crux.api.ICruxAPI [{:keys [storage-dir persist]
                                      :or {persist true}}]
  (crux/start-node {:crux.node/topology (cond-> '[crux.standalone/topology]
                                          persist (conj 'crux.kv.rocksdb/kv-store))
                    :crux.kv/db-dir (str (io/file storage-dir))}))

(bu/sdefs
  ::ident (s/cat :table keyword? :id (s/? any?))
  ::tx (s/map-of ::ident map?))

(defn prep-doc [{:keys [db rules]}
                [[table id] {merge-doc :db/merge update-doc :db/update :as doc}]]
  (let [generated-id (nil? id)
        merge-update (or merge-doc update-doc)
        _ (when (and generated-id merge-update)
            (throw (ex-info "Attempted to merge or update on a new document."
                     {:doc doc
                      :ident [table id]})))
        id (or id (java.util.UUID/randomUUID))
        old-doc (crux/entity db id)
        doc (if merge-update
              (do
                (when (and update-doc (nil? old-doc))
                  (throw (ex-info "Attempted to update on a new document."
                           {:doc doc
                            :ident [table id]})))
                (merge old-doc doc))
              doc)
        doc (when (some? doc)
              (->>
                (when (map? id) (keys id))
                (concat [:db/merge :db/update :db.crux/id])
                (apply dissoc doc)
                (remove (comp #{:db/remove} second))
                (into {})))]
    (when (and (some? doc)
            (some not
              (map s/valid?
                (get-in rules [table :spec])
                [id doc])))
      (throw (ex-info "Document doesn't meet spec."
               {:doc doc
                :ident [table id]})))
    [[table id] {:table table
                 :id id
                 :generated-id generated-id
                 :old-doc old-doc
                 :doc (cond-> (assoc doc :crux.db/id id)
                        (map? id) (merge id))
                 :op (cond
                       (nil? doc) :delete
                       (nil? old-doc) :create
                       :default :update)}]))

(defn authorize-write [{:keys [rules] :as env} {:keys [table op] :as doc-tx-data}]
  (let [auth-fn (get-in rules [table op])
        _ (when (nil? auth-fn)
            (throw (ex-info "No auth function." doc-tx-data)))
        result (auth-fn (merge env doc-tx-data))]
    (when-not result
      (throw (ex-info "Document rejected."
               doc-tx-data)))
    (cond-> doc-tx-data
      (map? result) (merge result))))

(defn authorize-tx [{:keys [tx] :as env}]
  (when-not (s/valid? ::tx tx)
    (ex-info "Invalid transaction shape."
      {:tx tx}))
  (let [current-time (java.util.Date.)
        tx (->> tx
             (walk/postwalk
               #(case %
                  :db/current-time current-time
                  %))
             (map #(prep-doc env %))
             (into {}))
        env (assoc env :tx tx :current-time current-time)
        auth-result (mapv #(authorize-write env (second %)) tx)
        crux-tx (for [{:keys [op cas old-doc doc id]} auth-result]
                  (cond
                    cas            [:crux.tx/cas old-doc doc]
                    (= op :delete) [:crux.tx/delete id]
                    :default       [:crux.tx/put doc]))]
    crux-tx))

(defn attr-clause? [clause]
  (not (coll? (first clause))))

(defn normalize-query [{:keys [table where args id] :as query}]
  (if (some? id)
    {:id id}
    (u/assoc-some
      {:where where}
      :args (dissoc args 'doc))))

(defn subscribe-data
  [{:keys [uid db fn-whitelist]} {:keys [table] :as query}]
  (let [fn-whitelist (into #{= not= < > <= >= == !=} fn-whitelist)
        {:keys [where id args] :as query} (normalize-query query)
        authorized (not (some #(or (attr-clause? %) (fn-whitelist (ffirst %)))))
        docs (cond
               (not authorized) nil
               (some? id) (some-> (crux/entity db id) vector)
               (map #(crux/entity db (first %))
                 (crux/q db
                   {:find '[doc]
                    :where (map #(cond->> %
                                   (attr-clause? %) (into ['doc]))
                             where)
                    :args [args]})))
        authorized (and authorized
                     (u/for-every? [d docs]
                       (not= :unauthorized
                         (authorize-read
                           (merge env
                             {:table table
                              :doc d
                              :query query})))))
        changeset (u/map-from
                    (fn [{:crux.db/keys [id]}]
                      [table id])
                    docs)]
    (when authorized
      {:query query
       :sub-data {:query (assoc query :table table)
                  :changeset changeset}})))

(defn crux-subscribe!
  [{:keys [api-send subscriptions client-id uid] :as env} {:keys [table] :as query}]
  (let [{:keys [query sub-data]} (subscribe-data env query)
        authorized (boolean sub-data)]
    (when authorized
      (swap! subscriptions assoc-in [client-id query] {:table table
                                                       :uid uid})
      (api-send client-id [:findka/sub sub-data])
      true)))

(defn crux-unsubscribe!
  [{:keys [subscriptions client-id uid]} query]
  (swap! subscriptions update client-id dissoc (normalize-query query)))

(defn crux== [& args]
  (let [[colls xs] (u/split-by coll? args)
        sets (map set colls)]
    (if (empty? xs)
      (not-empty (apply set/intersection sets))
      (and (apply = xs)
        (every? #(contains? % (first xs)) sets)))))

(defn crux!= [& args]
  (not (apply crux== args)))

; todo fix cardinality-many attr handling
; maybe just spin up a temp crux db
(defn query-contains? [{:keys [id where args]} doc]
  (or (= id (:crux.db/id doc))
    (let [where (walk/postwalk #(get args % %) where)
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
                       (apply (requiring-resolve (condp = f
                                                   '== `crux==
                                                   '!= `crux!=
                                                   f)) params)))
             fail
             rule-clauses)))))

(defn authorize-read [{:keys [table uid db doc query rules]}]
  (let [query-type (if (contains? query :id)
                     :get
                     :query)
        id (:crux.db/id doc)
        auth-doc (if (map? id)
                   (apply dissoc doc (keys id))
                   doc)
        auth-doc (dissoc auth-doc :crux.db/id)
        auth-fn (get-in rules [table query-type])
        specs (get-in rules [table :spec])]
    (if (and
          (some? auth-fn)
          (some? spec)
          (every? s/valid? specs [id auth-doc])
          (u/catchall (auth-fn {:db db :doc doc :auth-uid uid})))
      doc
      :unauthorized)))

(defn changeset [{:keys [db-after bypass-auth query id->change] :as env}]
  (->> id->change
    (u/map-vals
      (fn [change]
        (walk/postwalk
          #(when (or (not (map? %))
                   (query-contains? query %))
             %)
          change)))
    (remove (comp #(= %1 %2) second))
    (u/map-vals (comp #(if bypass-auth
                         %
                         (authorize-read (assoc env :doc % :db db-after)))
                  second))))

; todo make sure there aren't race conditions for subscribing and receiving updates
; make this functional
; spec env
(defn send-changesets [{:keys [api-send uid client-id subscriptions] :as env}]
  (reduce (fn [query->id->doc [client-id query->info]]
            (let [queries (keys query->info)
                  query->id->doc (->> queries
                                   (remove query->id->doc)
                                   (u/map-to #(changeset (merge (query->info %) env {:query %})))
                                   (merge query->id->doc))]
              (doseq [q queries
                      :let [id->doc (query->id->doc q)
                            unauthorized (= id->doc :unauthorized)
                            {:keys [table]} (query->info q)
                            changeset (delay (u/map-keys #(vector table %) id->doc))]
                      :when (or unauthorized (not-empty id->doc))]
                (if unauthorized
                  (do
                    (swap! subscriptions update client-id dissoc q)
                    (u/pprint [:unauthorized-subscription uid client-id q]))
                  (api-send client-id [:findka/sub {:query (assoc q :table table)
                                                    :changeset @changeset}])))
              query->id->doc))
    {}
    @subscriptions))

(defn tx-log [{:keys [node after-tx with-ops]}]
  (iterator-seq (crux/open-tx-log node after-tx with-ops)))

(defn notify-subscribers [{:keys [node client-id last-tx-id tx] :as env}]
  (crux/await-tx tx)
  (and (crux/tx-committed? node)
    (let [txes (take 20 (tx-log {:node node
                                 :after-tx @last-tx-id}))
          tx-time-before (-> txes
                           first
                           :crux.tx/tx-time
                           .toInstant
                           (.minusMillis 1)
                           java.util.Date/from)
          {:crux.tx/keys [tx-id tx-time]} (last txes)
          db-before (crux/db node tx-time-before)
          db-after (crux/db node tx-time)
          id->change (->> (for [{:crux.tx.event/keys [tx-events]} txes
                                [_ doc-id] tx-events]
                            doc-id)
                       distinct
                       (map (fn [doc-id]
                              (mapv #(crux/entity % doc-id) [db-before db-after])))
                       distinct
                       (remove #(= %1 %2))
                       (u/map-from #(some :crux.db/id %)))]
      (send-changesets
        (assoc env
          :db-after db-after
          :id->change id->change))
      (reset! last-tx-id tx-id))))
