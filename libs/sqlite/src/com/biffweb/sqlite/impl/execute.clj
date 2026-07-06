(ns com.biffweb.sqlite.impl.execute
  (:require [clojure.string :as str]
            [com.biffweb.sqlite.impl.coerce :as coerce]
            [com.biffweb.sqlite.impl.util :as util]
            [com.biffweb.sqlite.impl.validate :as validate]
            [honey.sql :as hsql]
            [next.jdbc :as jdbc]))

(def write-lock (Object.))

(defn- run-on-tx! [ctx]
  (when-let [on-tx (:biff.core/on-tx ctx)]
    (on-tx ctx)))

;; Turn qualified keyword select aliases into strings so honeysql doesn't turn
;; them into dotted things (:foo/bar -> "foo/bar" instead of "foo.bar"). Then
;; next.jdbc turns the results into keywords (:foo/bar).
(defn- preserve-namespaced-aliases [select]
  (if (vector? select)
    (mapv (fn [item]
            (if (and (vector? item)
                     (= 2 (count item))
                     (qualified-keyword? (second item)))
              (let [kw (second item)]
                [(first item)
                 ;; TODO do we actually need to convert to underscore here?
                 (str/replace (str (namespace kw) "/" (name kw)) "-" "_")])
              item))
          select)
    select))

(defn- write-statement? [sql-str]
  (let [trimmed (str/triml sql-str)]
    (boolean (re-find #"(?i)^(INSERT|UPDATE|DELETE|CREATE|DROP|ALTER)\b" trimmed))))

(defn execute [{:biff.sqlite/keys [columns read-pool write-conn] :as ctx}
               input]
  (validate/validate-write columns input)
  (let [input   (if (and (map? input) (:select input))
                  (update input :select preserve-namespaced-aliases)
                  input)
        sql-vec (cond
                  (map? input) (hsql/format input)
                  (string? input) [input]
                  :else input)
        sql-vec (into [(first sql-vec)] (coerce/coerce-params columns (rest sql-vec)))
        opts    {:builder-fn (coerce/builder-fn columns)}]
    (if (util/write-statement? (first sql-vec))
      (let [result (locking write-lock
                     (jdbc/execute! write-conn sql-vec opts))]
        (run-on-tx! ctx)
        result)
      (jdbc/execute! read-pool sql-vec opts))))
