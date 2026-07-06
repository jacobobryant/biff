(ns com.biffweb.sqlite.impl.pool
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [com.biffweb.sqlite.impl.defaults :as impl.defaults]
            [next.jdbc :as jdbc])
  (:import [com.zaxxer.hikari HikariConfig HikariDataSource]))

;; TODO make this more configurable?

(def ^:private pragmas
  ["PRAGMA journal_mode=WAL"
   "PRAGMA busy_timeout = 5000"
   "PRAGMA foreign_keys = ON"
   "PRAGMA synchronous = NORMAL"])

(defn start-read-pool
  [db-path]
  (io/make-parents db-path)
  (HikariDataSource.
   (doto (HikariConfig.)
     (.setJdbcUrl (str "jdbc:sqlite:" db-path))
     (.setConnectionInitSql (str/join ";" pragmas)))))

(defn start-write-conn
  [db-path]
  (io/make-parents db-path)
  (let [ds   (jdbc/get-datasource {:jdbcUrl (str "jdbc:sqlite:" db-path)})
        conn (jdbc/get-connection ds)]
    (doseq [pragma pragmas]
      (jdbc/execute! conn [pragma]))
    conn))

(defn use-conn
  [ctx]
  (let [{:biff.sqlite/keys [db-path] :as ctx}
        (merge impl.defaults/defaults ctx)

        read-pool  (start-read-pool db-path)
        write-conn (start-write-conn db-path)]
    (-> ctx
        (assoc :biff.sqlite/read-pool read-pool
               :biff.sqlite/write-conn write-conn)
        (update :biff.core/stop conj (fn []
                                       (.close write-conn)
                                       (.close read-pool))))))
