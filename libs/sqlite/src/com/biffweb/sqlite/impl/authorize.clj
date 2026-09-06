(ns com.biffweb.sqlite.impl.authorize
  (:require [com.biffweb.core :as biff.core]
            [com.biffweb.sqlite.impl.execute :as exec]
            [com.biffweb.sqlite.impl.coerce :as coerce]
            [com.biffweb.sqlite.impl.validate :as validate]
            [honey.sql :as hsql]
            [next.jdbc :as jdbc]))

(def ^:private table-target-schema
  [:or
   :keyword
   [:and
    [:vector {:min 1} :any]
    [:fn #(keyword? (first %))]]])

(def ^:private insert-schema
  [:and
   [:map
    [:insert-into table-target-schema]]
   [:fn #(not (contains? % :on-conflict))]])

(def ^:private upsert-schema
  [:map
   [:insert-into table-target-schema]
   [:on-conflict :any]
   [:do-update-set [:vector :keyword]]])

(def ^:private update-schema
  [:map
   [:update table-target-schema]
   [:set [:map-of :keyword :any]]])

(def ^:private delete-schema
  [:map
   [:delete-from table-target-schema]])

(def input-schema
  [:and
   :map
   [:fn #(not (contains? % :replace-into))]
   [:orn
    [:insert insert-schema]
    [:upsert upsert-schema]
    [:update update-schema]
    [:delete delete-schema]]])

(def ^:private statement-context-schema
  [:map {:closed true}
   [:statement 'any?]
   [:statement-type [:enum :insert :upsert :update :delete]]
   [:table :keyword]
   [:primary-key :keyword]])

(biff.core/register
 {::input   input-schema
  ::context statement-context-schema})

(def ^:private find-primary-key
  (memoize
   (fn [columns table-kw]
     (some (fn [[id props]]
             (when (and (= table-kw (keyword (namespace id)))
                        (:primary-key props))
               id))
           columns))))

(defn- extract-table [statement]
  (cond
    (:insert-into statement) (let [target (:insert-into statement)]
                               (if (vector? target)
                                 (first target)
                                 target))
    (:update statement)      (:update statement)
    (:delete-from statement) (:delete-from statement)))

(defn- execute-statement! [conn columns statement builder-fn]
  (let [sql-vec (hsql/format statement)]
    (jdbc/execute! conn
                   (into [(first sql-vec)]
                         (coerce/coerce-params columns (rest sql-vec)))
                   {:builder-fn builder-fn})))

(defn- process-insert! [{:keys [columns write-tx input builder-fn table]}]
  (let [returning-statement (assoc input :returning [:*])
        results             (execute-statement! write-tx columns
                                                returning-statement builder-fn)]
    (mapv (fn [row]
            {:table  table
             :op     :create
             :before nil
             :after  (into {} row)})
          results)))

(defn- process-delete! [{:keys [columns write-tx input builder-fn table]}]
  (let [returning-statement (assoc input :returning [:*])
        results             (execute-statement! write-tx columns
                                                returning-statement builder-fn)]
    (mapv (fn [row]
            {:table  table
             :op     :delete
             :before (into {} row)
             :after  nil})
          results)))

(defn- process-update!
  [{:keys [columns read-tx write-tx input builder-fn table primary-key]}]
  (let [returning-statement (assoc input :returning [:*])
        after-rows          (execute-statement! write-tx
                                                columns
                                                returning-statement
                                                builder-fn)
        after-by-pk         (into {} (map (juxt primary-key #(into {} %)))
                                  after-rows)
        pks                 (vec (keys after-by-pk))
        before-rows         (when (seq pks)
                              (let [select-statement
                                    {:select [:*]
                                     :from   table
                                     :where  [:in primary-key pks]}]
                                (execute-statement! read-tx
                                                    columns
                                                    select-statement
                                                    builder-fn)))
        before-by-pk        (into {}
                                  (map (juxt primary-key #(into {} %)))
                                  before-rows)
        all-pks             (distinct (concat (keys before-by-pk)
                                              (keys after-by-pk)))]
    (into []
          (mapcat
           (fn [pk]
             (let [before (get before-by-pk pk)
                   after  (get after-by-pk pk)]
               (cond
                 (and before after)
                 [{:table table :op :update :before before :after after}]

                 (and before (not after))
                 [{:table table :op :delete :before before :after nil}]

                 (and after (not before))
                 [{:table table :op :create :before nil :after after}]))))
          all-pks)))

(defn- classify-statement [statement]
  (cond
    (and (:insert-into statement) (:on-conflict statement)) :upsert
    (:insert-into statement) :insert
    (:update statement)      :update
    (:delete-from statement) :delete))

(defn- statement-context [columns statement]
  (let [table-kw (extract-table statement)]
    {:statement      statement
     :statement-type (classify-statement statement)
     :table          table-kw
     :primary-key    (find-primary-key columns table-kw)}))

(defn- validate-primary-key-unchanged!
  [{:keys [statement statement-type primary-key]}]
  (when (case statement-type
          :update (contains? (:set statement) primary-key)
          :upsert (some #{primary-key} (:do-update-set statement))
          false)
    (throw
     (ex-info "authorized-write does not allow changing primary key columns."
              {:primary-key primary-key}))))

(defn- validate-input [columns {:keys [statement] :as context}]
  ;; Do two seperate biff.core/validate calls so we report errors on `input`
  ;; before checking `context`
  (biff.core/validate {:biff.sqlite/authorized-write-statement statement})
  (biff.core/validate {::context context})
  (validate-primary-key-unchanged! context)
  (validate/validate-schema-on-write columns statement))

(defn- diff-key [columns {:keys [table before after]}]
  (let [primary-key (find-primary-key columns table)]
    [table (get (or before after) primary-key)]))

(defn- merged-op [before after]
  (cond
    (= before after) nil
    (and before after) :update
    before :delete
    after :create))

(defn- merge-diff-entry [columns state entry]
  (let [k        (diff-key columns entry)
        existing (get-in state [:table-pk->diff k])
        before   (if (contains? existing :before)
                   (:before existing)
                   (:before entry))
        after    (:after entry)
        merged   (when-let [op (merged-op before after)]
                   {:table  (:table entry)
                    :op     op
                    :before before
                    :after  after})
        state    (if existing
                   state
                   (update state :table-pks conj k))]
    (if merged
      (assoc-in state [:table-pk->diff k] merged)
      (update state :table-pk->diff dissoc k))))

(defn- merge-diffs [columns diffs]
  (let [{:keys [table-pk->diff table-pks]}
        (reduce (fn [state entry]
                  (merge-diff-entry columns state entry))
                {:table-pk->diff {} :table-pks []}
                (apply concat diffs))]
    (into []
          (keep table-pk->diff)
          (distinct table-pks))))

(defn- run-on-tx! [ctx]
  (when-let [on-tx (:biff.core/on-tx ctx)]
    (on-tx ctx)))

(defn authorized-write*
  [{:biff.sqlite/keys [columns write-conn read-pool authorize] :as ctx}
   statements]
  (biff.core/validate ctx {:required [:biff.sqlite/authorize
                                      :biff.sqlite/write-conn
                                      :biff.sqlite/read-pool]})
  (let [builder-fn (coerce/builder-fn columns)
        contexts   (mapv #(statement-context columns %) statements)
        _          (doseq [context contexts]
                     (validate-input columns context))
        _          (.lock exec/write-lock)

        result
        (try
          (jdbc/with-transaction [read-tx read-pool]
            (jdbc/execute! read-tx ["SELECT 1"])
            (jdbc/with-transaction [write-tx write-conn]
              (let [diff     (->> contexts
                                  (mapv (fn [{:keys [statement
                                                     statement-type
                                                     table
                                                     primary-key]}]
                                          ((case statement-type
                                             :insert process-insert!
                                             :delete process-delete!
                                             (:update :upsert) process-update!)
                                           {:columns     columns
                                            :read-tx     read-tx
                                            :write-tx    write-tx
                                            :input       statement
                                            :builder-fn  builder-fn
                                            :table       table
                                            :primary-key primary-key})))
                                  (merge-diffs columns))
                    auth-ctx (assoc ctx
                                    :biff.sqlite/before-conn read-tx
                                    :biff.sqlite/after-conn write-tx)]
                (when-not (authorize auth-ctx diff)
                  (throw (ex-info "Write rejected by authorization rules."
                                  {:biff.sqlite/diff diff})))
                diff)))
          (finally
            (.unlock exec/write-lock)))]
    (run-on-tx! ctx)
    result))

;; Generate a diff (a data structure showing the "before" and "after" states
;; of each record affected by the transaction, then pass it to the user's
;; authorize function. To generate the diff:
;;
;; - For insert and delete statements, we simply add a `RETURNING *` to the
;; statement and then use the results as the before / after values.
;;
;; - for update/"upsert" (insert ... on conflict) statements, we also add
;; `RETURNING *` to get the "after" values, and then we get the primary keys
;; from those and use them in a `SELECT *` statement that we run on a read
;; transaction that was established before the update statement ran (i.e. so the
;; read transaction gives us a snapshot of the DB before changes took place).
;;
;; To make that diff-generation work, we have to put some restrictions on the
;; kinds of statements we can accept; for example, update statements are not
;; allowed to modify primary key columns.
(defn authorized-write [ctx statement]
  (biff.core/validate {:biff.sqlite/authorized-write-statement statement})
  (authorized-write* ctx [statement]))

(defn authorized-write-tx [ctx statements]
  (biff.core/validate {:biff.sqlite/authorized-write-statements statements})
  (authorized-write* ctx statements))
