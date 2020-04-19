(ns biff.util.crux
  (:require
    [biff.util :as bu]
    [cognitect.anomalies :as anom]
    [clojure.java.io :as io]
    [clojure.spec.alpha :as s]
    [clojure.walk :as walk]
    [clojure.set :as set]
    [crux.api :as crux]
    [orchestra.core :refer [defn-spec]]
    [trident.util :as u]))

(defn start-node ^crux.api.ICruxAPI [{:keys [storage-dir persist]
                                      :or {persist true}}]
  (crux/start-node {:crux.node/topology (cond-> '[crux.standalone/topology]
                                          persist (conj 'crux.kv.rocksdb/kv-store))
                    :crux.kv/db-dir (str (io/file storage-dir "db"))
                    :crux.standalone/event-log-dir (str (io/file storage-dir "eventlog"))
                    :crux.standalone/event-log-kv-store 'crux.kv.rocksdb/kv
                    :crux.kv/sync? true}))

(bu/sdefs
  ::ident (s/cat :table keyword? :id (s/? any?))
  ::tx (s/coll-of (s/tuple ::ident (s/nilable map?))))

(defn prep-doc [{:keys [db rules]}
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
                  (into {})))]
    (cond
      (and generated-id merge-update)
      (bu/anom :incorrect "Attempted to merge or update on a new document."
        :doc doc
        :ident [table id])

      (and update-doc (nil? old-doc))
      (bu/anom :incorrect "Attempted to update on a new document."
        :doc doc
        :ident [table id'])

      (and (some? doc'')
        (some not
          (map s/valid?
            (get-in rules [table :spec])
            [id' doc''])))
      (bu/anom :incorrect "Document doesn't meet spec."
        :doc doc''
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

(defn authorize-write [{:keys [rules] :as env}
                       {:keys [table op] :as doc-tx-data}]
  (bu/letdelay [auth-fn (get-in rules [table op])
                result (auth-fn (merge env doc-tx-data))]
    (merge doc-tx-data
      (cond
        (nil? auth-fn) (bu/anom :forbidden "No auth function.")
        (not result) (bu/anom :forbidden "Document rejected.")
        :default (when (map? result) result)))))

(defn authorize-tx [{:keys [tx current-time] :as env
                     :or {current-time (java.util.Date.)}}]
  (if-not (s/valid? ::tx tx)
    (bu/anom :incorrect "Invalid transaction shape."
      :tx tx)
    (bu/letdelay [tx* (->> tx
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
        (first (filter bu/anomaly? tx*))
        (first (filter bu/anomaly? auth-result))
        crux-tx))))

(defn attr-clause? [clause]
  (not (coll? (first clause))))

(defn normalize-query [{:keys [table where args id] :as query}]
  (if (some? id)
    {:id id}
    (u/assoc-some
      {:where where}
      :args (dissoc args 'doc))))

(defn crux== [& args]
  (let [[colls xs] (u/split-by coll? args)
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

(defn query-contains? [{:keys [id where args]} doc]
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

(defn doc-valid? [[id-spec doc-spec] {:crux.db/keys [id] :as doc}]
  (let [doc (apply dissoc doc
              (cond-> [:crux.db/id]
                (map? id) (concat (keys id))))]
    (every? true? (map s/valid? [id-spec doc-spec] [id doc] ))))

(defn authorize-read [{:keys [table uid db doc query rules]}]
  (let [query-type (if (contains? query :id)
                     :get
                     :query)
        auth-fn (get-in rules [table query-type])
        specs (get-in rules [table :spec])
        anom-message (cond
                       (nil? auth-fn) "No auth fn."
                       (nil? specs) "No specs."
                       (not (doc-valid? specs doc)) "Doc doesn't meet specs."
                       (not (u/catchall (auth-fn {:db db :doc doc :auth-uid uid}))) "Doc rejected.")]
    (if anom-message
      (bu/anom :forbidden anom-message
        :query query
        :table table
        :query-type query-type)
      doc)))

(defn crux-subscribe*
  [{:keys [db fn-whitelist uid event-id] :as env} {:keys [table] :as query}]
  (let [fn-whitelist (into #{'= 'not= '< '> '<= '>= '== '!=} fn-whitelist)
        {:keys [where id args] :as norm-query} (normalize-query query)]
    (bu/letdelay [fns-authorized (every? #(or (attr-clause? %) (fn-whitelist (ffirst %))) where)
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
                  docs-authorized (u/for-every? [d docs]
                                    (->>
                                      {:table table
                                       :doc d
                                       :query norm-query}
                                      (merge env)
                                      authorize-read
                                      bu/anomaly?
                                      not))
                  changeset (u/map-from
                              (fn [{:crux.db/keys [id]}]
                                [table id])
                              docs)]
      (cond
        (not= query (assoc norm-query :table table)) (bu/anom :incorrect "Invalid query format."
                                                       :query query)
        (not fns-authorized) (bu/anom :forbidden "Function call not allowed."
                               :where where)
        (not docs-authorized) (bu/anom :forbidden "Document read not allowed."
                                :docs docs)
        :default {:norm-query norm-query
                  :query-info {:table table
                               :event-id event-id
                               :uid uid}
                  :sub-data {:query query
                             :changeset changeset}}))))

(defn crux-subscribe!
  [{:keys [api-send subscriptions client-id event-id] :as env} query]
  (let [{:keys [norm-query query-info sub-data] :as result} (crux-subscribe* env query)]
    (if-not (bu/anomaly? result)
      (do
        (api-send client-id [event-id sub-data])
        (swap! subscriptions assoc-in [client-id norm-query] query-info))
      result)))

(defn crux-unsubscribe!
  [{:keys [subscriptions client-id uid]} query]
  (swap! subscriptions update client-id dissoc (normalize-query query)))

(defn get-id->doc [{:keys [db-after bypass-auth query id->change] :as env}]
  (->> id->change
    (u/map-vals
      (fn [change]
        (map
          #(when (or (not (map? %))
                   (query-contains? query %))
             %)
          change)))
    (remove (comp (fn [[a b]] (= a b)) second))
    (u/map-vals (comp #(if (or (nil? %) bypass-auth)
                         %
                         (authorize-read (assoc env :doc % :db db-after)))
                  second))))

; todo fix race conditions for subscribing and receiving updates
(defn changesets* [{:keys [subscriptions] :as env}]
  ((fn step [{:keys [query->id->doc subscriptions]}]
     (let [[[client-id query->info] & subscriptions] subscriptions
           queries (keys query->info)
           query->id->doc (->> queries
                            (remove query->id->doc)
                            (u/map-to #(get-id->doc (merge env (query->info %) {:query %})))
                            (merge query->id->doc))]
       (concat (for [q queries
                     :let [id->doc (query->id->doc q)
                           anom (->> id->doc
                                  vals
                                  (filter bu/anomaly?)
                                  first)
                           {:keys [table event-id]} (query->info q)
                           changeset (delay (u/map-keys #(vector table %) id->doc))]
                     :when (or anom (not-empty id->doc))]
                 (if anom
                   (assoc anom
                     :client-id client-id
                     :event-id event-id
                     :query q)
                   {:client-id client-id
                    :event-id event-id
                    :query (assoc q :table table)
                    :changeset @changeset}))
         (when (not-empty subscriptions)
           (lazy-seq
             (step {:query->id->doc query->id->doc
                    :subscriptions subscriptions}))))))
   {:query->id->doc {}
    :subscriptions subscriptions}))

(defn tx-log [{:keys [node after-tx with-ops]
               :or {after-tx nil with-ops false}}]
  (iterator-seq (crux/open-tx-log node after-tx with-ops)))

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

(defn get-id->change [{:keys [txes db-before db-after]}]
  (->> (for [{:crux.tx.event/keys [tx-events]} txes
             [_ doc-id] tx-events]
         doc-id)
    distinct
    (map (fn [doc-id]
           (mapv #(crux/entity % doc-id) [db-before db-after])))
    distinct
    (remove #(apply = %))
    (u/map-from #(some :crux.db/id %))))

(defn changesets [{:keys [client-id] :as env}]
  (-> env
    (assoc :id->change (get-id->change env))
    (update :subscriptions (fn [xs] (sort-by #(not= client-id (first %)) xs)))
    changesets*))

(defn trigger-data [{:keys [rules triggers id->change txes node] :as env}]
  (for [{:keys [crux.tx/tx-time crux.tx.event/tx-events] :as tx} txes
          :let [db (crux/db node tx-time)
                db-before (crux/db node (time-before tx-time))]
          [_ doc-id] tx-events
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
                  (some #(doc-valid? specs %) [doc doc-before]))]
    (assoc env
      :table table
      :op trigger-op
      :doc doc
      :doc-before doc-before
      :db db
      :db-before db-before)))

(defn run-triggers [env]
  (doseq [{:keys [table op triggers doc] :as env}
          (trigger-data env)]
    (u/pprint [:calling-trigger table op])
    ((get-in triggers [table op]) env)))

;(u/pprint ((juxt :id->change :subscriptions changesets) env))

(defn notify-tx [{:keys [triggers api-send node last-tx-id tx subscriptions] :as env}]
  (crux/await-tx node tx)
  (and (crux/tx-committed? node tx)
    (let [txes (take 20 (tx-log {:node node
                                 :after-tx @last-tx-id}))
          {:crux.tx/keys [tx-id tx-time]} (last txes)
          {:keys [id->change] :as env} (-> env
                                         (update :subscriptions deref)
                                         (assoc
                                           :txes txes
                                           :db-before (crux/db node (time-before-txes txes))
                                           :db-after (crux/db node tx-time))
                                         (#(assoc % :id->change (get-id->change %))))
          changesets (changesets env)]
      (u/pprint [:processed (take 20 (tx-log {:node node :after-tx @last-tx-id :with-ops true}))])
      (def env env)
      (future (bu/fix-stdout (run-triggers env)))
      (when (not-empty txes)
        (doseq [{:keys [client-id query changeset event-id] :as result} changesets]
          (if (bu/anomaly? result)
            (do
              (swap! subscriptions
                #(let [subscriptions (update % client-id dissoc query)]
                   (cond-> subscriptions
                     (empty? (get subscriptions client-id)) (dissoc client-id))))
              (api-send client-id [:biff/error (bu/anom :forbidden "Query not allowed."
                                                 :query query)]))
            (api-send client-id [event-id {:query query
                                           :changeset changeset}]))))
      (reset! last-tx-id tx-id)
      true)))

(comment

  ; todo infer parts of this
  (def params
    '{prep-doc {:keys [:db :rules]}
      doc-tx-data {:keys [:table :id :generated-id :old-doc :doc :op]}
      read-auth-fn {:keys [:db :doc :auth-uid]}
      write-auth-fn {:keys [:db :auth-uid :tx :current-time]
                     :fns {doc-tx-data nil}}
      authorize-write {:keys [:rules]
                       :fns {write-auth-fn [:table :id :generated-id :old-doc :doc :op]}}
      authorize-tx {:keys [:tx]
                    :fns {prep-doc nil
                          authorize-write [:current-time]}}
      authorize-read {:keys [:table :uid :db :doc :query :rules]}
      crux-subscribe* {:keys [:db :uid :event-id]
                       :fns {authorize-read [:table :doc :query]}}
      crux-subscribe! {:keys [:api-send :subscriptions :client-id]
                       :fns {crux-subscribe* nil}}
      crux-unsubscribe! {:keys [:subscriptions :client-id :uid]}
      get-id->doc {:keys [:db-after :query :id->change]
                   :fns {authorize-read [:doc :db]}}
      changesets* {:keys [:subscriptions]
                   :fns {get-id->doc [:query :table :uid :event-id]} }
      get-id->change {:keys [:txes :db-before :db-after]}
      changesets {:fns {get-id->change nil
                        changesets* [:id->change]}}
      notify-tx {:keys [:api-send :node :client-id :last-tx-id :tx :subscriptions]
                          :fns {changesets [:txes :db-before :db-after]}}})

  (bu/infer-keys params)
  {prep-doc [:db :rules],
   get-id->doc [:table :db-after :uid :rules :id->change :query],
   authorize-tx [:db :rules :tx :auth-uid],
   crux-unsubscribe! [:client-id :uid :subscriptions],
   changesets* [:db-after :rules :subscriptions :id->change],
   changesets [:db-after :txes :rules :db-before :subscriptions],
   authorize-write [:current-time :db :rules :tx :auth-uid],
   doc-tx-data [:table :generated-id :op :id :old-doc :doc],
   write-auth-fn [:current-time :table :db :generated-id :op :tx :id :old-doc :doc :auth-uid],
   notify-tx [:client-id :last-tx-id :api-send :node :rules :subscriptions :tx],
   authorize-read [:table :db :uid :rules :query :doc],
   read-auth-fn [:db :doc :auth-uid],
   crux-subscribe* [:event-id :db :uid :rules],
   get-id->change [:db-after :txes :db-before],
   crux-subscribe! [:event-id :client-id :db :uid :api-send :rules :subscriptions]}


  (defn tmp-node []
    (start-node {:storage-dir (bu/tmp-dir)
                 :persist false}))

  (defmacro with-db [id->doc & forms]
    `(with-open [node# (tmp-node)]
       (->>
         (for [[id# doc#] ~id->doc]
           [:crux.tx/put
            (assoc doc# :crux.db/id id#)])
         (crux/submit-tx node#)
         (crux/await-tx node#))
       (let [~'db (crux/db node#)]
         ~@forms)))

  (query-contains?
    {:id :foo}
    {:crux.db/id :foo})

  (query-contains?
    {:where [:foo 3]}
    {:foo 3})

  (query-contains?
    {:where [:foo #{3}]}
    {:foo [3]})

  (with-db {:foo {:bar 3}}
    (crux/entity db :foo))

  (bu/anomaly? {::anom/category ::anom/incorrect})
  (bu/anom :incorrect)
  (bu/anom :incorrect "hello")
  (bu/anomaly? (bu/anom :incorrect "hello"))

  (defmacro test-prep-doc [db & forms]
    `(u/pprint
       (with-db ~db
         (prep-doc ~@forms))))

  (test-prep-doc {}
    {:db db
     :rules {:some-table {:spec [uuid? #{{:foo 3}}]}}}
    [[:some-table #uuid "171a3404-cc31-498b-848e-7dabc04a1daa"] {:foo 3}])


  (test-prep-doc {}
    {:db db
     :rules {:some-table {:spec [uuid? #{{:foo 3}}]}}}
    [[:some-table] {:foo 3}])

  (test-prep-doc {:doc-id {:foo 1}}
    {:db db
     :rules {:some-table {:spec [uuid? #{{:foo 3}}]}}}
    [[:some-table :foo] {:foo 3}])

  (test-prep-doc {:doc-id {:foo 1}}
    {:db db
     :rules {:some-table {:spec [keyword? #{{:foo 2}}]}}}
    [[:some-table :foo] {:foo 3}])

  (test-prep-doc {{:a 1 :b 2} {:foo 1 :bar "hey"}}
    {:db db
     :rules {:some-table {:spec [map? #{{:foo 2}}]}}}
    [[:some-table {:a 1 :b 2}] {:foo 2}])

  (test-prep-doc {{:a 1 :b 2} {:foo 1 :bar "hey" :a 1 :b 2}}
    {:db db
     :rules {:some-table {:spec [map? #{{:foo 2 :bar "hey"}}]}}}
    [[:some-table {:a 1 :b 2}] {:foo 2
                                :db/update true}])

  (authorize-write
    {:rules {:some-table {:create (constantly true)}}}
    {:table :some-table
     :op :create})

  (authorize-write
    {:rules {:some-table {:create (constantly false)}}}
    {:table :some-table
     :op :create})

  (authorize-write
    {}
    {:table :some-table
     :op :create})

  (with-db {{:a 1 :b 2} {:foo 1}}
    (authorize-tx
      {:db db
       :tx {[:some-table {:a 1 :b 2}] {:foo 2}}
       :rules {:some-table {:spec [map? (s/keys :req-un [::foo])]
                            :update (constantly true)}}}))

  (with-db {{:a 1 :b 2} {:foo 1}}
    (authorize-tx
      {:db db
       :tx {[:some-table {:a 1 :b 2}] {:foo 2}}
       :rules {:some-table {:spec [map? (s/keys :req-un [::foo])]
                            :update (constantly false)}}}))

(with-db {{:a 1 :b 2} {:foo 1}}
  (authorize-tx
    {:db db
     :tx {[:some-table {:a 1 :b 2}] {:foo 2}}
     :rules {:some-table {:spec [map? (s/keys :req-un [::foo ::bar])]
                          :update (constantly true)}}}))

(with-db {{:a 1 :b 2} {:foo 1}}
  (authorize-tx
    {:db db
     :tx "hi"
     :rules {:some-table {:spec [map? (s/keys :req-un [::foo])]
                          :update (constantly true)}}}))

(with-db {{:a 1 :b 2} {:foo 1}}
  (authorize-tx
    {:current-time "hello"
     :db db
     :tx {[:some-table {:a 1 :b 2}] {:foo :db/current-time}}
     :rules {:some-table {:spec [map? (s/keys :req-un [::foo])]
                          :update (constantly true)}}}))

(authorize-read
  {:table :some-table
   :query {:id nil}
   :doc {:crux.db/id {:a 1 :b 2}
         :foo "hey"}
   :rules {:some-table {:spec [map? (bu/only-keys :req-un [::foo])]
                        :get (constantly true)}}})

(authorize-read
  {:table :some-table
   :query {:query nil}
   :doc {:crux.db/id {:a 1 :b 2}
         :foo "hey"}
   :rules {:some-table {:spec [map? (bu/only-keys :req-un [::foo])]
                        :get (constantly true)}}})

(authorize-read
  {:table :some-table
   :query {:id nil}
   :doc {:crux.db/id {:a 1 :b 2}
         :foo "hey"}
   :rules {:some-table {:spec [map? (bu/only-keys :req-un [::foo ::bar])]
                        :get (constantly true)}}})

(authorize-read
  {:table :some-table
   :query {:id nil}
   :doc {:crux.db/id {:a 1 :b 2}
         :foo "hey"
         :bar "hello"}
   :rules {:some-table {:spec [map? (bu/only-keys :req-un [::foo])]
                        :get (constantly true)}}})


;(with-redefs [authorize-read :doc]
;  (with-db {}
;    (crux-subscribe*
;      {:db db
;       :doc {:crux.db/id {:a 1 :b 2}
;             :foo "hey"}
;       :rules {:some-table {:spec [map? (bu/only-keys :req-un [::foo])]
;                            :get (constantly true)}}}
;      {:table :some-table
;       :id nil
;       :where nil
;       :args nil})))

(with-redefs [authorize-read :doc]
  (with-db {:foo {:a 1}}
    (crux-subscribe*
      {:db db
       :uid "some-uid"}
      '{:table :some-table
        :id :foo
        :where nil
        :args nil})))

(with-redefs [authorize-read :doc]
  (with-db {:foo {:a 1}}
    (crux-subscribe*
      {:db db
       :uid "some-uid"}
      '{:table :some-table
        :id :foo})))

(with-redefs [authorize-read :doc]
  (with-db {:foo {:a 1}}
    (crux-subscribe*
      {:db db
       :uid "some-uid"}
      '{:table :some-table
        :where [[(println "you got pwned")]]})))

(u/pprint
  (with-redefs [authorize-read :doc]
    (with-db {:foo {:a 1}
              :bar {:a 1}}
      (crux-subscribe*
        {:db db
         :uid "some-uid"}
        '{:table :some-table
          :where [[:a a]
                  [(== a #{1 2 3})]]
          :args {a 1}}))))



(query-contains? {:id :foo}
  {:crux.db/id :bar :a 1})

(query-contains?
  {:id
   {:content-type :music,
    :provider :lastfm,
    :provider-id ["Breaking Benjamin" "The Diary of Jane"]}}
  {:title "The Diary of Jane",
   :author "Breaking Benjamin",
   :url
   "https://www.last.fm/music/Breaking+Benjamin/_/The+Diary+of+Jane",
   :image
   "https://lastfm.freetls.fastly.net/i/u/174s/2e5b0bc8cf774381c37c150f159e58c4.png",
   :crux.db/id
   {:content-type :music,
    :provider :lastfm,
    :provider-id ["Breaking Benjamin" "The Diary of Jane"]},
   :content-type :music,
   :provider :lastfm,
   :provider-id ["Breaking Benjamin" "The Diary of Jane"]})

(u/pprint
  (get-id->doc
    {:bypass-auth true
     :query {:id :foo}
     :id->change {:foo [nil
                        {:crux.db/id :foo
                         :a 1}]
                  :bar [{:crux.db/id :bar
                         :a 1}
                        nil]}}))
(u/pprint
  (get-id->doc
    {:bypass-auth true
     :query {:id :foo}
     :id->change {:foo [nil
                        {:crux.db/id :foo
                         :a 1}]
                  :bar [{:crux.db/id :bar
                         :a 1}
                        nil]}}))

(u/pprint
  (with-redefs [authorize-read (constantly (bu/anom :forbidden))]
    (changesets* {:id->change {:foo [nil
                                     {:crux.db/id :foo
                                      :a 1}]
                               :bar [{:crux.db/id :bar
                                      :a 1}
                                     nil]}
                  :subscriptions
                  {"bob" {{:id :foo} {:uid "bob-uid"
                                      :bypass-auth true
                                      :table :some-table}}
                   "alice" {{:where [[:a 1]]} {:uid "alice-uid"
                                               :bypass-auth true
                                               :table :some-table}
                            {:id :bar} {:uid "alice-uid"
                                        :table :some-other-table}}}})))


(prn (query-contains? '{:where [[:provider] [(uuid? doc)]]}
       {:provider-id 78490,
        :provider :thetvdb,
        :content-type :tv-show,
        :event-type :pick,
        :timestamp #inst "2020-04-18T04:47:27.096-00:00",
        :uid #uuid "676589b8-5a80-433e-9368-c6712b0b569d",
        :crux.db/id #uuid "c0a0331c-9d32-4732-aeaf-2e09507df882"}))

(prn (query-contains? '{:where [[:provider] [(map? doc)]]}
       {:provider-id 78490,
        :provider :thetvdb,
        :content-type :tv-show,
        :event-type :pick,
        :timestamp #inst "2020-04-18T04:47:27.096-00:00",
        :uid #uuid "676589b8-5a80-433e-9368-c6712b0b569d",
        :crux.db/id #uuid "c0a0331c-9d32-4732-aeaf-2e09507df882"}))


)
