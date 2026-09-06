(ns com.biffweb.xtdb.impl.util
  (:require [clojure.string :as str]
            [clojure.walk :as walk]
            [honey.sql :as hsql]
            [xtdb.util :as xt.util]))

(defn format-query
  ([query]
   (format-query query nil))
  ([query opts]
   (if (map? query)
     (hsql/format
      (walk/postwalk (fn [x]
                       (cond-> x
                         (qualified-keyword? x) xt.util/kw->normal-form-kw))
                     query)
      opts)
     query)))

(defn table-key [x]
  (if (map? x)
    (or (:into x) (:from x))
    x))

(defn table-id-key [table]
  (keyword (name table) "id"))

(defn table-from-column [k]
  (keyword (namespace k)))

(defn table-name [table]
  (name table))

(defn sql-ident [k]
  (let [k (if (qualified-keyword? k)
            (xt.util/kw->normal-form-kw k)
            k)]
    (if (= k :_id)
      "_id"
      (str "\"" (name k) "\""))))

(defn sql-table [table]
  (str "\"" (name table) "\""))

(defn where-and [kvs]
  (str/join
   " AND "
   (map (fn [[k _]]
          (str (sql-ident k) " = ?"))
        kvs)))

(defn sql-args [kvs]
  (mapv val kvs))

(defn assoc-table-key [table row]
  (if (contains? row :xt/id)
    (assoc row (table-id-key table) (:xt/id row))
    row))

(defn doc-id [doc]
  (or (:xt/id doc) (get doc "_id")))
