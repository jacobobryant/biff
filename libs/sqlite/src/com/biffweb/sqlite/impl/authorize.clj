(ns com.biffweb.sqlite.impl.authorize
  "Internal implementation for authorized write transactions.
   Generates diff data structures and manages transaction rollback."
  (:require [com.biffweb.sqlite.impl.execute :as exec]
            [com.biffweb.sqlite.impl.coerce :as coerce]
            [com.biffweb.sqlite.impl.validate :as validate]
            [honey.sql :as hsql]
            [next.jdbc :as jdbc]))

(def ^:private find-primary-key
  (memoize
   (fn [columns table-kw]
     (some (fn [[id props]]
             (when (and (= table-kw (keyword (namespace id)))
                        (:primary-key props))
               id))
           columns))))

(defn- extract-table
  "Extract the table keyword from a HoneySQL statement map."
  [stmt]
  (cond
    (:insert-into stmt) (let [target (:insert-into stmt)]
                          (if (keyword? target)
                            target
                            (if (vector? target)
                              (first target)
                              target)))
    (:update stmt)      (:update stmt)
    (:delete-from stmt) (:delete-from stmt)))

(defn- format-and-coerce
  "Format a HoneySQL map to a SQL vector and apply write coercions."
  [columns stmt]
  (let [sql-vec (hsql/format stmt)]
    (into [(first sql-vec)] (coerce/coerce-params columns (rest sql-vec)))))

(defn- execute-sql!
  "Execute a SQL vector on a connection with the given builder-fn."
  [conn sql-vec builder-fn]
  (jdbc/execute! conn sql-vec {:builder-fn builder-fn}))

(defn- process-insert!
  "Process a plain INSERT statement (no :on-conflict): add :returning :*, execute, return diff entries."
  [columns conn stmt builder-fn]
  (let [table-kw       (extract-table stmt)
        returning-stmt (assoc stmt :returning [:*])
        sql-vec        (format-and-coerce columns returning-stmt)
        results        (execute-sql! conn sql-vec builder-fn)]
    (mapv (fn [row]
            {:table  table-kw
             :op     :create
             :before nil
             :after  (into {} row)})
          results)))

(defn- process-delete!
  "Process a DELETE statement: add :returning :*, execute, return diff entries."
  [columns conn stmt builder-fn]
  (let [table-kw       (extract-table stmt)
        returning-stmt (assoc stmt :returning [:*])
        sql-vec        (format-and-coerce columns returning-stmt)
        results        (execute-sql! conn sql-vec builder-fn)]
    (mapv (fn [row]
            {:table  table-kw
             :op     :delete
             :before (into {} row)
             :after  nil})
          results)))

