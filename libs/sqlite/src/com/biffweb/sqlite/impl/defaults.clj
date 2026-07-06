(ns com.biffweb.sqlite.impl.defaults)

(def defaults
  {:biff.sqlite/db-path            "storage/sqlite/main.db"
   :biff.sqlite/schema-path        "resources/schema.sql"
   :biff.sqlite/sqldef-version     "3.10.1"
   :biff.sqlite/bin-dir            "target/bin"
   :biff.sqlite/litestream-version "0.5.9"})
