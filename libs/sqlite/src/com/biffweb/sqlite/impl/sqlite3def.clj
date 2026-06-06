(ns com.biffweb.sqlite.impl.sqlite3def
  "Auto-installation of sqlite3def for schema migrations.
   Downloads the sqlite3def binary automatically and makes it available
   for apply-schema!. Modeled after the litestream auto-install logic."
  (:require [clojure.tools.logging :as log]
            [clojure.java.io :as io]
            [clojure.java.process :as process]
            [clojure.string :as str])
  (:import [java.nio.file Files Paths]
           [java.nio.file.attribute PosixFilePermission]))

(def default-version "3.10.1")

(def sqlite3def-dir "storage/sqlite3def")

(defn- windows? []
  (str/includes? (str/lower-case (System/getProperty "os.name")) "windows"))

(defn local-bin-path
  ([] (local-bin-path default-version))
  ([_version]
   (str sqlite3def-dir "/" (if (windows?) "sqlite3def.exe" "sqlite3def"))))

(defn- infer-download-filename
  "Infers the correct sqlite3def release filename for the current platform."
  [_version]
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

(defn- find-global-sqlite3def
  "Returns \"sqlite3def\" if a globally installed sqlite3def is found on PATH, or nil."
  []
  (try
    (let [_output (str/trim (process/exec "sqlite3def" "--help"))]
      (log/info "Found globally installed sqlite3def")
      "sqlite3def")
    (catch Exception _ nil)))

(defn- check-version
  "Returns the version string of the sqlite3def binary at the given path, or nil."
  [bin-path]
  (try
    (let [output (str/trim (process/exec bin-path "--help"))]
      (when (not-empty output)
        (some->> output (re-find #"v([\d]+\.[\d]+\.[\d]+)") second)))
    (catch Exception e
      (log/warn "Failed to check sqlite3def version:" (.getMessage e))
      nil)))

(defn- download-and-extract!
  "Downloads sqlite3def binary from GitHub releases and extracts it."
  [version]
  (let [filename     (infer-download-filename version)
        url          (str "https://github.com/sqldef/sqldef/releases/download/v"
                          version "/" filename)
        archive-path (str sqlite3def-dir "/" filename)
        bin-path     (local-bin-path version)]
    (log/info "Downloading sqlite3def from" url)
    (.mkdirs (io/file sqlite3def-dir))
    (process/exec "curl" "-sL" "-o" archive-path url)
    (log/info "Extracting sqlite3def binary...")
    (if (windows?)
      (process/exec "powershell" "-Command"
                    (str "Expand-Archive -Path '" archive-path
                         "' -DestinationPath '" sqlite3def-dir "' -Force"))
      (process/exec "tar" "xzf" archive-path "-C" sqlite3def-dir))
    (io/delete-file archive-path true)
    (when-not (windows?)
      (let [perms #{PosixFilePermission/OWNER_READ
                    PosixFilePermission/OWNER_WRITE
                    PosixFilePermission/OWNER_EXECUTE}]
        (Files/setPosixFilePermissions
         (Paths/get bin-path (into-array String [])) perms)))
    (log/info "sqlite3def binary installed at" bin-path)))

(defn- ensure-local-binary!
  "Downloads sqlite3def to local dir if not present or version mismatch."
  [version]
  (let [current (check-version (local-bin-path version))]
    (when (not= current version)
      (when current
        (log/info "sqlite3def version mismatch: installed" current
                  "expected" version))
      (download-and-extract! version))))

(defn resolve-bin!
  "Returns the path to the sqlite3def binary to use. Prefers global install.
   version is the desired version string (e.g. \"3.10.1\")."
  ([] (resolve-bin! default-version))
  ([version]
   (or (find-global-sqlite3def)
       (do (ensure-local-binary! version)
           (local-bin-path version)))))
