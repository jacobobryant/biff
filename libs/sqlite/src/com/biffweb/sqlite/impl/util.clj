(ns com.biffweb.sqlite.impl.util
  "Internal helpers for SQL name conversion and type mapping."
  (:require [clojure.string :as str]))

(defn col-table
  "Get the table keyword from a column :id (its namespace as a keyword)."
  [col-id]
  (keyword (namespace col-id)))

(defn normalize-columns
  "Convert public map format {kw {:type ...}} to internal vector-of-maps format.
   Also ensures :primary-key implies :required."
  [columns]
  (mapv (fn [[id props]]
          (cond-> (assoc props :id id)
            (:primary-key props) (assoc :required true)))
        columns))

(defn write-statement?
  "Returns true if the SQL string is a write statement."
  [sql-str]
  (let [trimmed (str/triml sql-str)]
    (boolean (re-find #"(?i)^(INSERT|UPDATE|DELETE|CREATE|DROP|ALTER)\b" trimmed))))
