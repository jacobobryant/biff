(ns com.biffweb.sqlite.impl.query
  "Internal query execution: result set building, namespaced alias support."
  (:require [camel-snake-kebab.core :as csk]
            [clojure.string :as str]
            [next.jdbc.result-set :as rs])
  (:import [java.sql ResultSet ResultSetMetaData]))

(defn preprocess-select
  "Walk a HoneySQL :select clause and encode namespaced keyword aliases
   as strings so HoneySQL quotes them correctly."
  [select]
  (if (vector? select)
    (mapv (fn [item]
            (if (and (vector? item)
                     (= 2 (count item))
                     (keyword? (second item))
                     (namespace (second item)))
              (let [kw (second item)]
                [(first item)
                 (str/replace (str (namespace kw) "/" (name kw)) "-" "_")])
              item))
          select)
    select))

(defn- sql-label->kebab [s]
  (cond-> (csk/->kebab-case-string s)
    (str/ends-with? s "_") (str "-")))

(defn- smart-column-names [^ResultSetMetaData rsmeta]
  (mapv (fn [^Integer i]
          (let [label (.getColumnLabel rsmeta i)]
            (if-let [slash-idx (str/index-of label "/")]
              (keyword (sql-label->kebab (subs label 0 slash-idx))
                       (sql-label->kebab (subs label (inc slash-idx))))
              (let [table (.getTableName rsmeta i)]
                (if (and table (not= table ""))
                  (keyword (sql-label->kebab table)
                           (sql-label->kebab label))
                  (keyword (sql-label->kebab label)))))))
        (range 1 (inc (.getColumnCount rsmeta)))))

(defn smart-kebab-maps
  "Builder function that produces kebab-case keyword maps with proper handling
   of namespaced aliases."
  [^ResultSet rs _opts]
  (let [rsmeta (.getMetaData rs)
        cols   (smart-column-names rsmeta)]
    (rs/->MapResultSetBuilder rs rsmeta cols)))

(defn make-column-reader
  "Create a column reader fn that applies read coercions."
  [read-coercions]
  (fn [builder ^ResultSet rs ^Integer i]
    (let [col-kw        (nth (:cols builder) (dec i))
          coerce-fn     (get read-coercions col-kw)
          value         (.getObject rs i)
          coerced-value (if (and coerce-fn (some? value))
                          (coerce-fn value)
                          value)]
      (rs/read-column-by-index coerced-value (:rsmeta builder) i))))
