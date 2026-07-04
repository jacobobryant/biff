(ns com.biffweb.sqlite.impl.execute
  (:require [clojure.string :as str]
            [com.biffweb.core :as biff.core]
            [com.biffweb.sqlite.impl.coerce :as coerce]
            [com.biffweb.sqlite.impl.validate :as validate]
            [honey.sql :as hsql]
            [next.jdbc :as jdbc])
  (:import [java.util.concurrent.locks ReentrantLock]))

(def write-lock (ReentrantLock.))

(defn- run-on-tx! [ctx]
  (when-let [on-tx (:biff.core/on-tx ctx)]
    (on-tx ctx)))

;; Turn qualified keyword select aliases into strings so honeysql doesn't turn
;; them into dotted things (:foo/bar -> "foo/bar" instead of "foo.bar" --
;; "foo.bar" is invalid as a sqlite column alias). See also coerce/builder-fn
;; which does corresponding post-processing of the query results.
(defn- preserve-namespaced-aliases [select]
  (if (vector? select)
    (mapv (fn [item]
            (if (and (vector? item)
                     (= 2 (count item))
                     (qualified-keyword? (second item)))
              (let [kw (second item)]
                [(first item)
                 (str (namespace kw) "/" (name kw))])
              item))
          select)
    select))

(defn- write-statement? [sql-str]
  (let [trimmed (str/triml sql-str)]
    (boolean (re-find #"(?i)^(INSERT|UPDATE|DELETE|CREATE|DROP|ALTER)\b"
                      trimmed))))

(defn- sql-vec [columns statement]
  ;; Best-effort schema validation for :set / :values in statement
  (validate/validate-schema-on-write columns statement)
  (let [;; Make it so we can use namespaced column aliases which is necessary
        ;; for coercing the results, since coercion is based on `columns`
        statement (if (and (map? statement) (:select statement))
                    (update statement :select preserve-namespaced-aliases)
                    statement)
        sql-vec   (cond
                    (map? statement) (hsql/format statement)
                    (string? statement) [statement]
                    :else statement)]
    ;; Coerce the statement params to sqlite (e.g. 1 instead of true)
    (into [(first sql-vec)]
          (coerce/coerce-params columns (rest sql-vec)))))

(defn- execute* [{:biff.sqlite/keys [columns read-pool write-conn] :as ctx}
                 statements
                 execute-fn]
  (biff.core/validate ctx {:required [:biff.sqlite/read-pool
                                      :biff.sqlite/write-conn]})
  (let [sql-statements (mapv #(sql-vec columns %) statements)
        opts           {:builder-fn (coerce/builder-fn columns)}]
    (if (some (comp write-statement? first) sql-statements)
      (let [_      (.lock write-lock)
            result (try
                     (execute-fn write-conn sql-statements opts)
                     (finally
                       (.unlock write-lock)))]
        (run-on-tx! ctx)
        result)
      (execute-fn read-pool sql-statements opts))))

(defn execute-tx [ctx statements]
  (biff.core/validate {:biff.sqlite/statements statements})
  (execute* ctx
            statements
            (fn [conn sql-statements opts]
              (jdbc/with-transaction [tx conn]
                (mapv #(jdbc/execute! tx % opts)
                      sql-statements)))))

(defn execute [ctx statement]
  (biff.core/validate {:biff.sqlite/statement statement})
  (execute* ctx
            [statement]
            (fn [conn sql-statements opts]
              (jdbc/execute! conn (first sql-statements) opts))))
