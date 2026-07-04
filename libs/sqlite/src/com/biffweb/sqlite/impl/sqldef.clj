(ns com.biffweb.sqlite.impl.sqldef
  (:require [clojure.java.io :as io]
            [clojure.java.process :as process]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [com.biffweb.sqlite.impl.defaults :as impl.defaults]
            [com.biffweb.sqlite.impl.schema :as schema])
  (:import [java.nio.file Files Paths]
           [java.nio.file.attribute PosixFilePermission]))

(defn- windows? []
  (str/includes? (str/lower-case (System/getProperty "os.name")) "windows"))

(defn- local-bin-path []
  (str impl.defaults/bin-dir
       "/"
       (if (windows?) "sqlite3def.exe" "sqlite3def")))

(defn- find-global-sqlite3def []
  (try
    ;; TODO check for successful exit code or something
    ;; TODO does this work on windows / if the binary name is sqlite3def.exe?
    ;; idk maybe it does.
    (let [_output (str/trim (process/exec "sqlite3def" "--help"))]
      (log/info "Found globally installed sqlite3def")
      "sqlite3def")
    (catch Exception _ nil)))

;; TODO see if there's a more robust way to do this
(defn- check-version [bin-path]
  (try
    (let [output (str/trim (process/exec bin-path "--help"))]
      (when (not-empty output)
        (some->> output (re-find #"v([\d]+\.[\d]+\.[\d]+)") second)))
    (catch Exception e
      (log/warn "Failed to check sqlite3def version:" (.getMessage e))
      nil)))

;; TODO in libs/tasks/ we have some shared code for downloading binaries for
;; various tools. Extract that into a new libs/stuff/ library (meant for
;; internal use, no need to document it) and then reuse that binary logic here.
;; If needed generalize it further to work for sqldef.
(defn- infer-download-filename []
  (let [os-name (str/lower-case (System/getProperty "os.name"))
        os-type (cond
                  (str/includes? os-name "linux") "linux"
                  (or (str/includes? os-name "mac")
                      (str/includes? os-name "darwin")) "darwin"
                  (str/includes? os-name "windows") "windows"
                  :else (throw (ex-info (str "Unable to auto-install sqlite3def (unsupported OS: "
                                             (System/getProperty "os.name")
                                             "). Please install it manually.")
                                        {:os.name (System/getProperty "os.name")})))
        arch    (case (System/getProperty "os.arch")
                  ("amd64" "x86_64") "amd64"
                  "aarch64" "arm64"
                  (throw (ex-info (str "Unable to auto-install sqlite3def (unsupported architecture: "
                                       (System/getProperty "os.arch")
                                       "). Please install it manually.")
                                  {:os.arch (System/getProperty "os.arch")})))
        ext     (if (= os-type "windows") "zip" "tar.gz")]
    (str "sqlite3def_" os-type "_" arch "." ext)))

(defn- download-and-extract! [version]
  (let [filename     (infer-download-filename)
        url          (str "https://github.com/sqldef/sqldef/releases/download/v"
                          version "/" filename)
        archive-path (str impl.defaults/bin-dir "/" filename)
        bin-path     (local-bin-path)]
    (log/info "Downloading sqlite3def from" url)
    (.mkdirs (io/file impl.defaults/bin-dir))
    (process/exec "curl" "-sL" "-o" archive-path url)
    (log/info "Extracting sqlite3def binary...")
    (if (windows?)
      (process/exec "powershell" "-Command"
                    (str "Expand-Archive -Path '" archive-path
                         "' -DestinationPath '" impl.defaults/bin-dir "' -Force"))
      (process/exec "tar" "xzf" archive-path "-C" impl.defaults/bin-dir))
    (io/delete-file archive-path true)
    (when-not (windows?)
      (let [perms #{PosixFilePermission/OWNER_READ
                    PosixFilePermission/OWNER_WRITE
                    PosixFilePermission/OWNER_EXECUTE}]
        (Files/setPosixFilePermissions
         (Paths/get bin-path (into-array String [])) perms)))
    (log/info "sqlite3def binary installed at" bin-path)))

(defn- ensure-local-binary! [version]
  (let [current (check-version (local-bin-path))]
    (when (not= current version)
      (when current
        (log/info "sqlite3def version mismatch: installed" current
                  "expected" version))
      (download-and-extract! version))))

(defn resolve-bin! [version]
  ;; TODO ensure we always have the version specified even if there's a global
  ;; binary
  (or (find-global-sqlite3def)
      (do
        (ensure-local-binary! version)
        (local-bin-path))))

(defn use-sqldef
  [{:biff.sqlite/keys [db-path
                       schema-path
                       columns
                       extra-init-sql
                       sqldef-version]
    :or               {db-path        impl.defaults/db-path
                       schema-path    impl.defaults/schema-path
                       sqldef-version impl.defaults/sqldef-version}
    :as               ctx}]
  (io/make-parents schema-path)
  (io/make-parents db-path)
  (let [sqldef-path (resolve-bin! sqldef-version)
        full-sql    (str "-- Auto-generated; do not edit.\n\n"
                         (schema/schema-sql {:biff.sqlite/columns columns})
                         (when (not-empty extra-init-sql)
                           (str "\n\n" (str/join "\n" extra-init-sql))))
        _           (spit schema-path full-sql)
        result      (process/exec sqldef-path db-path "--apply" "-f" schema-path)]
    (when (not-empty result)
      (log/info result)))
  ctx)
