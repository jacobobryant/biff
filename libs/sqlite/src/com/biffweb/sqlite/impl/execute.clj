(ns com.biffweb.sqlite.impl.execute
  "Core execute implementation, extracted so it can be used by other impl
   namespaces (e.g. auth) without creating a cyclic dependency on
   com.biffweb.sqlite."
  (:require [com.biffweb.sqlite.impl.coerce :as coerce]
            [com.biffweb.sqlite.impl.query :as query]
            [com.biffweb.sqlite.impl.util :as util]
            [com.biffweb.sqlite.impl.validate :as validate]
            [honey.sql :as hsql]
            [next.jdbc :as jdbc]))

(def write-lock (Object.))

(defn- run-on-tx! [ctx]
  (when-let [on-tx (:biff.core/on-tx ctx)]
    (on-tx ctx)))

(defn execute
  [ctx input]
  (let [{:biff.sqlite/keys [columns read-pool write-conn]}
        ctx

        columns (or columns {})

        {:keys [builder-fn enum-val->int normalized-columns]}
        (coerce/memoized-coercions columns)

        _       (validate/validate-honeysql-input! normalized-columns input)
        input   (if (and (map? input) (:select input))
                  (update input :select query/preprocess-select)
                  input)
        sql-vec (cond
                  (map? input) (hsql/format input)
                  (string? input) [input]
                  :else input)
        sql-vec (into [(first sql-vec)] (coerce/coerce-params enum-val->int (rest sql-vec)))
        opts    {:builder-fn builder-fn}]
    (if (util/write-statement? (first sql-vec))
      (locking write-lock
        (let [result (jdbc/execute! write-conn sql-vec opts)]
          (run-on-tx! ctx)
          result))
      (jdbc/execute! read-pool sql-vec opts))))
