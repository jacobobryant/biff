(ns com.biffweb.sqlite.impl.schema
  "Internal DDL generation from column definitions."
  (:require [clojure.java.io :as io]
            [clojure.java.process :as process]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [com.biffweb.sqlite.impl.util :as util])
  (:import [java.sql DriverManager]))

(defn- generate-column-def [col]
  (let [col-name (util/col-col-name (:id col))
        col-type (util/sqlite-type (:type col))
        enum-map (:enum-values col)
        check    (when enum-map
                   (str " CHECK (" col-name " IN ("
                        (str/join ", " (sort (keys enum-map)))
                        "))"))
        comment  (when enum-map
                   (str " -- " (str/join ", " (map (fn [[k v]] (str (name v) " (" k ")"))
                                                   (sort-by key enum-map)))))]
    {:line    (str col-name " " col-type
                   (when (:primary-key col) " PRIMARY KEY")
                   (when (:required col) " NOT NULL")
                   check)
     :comment comment}))

(defn- generate-table-constraints [table-cols]
  (let [fk        (keep (fn [col]
                          (when-let [ref (:ref col)]
                            {:line (str "FOREIGN KEY(" (util/col-col-name (:id col))
                                        ") REFERENCES " (util/col-table-name ref)
                                        "(" (util/col-col-name ref) ")")}))
                        table-cols)
        uniq      (keep (fn [col]
                          (when (:unique col)
                            {:line (str "UNIQUE(" (util/col-col-name (:id col)) ")")}))
                        table-cols)
        uniq-with (keep (fn [col]
                          (when-let [others (:unique-with col)]
                            (let [all-cols (into [(:id col)] others)]
                              {:line (str "UNIQUE(" (str/join ", " (mapv util/col-col-name all-cols)) ")")})))
                        table-cols)]
    (concat fk uniq uniq-with)))

(defn- generate-indexes [table-cols]
  (keep (fn [col]
          (when (:index col)
            (let [tbl      (util/col-table-name (:id col))
                  col-name (util/col-col-name (:id col))]
              (str "CREATE INDEX idx_" tbl "_" col-name
                   " ON " tbl "(" col-name ");"))))
        table-cols))

(defn- sort-columns
  "Sort columns: primary key first, then required (alphabetically), then optional (alphabetically)."
  [table-cols]
  (sort-by (fn [col]
             [(cond
                (:primary-key col) 0
                (:required col)    1
                :else              2)
              (util/col-col-name (:id col))])
           table-cols))

(defn- generate-create-table [table-name table-cols]
  (let [table-cols  (sort-columns table-cols)
        col-defs    (mapv generate-column-def table-cols)
        constraints (generate-table-constraints table-cols)
        lines       (vec (concat col-defs constraints))
        formatted   (map-indexed
                     (fn [i {:keys [line] comment* :comment}]
                       (str "  " line
                            (when (not= (inc i) (count lines)) ",")
                            comment*))
                     lines)]
    (str "CREATE TABLE " table-name " (\n"
         (str/join "\n" formatted)
         "\n) STRICT;")))

(defn- topo-sort-tables [grouped-cols]
  (let [tables (set (keys grouped-cols))
        deps   (into {}
                     (map (fn [[table-key table-cols]]
                            [table-key
                             (->> table-cols
                                  (keep :ref)
                                  (map util/col-table)
                                  (filter tables)
                                  (remove #{table-key})
                                  set)]))
                     grouped-cols)]
    (loop [sorted    []
           remaining deps]
      (if (empty? remaining)
        sorted
        (let [ready (into [] (comp (filter #(empty? (val %)))
                                   (map key))
                          remaining)]
          (if (empty? ready)
            (into sorted (keys remaining))
            (recur (into sorted (sort ready))
                   (into {}
                         (comp (remove #(contains? (set ready) (key %)))
                               (map (fn [[k v]]
                                      [k (apply disj v ready)])))
                         remaining))))))))

(defn- generate-ddl
  "Generate CREATE TABLE and CREATE INDEX DDL from column definitions (internal vector format)."
  [columns]
  (let [grouped       (group-by (comp util/col-table :id) columns)
        table-order   (topo-sort-tables grouped)
        create-tables (for [table-key table-order
                            :let      [table-cols (get grouped table-key)]
                            :when     table-cols]
                        (generate-create-table (util/sql-name (name table-key)) table-cols))
        all-indexes   (mapcat #(generate-indexes (get grouped %)) table-order)]
    (str/join "\n\n"
              (concat create-tables all-indexes))))

(defn generate-schema-sql
  "Generate the complete schema SQL string from normalized column definitions and extra SQL.
   Returns the full string including header comment, DDL, and any extra SQL statements."
  [columns extra-init-sql]
  (let [ddl (generate-ddl columns)]
    (str "-- Auto-generated; do not edit.\n\n"
         ddl
         (when (seq extra-init-sql)
           (str "\n\n" (str/join "\n" extra-init-sql))))))

(defn- table-columns [conn table-name]
  (with-open [stmt (.createStatement conn)
              rs   (.executeQuery stmt (str "PRAGMA table_info(" table-name ")"))]
    (loop [columns []]
      (if (.next rs)
        (recur (conj columns (.getString rs "name")))
        columns))))

(defn- migrate-legacy-kv-table! [db-path]
  (when (.exists (io/file db-path))
    (with-open [conn (DriverManager/getConnection (str "jdbc:sqlite:" db-path))]
      (let [columns (set (table-columns conn "biff_sqlite_kv"))]
        (when (= columns #{"namespace" "key_" "value_"})
          (log/info "Migrating biff_sqlite_kv to add an id primary key.")
          (.setAutoCommit conn false)
          (try
            (doseq [sql ["ALTER TABLE biff_sqlite_kv RENAME TO biff_sqlite_kv_old"
                         "CREATE TABLE biff_sqlite_kv (id BLOB PRIMARY KEY NOT NULL, key_ TEXT NOT NULL, namespace TEXT NOT NULL, value_ BLOB NOT NULL, UNIQUE(namespace, key_)) STRICT"
                         "INSERT INTO biff_sqlite_kv (id, key_, namespace, value_) SELECT randomblob(16), key_, namespace, value_ FROM biff_sqlite_kv_old"
                         "DROP TABLE biff_sqlite_kv_old"]]
              (with-open [stmt (.createStatement conn)]
                (.executeUpdate stmt sql)))
            (.commit conn)
            (catch Throwable t
              (.rollback conn)
              (throw t))))))))

(defn apply-schema!
  "Generate schema SQL from column definitions, write to schema-path,
   and run sqlite3def to apply migrations."
  [db-path schema-path columns extra-init-sql sqlite3def-path]
  (io/make-parents db-path)
  (io/make-parents schema-path)
  (migrate-legacy-kv-table! db-path)
  (let [full-sql (generate-schema-sql columns extra-init-sql)
        _        (spit schema-path full-sql)
        result   (process/exec sqlite3def-path db-path "--apply" "-f" schema-path)]
    (when (not-empty result)
      (log/info result))))
