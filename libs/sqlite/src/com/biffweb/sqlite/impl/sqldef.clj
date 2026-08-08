(ns com.biffweb.sqlite.impl.sqldef
  (:require [clojure.java.io :as io]
            [clojure.java.process :as process]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [com.biffweb.stuff.bin :as stuff.bin]
            [com.biffweb.sqlite.impl.defaults :as impl.defaults]
            [com.biffweb.sqlite.impl.schema :as impl.schema]))

(def ^:private supported-platforms
  #{[:linux :amd64]
    [:linux :arm64]
    [:macos :amd64]
    [:macos :arm64]
    [:windows :amd64]})

(defn sqlite3def-url [{:keys [os arch version]}]
  (stuff.bin/check-platform {:supported-platforms supported-platforms
                             :binary              "sqlite3def"
                             :version             version
                             :os                  os
                             :arch                arch})
  (let [os-str (case os
                 :linux "linux"
                 :macos "darwin"
                 :windows "windows")
        ext    (case os
                 :linux "tar.gz"
                 (:macos :windows) "zip")]
    (str "https://github.com/sqldef/sqldef/releases/download/v"
         version "/sqlite3def_" os-str "_" (name arch) "." ext)))

(defn- get-sqldef-version [command]
  (-> (process/exec command "--version")
      (str/split #"\s")
      first))

(defn ensure-sqldef-binary! [ctx]
  (let [{:biff.sqlite/keys [sqldef-version]}
        (merge impl.defaults/defaults ctx)

        {:keys [os arch]} (stuff.bin/platform-info)
        url               (sqlite3def-url {:version sqldef-version
                                           :os      os
                                           :arch    arch})]
    (stuff.bin/ensure-binary
     {:executable-basename "sqlite3def"
      :get-version         get-sqldef-version
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
        result      (process/exec sqldef-path db-path
                                  "--apply" "-f" schema-path)]
    (when (not-empty result)
      (log/info result)))
  ctx)
