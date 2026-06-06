(ns com.biffweb.sqlite.impl.pool
  "Internal connection pool and write connection management."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [next.jdbc :as jdbc])
  (:import [com.zaxxer.hikari HikariConfig HikariDataSource]))

(def ^:private pragmas
  ["PRAGMA journal_mode=WAL"
   "PRAGMA busy_timeout = 5000"
   "PRAGMA foreign_keys = ON"
   "PRAGMA synchronous = NORMAL"])

(defn start-read-pool
  "Start a HikariCP read connection pool for SQLite at db-path."
  [db-path]
  (io/make-parents db-path)
  (HikariDataSource.
   (doto (HikariConfig.)
     (.setJdbcUrl (str "jdbc:sqlite:" db-path))
     (.setConnectionInitSql (str/join ";" pragmas)))))

(defn start-write-conn
  "Open a single long-lived JDBC connection for writes, initialized with pragmas."
  [db-path]
  (io/make-parents db-path)
  (let [ds   (jdbc/get-datasource {:jdbcUrl (str "jdbc:sqlite:" db-path)})
        conn (jdbc/get-connection ds)]
    (doseq [pragma pragmas]
      (jdbc/execute! conn [pragma]))
    conn))
