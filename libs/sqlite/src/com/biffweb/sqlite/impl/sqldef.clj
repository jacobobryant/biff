(ns com.biffweb.sqlite.impl.sqldef
  (:require [clojure.java.io :as io]
            [clojure.java.process :as process]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [com.biffweb.sqlite.impl.bin :as impl.bin]
            [com.biffweb.sqlite.impl.defaults :as impl.defaults]
            [com.biffweb.sqlite.impl.schema :as impl.schema]))

(defn ensure-sqldef-binary! [{:biff.sqlite/keys [sqldef-version bin-dir]}]
  (let [{:keys [os arch]} (impl.bin/platform-info)
        asset-name (str "sqlite3def_"
                        (case os
                          :linux "linux"
                          :macos "darwin"
                          :windows "windows")
                        "_"
                        (name arch)
                        "."
                        (if (= os :windows)
                          "zip"
                          "tar.gz"))
        url (str "https://github.com/sqldef/sqldef/releases/download/v"
                 sqldef-version "/" asset-name)]
    (impl.bin/ensure-binary!
     {:executable-basename "sqlite3def"
      :bin-dir             bin-dir
      :get-version         (fn [command]
                             (-> (process/exec command "--version")
                                 (str/split #"\s")
                                 first))
      :target-version      sqldef-version
      :url                 url})))

(defn use-sqldef
  [ctx]
  (let [{:biff.sqlite/keys [db-path
                            schema-path
                            columns
                            extra-init-sql]
         :as               ctx}
        (merge impl.defaults/defaults ctx)

        _ (io/make-parents schema-path)
        _ (io/make-parents db-path)

        sqldef-path (ensure-sqldef-binary! ctx)
        full-sql    (str "-- Auto-generated; do not edit.\n\n"
                         (impl.schema/schema-sql {:biff.sqlite/columns columns})
                         (when (not-empty extra-init-sql)
                           (str "\n\n" (str/join "\n" extra-init-sql))))
        _           (spit schema-path full-sql)
        result      (process/exec sqldef-path db-path "--apply" "-f" schema-path)]
    (when (not-empty result)
      (log/info result)))
  ctx)