(defn- process-update!
  "Process an UPDATE or INSERT...ON CONFLICT statement:
   1. Execute the write statement with :returning :* on write-tx to get after-values
   2. Extract primary keys from the results
   3. Query read-tx for the original records using those primary keys
   4. Pair before/after by primary key to generate diff entries"
  [columns read-tx write-tx stmt builder-fn]
  (let [table-kw (extract-table stmt)
        pk-key   (find-primary-key columns table-kw)]
    (when-not pk-key
      (throw (ex-info "authorized-write requires a primary key for UPDATE/upsert statements."
                      {:table table-kw})))
    (let [;; Execute the write with :returning :* on the write transaction
          returning-stmt (assoc stmt :returning [:*])
          write-sql      (format-and-coerce columns returning-stmt)
          after-rows     (execute-sql! write-tx write-sql builder-fn)
          after-by-pk    (into {} (map (juxt pk-key #(into {} %))) after-rows)
          ;; Query the read transaction for before-values using the PKs from the write result
          pks            (vec (keys after-by-pk))
          before-rows    (when (seq pks)
                           (let [select-stmt {:select [:*]
                                              :from   table-kw
                                              :where  [:in pk-key pks]}
                                 select-sql  (format-and-coerce columns select-stmt)]
                             (execute-sql! read-tx select-sql builder-fn)))
          before-by-pk   (into {} (map (juxt pk-key #(into {} %))) before-rows)
          all-pks        (distinct (concat (keys before-by-pk) (keys after-by-pk)))]
      (into []
            (mapcat
             (fn [pk]
               (let [before (get before-by-pk pk)
                     after  (get after-by-pk pk)]
                 (cond
                   (and before after)
                   [{:table table-kw :op :update :before before :after after}]

                   (and before (not after))
                   [{:table table-kw :op :delete :before before :after nil}]

                   (and after (not before))
                   [{:table table-kw :op :create :before nil :after after}]))))
            all-pks))))

(defn- classify-statement
  "Classify a HoneySQL statement as :insert, :upsert, :update, or :delete.
   Throws if the statement is not a write statement, or if it uses REPLACE."
  [stmt]
  (cond
    (not (map? stmt))
    (throw (ex-info "authorized-write only accepts HoneySQL maps."
                    {:input stmt}))

    (:replace-into stmt)
    (throw (ex-info "authorized-write does not support REPLACE INTO statements. Use INSERT ... ON CONFLICT instead."
                    {:statement stmt}))

    (and (:insert-into stmt) (:on-conflict stmt)) :upsert
    (:insert-into stmt) :insert
    (:update stmt)      :update
    (:delete-from stmt) :delete

    :else
    (throw (ex-info "authorized-write only accepts INSERT, UPDATE, or DELETE statements."
                    {:statement stmt}))))

(defn- validate-no-pk-changes!
  "Validate that the statement does not attempt to change the primary key column.
   For UPDATE: asserts that :set is a map with keyword keys and does not contain the primary key.
   For UPSERT: asserts that :do-update-set is a vector of keywords and does not contain the primary key."
  [columns stmt stmt-type]
  (let [table-kw (extract-table stmt)
        pk-key   (find-primary-key columns table-kw)]
    (when pk-key
      (case stmt-type
        :update
        (let [set-val (:set stmt)]
          (when-not (map? set-val)
            (throw (ex-info "authorized-write UPDATE requires :set to be a map."
                            {:set set-val})))
          (when-not (every? keyword? (keys set-val))
            (throw (ex-info "authorized-write UPDATE requires all :set keys to be keywords."
                            {:set-keys (keys set-val)})))
          (when (contains? set-val pk-key)
            (throw (ex-info (str "authorized-write does not allow changing primary key columns. "
                                 "Found primary key " pk-key " in :set.")
                            {:primary-key pk-key :set-keys (keys set-val)}))))

        :upsert
        (let [update-set (:do-update-set stmt)]
          (when-not (vector? update-set)
            (throw (ex-info "authorized-write UPSERT requires :do-update-set to be a vector."
                            {:do-update-set update-set})))
          (when-not (every? keyword? update-set)
            (throw (ex-info "authorized-write UPSERT requires all :do-update-set entries to be keywords."
                            {:do-update-set update-set})))
          (when (some #{pk-key} update-set)
            (throw (ex-info (str "authorized-write does not allow changing primary key columns. "
                                 "Found primary key " pk-key " in :do-update-set.")
                            {:primary-key pk-key :do-update-set update-set}))))

        nil))))

(defn authorized-write!
  "Execute a write statement within a transaction, generating a diff and checking
   authorization. Returns the diff if authorized.

   Opens a read transaction (before-conn) and a write transaction (after-conn).
   Both are added to ctx before calling authorize-fn, so it can query the
   database state before and after the write.

   Primary key changes are not allowed in UPDATE or UPSERT statements.
   REPLACE INTO statements are rejected.

   Parameters:
   - ctx: the system context map (must contain :biff.sqlite/write-conn, :biff.sqlite/read-pool,
          :biff.sqlite/columns, and :biff.sqlite/authorize)
   - input: a HoneySQL map (INSERT, UPDATE, DELETE, or INSERT...ON CONFLICT)"
  [{:biff.sqlite/keys [columns write-conn read-pool authorize] :as ctx} input]
  (let [columns    (or columns {})
        builder-fn (coerce/builder-fn columns)
        stmt-type  (classify-statement input)]
    (validate-no-pk-changes! columns input stmt-type)
    (validate/validate-write columns input)
    (jdbc/with-transaction [read-tx read-pool]
      ;; Establish the read snapshot before opening the write transaction
      (jdbc/execute! read-tx ["SELECT 1"])
      (jdbc/with-transaction [write-tx write-conn {:isolation :serializable}]
        (let [diff     (case stmt-type
                         :insert (process-insert! columns write-tx input builder-fn)
                         :delete (process-delete! columns write-tx input builder-fn)
                         (:update :upsert) (process-update! columns read-tx write-tx input builder-fn))
              auth-ctx (assoc ctx
                              :biff.sqlite/before-conn read-tx
                              :biff.sqlite/after-conn write-tx)]
          (when-not (authorize auth-ctx diff)
            (throw (ex-info "Write rejected by authorization rules."
                            {:diff diff})))
          diff)))))

(defn- run-on-tx! [ctx result]
  (when-let [on-tx (:biff.core/on-tx ctx)]
    (on-tx ctx))
  result)

(defn authorized-write [ctx input]
  (when-not (:biff.sqlite/authorize ctx)
    (throw (ex-info "authorized-write requires :biff.sqlite/authorize in ctx."
                    {})))
  (locking exec/write-lock
    (run-on-tx! ctx (authorized-write! ctx input))))
