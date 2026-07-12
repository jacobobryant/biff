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

(defn execute [{:biff.sqlite/keys [columns read-pool write-conn] :as ctx}
               input]
  (biff.core/validate {:biff.core/statement input})
  (biff.core/validate ctx {:required [:biff.sqlite/read-pool
                                      :biff.sqlite/write-conn]})
  ;; Best-effort schema validation for :set / :values in input
  (validate/validate-schema-on-write columns input)
  (let [;; Make it so we can use namespaced column aliases which is necessary
        ;; for coercing the results, since coercion is based on `columns`
        input   (if (and (map? input) (:select input))
                  (update input :select preserve-namespaced-aliases)
                  input)
        sql-vec (cond
                  (map? input) (hsql/format input)
                  (string? input) [input]
                  :else input)
        ;; Coerce the input params to sqlite (e.g. 1 instead of true)
        sql-vec (into [(first sql-vec)]
                      (coerce/coerce-params columns (rest sql-vec)))
        ;; And then supply a builder-fn that coerces the results back from
        ;; sqlite types to "rich" types (true instead of 1)
        opts    {:builder-fn (coerce/builder-fn columns)}]
    (if (write-statement? (first sql-vec))
      (let [result (do
                     (.lock write-lock)
                     (try
                       (jdbc/execute! write-conn sql-vec opts)
                       (finally
                         (.unlock write-lock))))]
        (run-on-tx! ctx)
        result)
      (jdbc/execute! read-pool sql-vec opts))))
