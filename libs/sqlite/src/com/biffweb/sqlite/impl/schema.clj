(ns com.biffweb.sqlite.impl.schema
  (:require [clojure.string :as str]))

(defn- sql-name [s]
  (str/replace (name s) "-" "_"))

(defn- table-for [column]
  (keyword (namespace column)))

(defn- sqlite-type [col-type]
  (case col-type
    :uuid    "BLOB"
    :text    "TEXT"
    :int     "INT"
    :real    "REAL"
    :boolean "INT"
    :inst    "INT"
    :enum    "INT"
    :edn     "BLOB"
    :blob    "BLOB"))

(defn- index-sql [col]
  (when (:index col)
    (let [table-name (sql-name (:table col))
          col-name   (sql-name (:id col))]
      (str "CREATE INDEX idx_" table-name "_" col-name
           " ON " table-name "(" col-name ");"))))

(defn- constraint-sql-defs [col]
  (concat (when-some [ref* (:ref col)]
            [{:line (str "FOREIGN KEY(" (sql-name (:id col))
                         ") REFERENCES " (sql-name (namespace ref*))
                         "(" (sql-name ref*) ")")}])
          (when (:unique col)
            [{:line (str "UNIQUE(" (sql-name (:id col)) ")")}])
          (when-some [others (:unique-with col)]
            (let [all-cols (into [(:id col)] others)]
              [{:line (str "UNIQUE("
                           (->> all-cols
                                (mapv sql-name)
                                (str/join ", "))
                           ")")}]))))

(defn- column-sql-def [column]
  (let [col-name (sql-name (:id column))
        col-type (sqlite-type (:type column))
        enum-map (:enum-values column)
        check    (when enum-map
                   (str " CHECK (" col-name " IN ("
                        (str/join ", " (sort (keys enum-map)))
                        "))"))
        comment*  (when enum-map
                    (->> enum-map
                         (sort-by key)
                         (map (fn [[k v]] (str (name v) " (" k ")")))
                         (str/join ", ")
                         (str " -- ")))
        required (or (:primary-key column) (:required column))]
    {:line    (str col-name " " col-type
                   (when (:primary-key column) " PRIMARY KEY")
                   (when required " NOT NULL")
                   check)
     :comment comment*}))

(defn- table-sql [{:keys [table columns]}]
  (let [table-name  (sql-name table)
        col-defs    (mapv column-sql-def columns)
        constraints (->> (mapcat constraint-sql-defs columns)
                         (sort-by :line))
        lines       (concat col-defs constraints)
        formatted   (map-indexed
                     (fn [i {:keys [line] comment* :comment}]
                       (str "  "
                            line
                            (when (not= (inc i) (count lines)) ",")
                            comment*))
                     lines)]
    (str "CREATE TABLE " table-name " (\n"
         (str/join "\n" formatted)
         "\n) STRICT;")))

(defn- topo-sort [deps]
  (loop [sorted    []
         remaining deps]
    (if (empty? remaining)
      sorted
      (let [ready (into #{}
                        (comp (filter #(empty? (val %)))
                              (map key))
                        remaining)]
        (if (empty? ready)
          (into sorted (keys remaining))
          (recur (into sorted (sort ready))
                 (into {}
                       (comp (remove #(contains? ready (key %)))
                             (map (fn [[k v]]
                                    [k (apply disj v ready)])))
                       remaining)))))))

(defn- topo-sort-tables [tables]
  (let [all-tables (into #{} (map :table) tables)
        table-deps (into {}
                         (map (fn [{:keys [table columns]}]
                                [table
                                 (into #{}
                                       (comp (keep :ref)
                                             (map table-for)
                                             (filter all-tables)
                                             (remove #{table}))
                                       columns)]))
                         tables)
        sorted-table-keys (topo-sort table-deps)
        tables-by-key (into {}
                            (map (juxt :table identity))
                            tables)]
    (mapv tables-by-key sorted-table-keys)))

(defn- sort-columns [table-cols]
  (sort-by (fn [col]
             [(cond
                (:primary-key col) 0
                (:required col)    1
                :else              2)
              (name (:id col))])
           table-cols))

(defn schema-sql
  [{:biff.sqlite/keys [columns]}]
  (biff.core/validate {:biff.sqlite/columns columns})
  (let [tables (->> columns
                    (mapv (fn [[id opts]]
                            (assoc opts :id id :table (table-for id))))
                    (group-by :table)
                    (mapv (fn [[table columns]]
                            {:table table
                             :columns (vec (sort-columns columns))}))
                    topo-sort-tables)]
    (str/join "\n\n"
              (concat (mapv table-sql tables)
                      (->> (mapcat :columns tables)
                           (keep index-sql))))))
