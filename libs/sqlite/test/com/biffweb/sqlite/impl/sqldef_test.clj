(ns com.biffweb.sqlite.impl.sqldef-test
  (:require [clojure.test :refer [deftest]]
            [com.biffweb.stuff.bin :as bin]
            [com.biffweb.sqlite.impl.defaults :as defaults]
            [com.biffweb.sqlite.impl.sqldef :as sqldef]))

(def ^:private version (:biff.sqlite/sqldef-version defaults/defaults))

(defn- prefixed-urls [prefix paths]
  (into #{} (map #(str prefix %)) paths))

(def ^:private unsupported-urls
  (prefixed-urls
   (str "https://github.com/sqldef/sqldef/releases/download/v" version "/")
   #{"mssqldef_darwin_amd64.zip"
     "mssqldef_darwin_arm64.zip"
     "mssqldef_linux_386.tar.gz"
     "mssqldef_linux_amd64.tar.gz"
     "mssqldef_linux_arm.tar.gz"
     "mssqldef_linux_arm64.tar.gz"
     "mssqldef_windows_amd64.zip"
     "mysqldef_darwin_amd64.zip"
     "mysqldef_darwin_arm64.zip"
     "mysqldef_linux_386.tar.gz"
     "mysqldef_linux_amd64.tar.gz"
     "mysqldef_linux_arm.tar.gz"
     "mysqldef_linux_arm64.tar.gz"
     "mysqldef_windows_amd64.zip"
     "psqldef_darwin_amd64.zip"
     "psqldef_darwin_arm64.zip"
     "psqldef_linux_386.tar.gz"
     "psqldef_linux_amd64.tar.gz"
     "psqldef_linux_arm.tar.gz"
     "psqldef_linux_arm64.tar.gz"
     "psqldef_windows_amd64.zip"
     "sqlite3def_linux_386.tar.gz"
     "sqlite3def_linux_arm.tar.gz"}))

(deftest binary-urls-match-release
  (bin/assert-binary-urls
   {:fixture-path       (str "com/biffweb/sqlite/impl/sqlite3def-urls-"
                             version ".edn")
    :github-release-url (str "https://api.github.com/repos/sqldef/sqldef/releases/tags/v" version)
    :unsupported-urls   unsupported-urls
    :url-fn             sqldef/sqlite3def-url
    :version            version}))
