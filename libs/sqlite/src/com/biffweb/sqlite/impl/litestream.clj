(ns com.biffweb.sqlite.impl.litestream
  "Litestream integration for continuous SQLite replication to S3.
   Downloads litestream binary automatically and runs it as a subprocess.
   Adapted from budgetswu.lib.litestream."
  (:require [clojure.tools.logging :as log]
            [clojure.java.io :as io]
            [clojure.java.process :as process]
            [clojure.string :as str])
  (:import [java.nio.file Files Paths]
           [java.nio.file.attribute PosixFilePermission]))

(def default-version "0.5.9")

(def litestream-dir "storage/litestream")

(defn- windows? []
  (str/includes? (str/lower-case (System/getProperty "os.name")) "windows"))

(defn local-bin-path []
  (str litestream-dir "/" (if (windows?) "litestream.exe" "litestream")))

(defn litestream-config-path []
  (str litestream-dir "/litestream.yml"))

(defn- infer-download-filename
  "Infers the correct litestream release filename for the current platform."
  [version]
  (let [os-name (str/lower-case (System/getProperty "os.name"))
        os-type (cond
                  (str/includes? os-name "linux") "linux"
                  (or (str/includes? os-name "mac")
                      (str/includes? os-name "darwin")) "darwin"
                  (str/includes? os-name "windows") "windows"
                  :else (throw (ex-info (str "Unable to auto-install litestream (unsupported OS: "
                                             (System/getProperty "os.name")
                                             "). Please install it manually.")
                                        {:os.name (System/getProperty "os.name")})))
        arch    (case (System/getProperty "os.arch")
                  ("amd64" "x86_64") "x86_64"
                  "aarch64" "arm64"
                  (throw (ex-info (str "Unable to auto-install litestream (unsupported architecture: "
                                       (System/getProperty "os.arch")
                                       "). Please install it manually.")
                                  {:os.arch (System/getProperty "os.arch")})))
        ext     (if (= os-type "windows") "zip" "tar.gz")]
    (str "litestream-" version "-" os-type "-" arch "." ext)))

(defn- find-global-litestream
  "Returns \"litestream\" if a globally installed litestream is found on PATH, or nil."
  []
  (try
    (let [output (str/trim (process/exec "litestream" "version"))]
      (log/info "Found globally installed litestream:" output)
      "litestream")
    (catch Exception _ nil)))

(defn- check-version
  "Returns the version string of the litestream binary at the given path, or nil."
  [bin-path]
  (try
    (let [output (str/trim (process/exec bin-path "version"))]
      (some->> output not-empty (re-find #"[\d]+\.[\d]+\.[\d]+")))
    (catch Exception e
      (log/warn "Failed to check litestream version:" (.getMessage e))
      nil)))

(defn- download-and-extract!
  "Downloads litestream binary from GitHub releases and extracts it."
  [version]
  (let [filename     (infer-download-filename version)
        url          (str "https://github.com/benbjohnson/litestream/releases/download/v"
                          version "/" filename)
        archive-path (str litestream-dir "/" filename)
        bin-path     (local-bin-path)]
    (log/info "Downloading litestream from" url)
    (.mkdirs (io/file litestream-dir))
    (process/exec "curl" "-sL" "-o" archive-path url)
    (log/info "Extracting litestream binary...")
    (if (windows?)
      (process/exec "powershell" "-Command"
                    (str "Expand-Archive -Path '" archive-path
                         "' -DestinationPath '" litestream-dir "' -Force"))
      (process/exec "tar" "xzf" archive-path "-C" litestream-dir))
    (io/delete-file archive-path true)
    (when-not (windows?)
      (let [perms #{PosixFilePermission/OWNER_READ
                    PosixFilePermission/OWNER_WRITE
                    PosixFilePermission/OWNER_EXECUTE}]
        (Files/setPosixFilePermissions
         (Paths/get bin-path (into-array String [])) perms)))
    (log/info "Litestream binary installed at" bin-path)))

(defn- ensure-local-binary!
  "Downloads litestream to local dir if not present or version mismatch."
  [version]
  (let [current (check-version (local-bin-path))]
    (when (not= current version)
      (when current
        (log/info "Litestream version mismatch: installed" current
                  "expected" version))
      (download-and-extract! version))))

(defn- resolve-bin!
  "Returns the path to the litestream binary to use. Prefers global install."
  [version]
  (or (find-global-litestream)
      (do (ensure-local-binary! version)
          (local-bin-path))))

(defn- credential-env
  "Returns env var map for litestream subprocess with S3 credentials."
  [{:biff.sqlite/keys [litestream-access-key-id litestream-secret-access-key]}]
  {"LITESTREAM_ACCESS_KEY_ID"     litestream-access-key-id
   "LITESTREAM_SECRET_ACCESS_KEY" (some-> litestream-secret-access-key (.invoke))})

(defn- write-config!
  "Generates litestream YAML config file.
   Uses env var references for secrets so credentials aren't written to disk."
  [{:biff.sqlite/keys [db-path litestream-bucket litestream-path
                       litestream-endpoint litestream-region]
    :or               {db-path "storage/sqlite/main.db"}}]
  (let [replica-path (if (str/blank? litestream-path)
                       (str/replace db-path #"^.*/" "")
                       (str (str/replace litestream-path #"/$" "") "/"
                            (str/replace db-path #"^.*/" "")))
        config       (str "dbs:\n"
                          "  - path: " db-path "\n"
                          "    replicas:\n"
                          "      - type: s3\n"
                          "        bucket: " litestream-bucket "\n"
                          "        path: " replica-path "\n"
                          (when litestream-endpoint
                            (str "        endpoint: " litestream-endpoint "\n"))
                          (when litestream-region
                            (str "        region: " litestream-region "\n"))
                          "        access-key-id: $LITESTREAM_ACCESS_KEY_ID\n"
                          "        secret-access-key: $LITESTREAM_SECRET_ACCESS_KEY\n")]
    (.mkdirs (io/file litestream-dir))
    (spit (litestream-config-path) config)
    (log/info "Litestream config written to" (litestream-config-path))))

(defn- restore!
  "Restores SQLite database from S3 replica if local DB doesn't exist.
   Returns true if restore was performed, false otherwise."
  [{:biff.sqlite/keys [db-path]
    :or               {db-path "storage/sqlite/main.db"}
    :as               ctx}
   bin-path]
  (let [db-file (io/file db-path)]
    (if (.exists db-file)
      (do (log/info "Local database exists, skipping restore")
          false)
      (do (log/info "No local database found, attempting restore from S3...")
          (.mkdirs (.getParentFile db-file))
          (let [env       (credential-env ctx)
                proc      (process/start {:env env}
                                         bin-path "restore"
                                         "-config" (litestream-config-path)
                                         "-if-replica-exists"
                                         db-path)
                exit-code (.waitFor proc)]
            (cond
              (and (zero? exit-code) (.exists db-file))
              (do (log/info "Database restored from S3")
                  true)

              (zero? exit-code)
              (do (log/info "No replica found in S3, starting fresh")
                  false)

              :else
              (do (log/warn "Litestream restore exited with code" exit-code
                            "- starting fresh")
                  false)))))))

(defn- start-replicate!
  "Starts litestream replicate as a subprocess. Returns the Process."
  [ctx bin-path]
  (log/info "Starting litestream replicate...")
  (let [env  (credential-env ctx)
        proc (process/start {:env env}
                            bin-path "replicate"
                            "-config" (litestream-config-path))]
    (.start (Thread. (fn []
                       (try
                         (with-open [reader (io/reader (process/stdout proc))]
                           (doseq [line (line-seq reader)]
                             (log/info "[litestream]" line)))
                         (catch Exception e
                           (log/error e "Error reading litestream stdout"))))))
    (.start (Thread. (fn []
                       (try
                         (with-open [reader (io/reader (process/stderr proc))]
                           (doseq [line (line-seq reader)]
                             (log/error "[litestream]" line)))
                         (catch Exception e
                           (log/error e "Error reading litestream stderr"))))))
    (Thread/sleep 1000)
    (when-not (.isAlive proc)
      (throw (ex-info "Litestream replicate failed to start"
                      {:exit-code (.exitValue proc)})))
    (log/info "Litestream replicate started (PID:" (.pid proc) ")")
    proc))

(defn- stop-replicate!
  "Stops the litestream replicate subprocess gracefully."
  [^Process process]
  (when (and process (.isAlive process))
    (log/info "Stopping litestream replicate...")
    (.destroy process)
    (let [exited (.waitFor process 10 java.util.concurrent.TimeUnit/SECONDS)]
      (when-not exited
        (log/warn "Litestream did not stop gracefully, forcing...")
        (.destroyForcibly process)))
    (log/info "Litestream replicate stopped")))

(defn use-litestream
  [{:biff.sqlite/keys [litestream-bucket
                       litestream-access-key-id
                       litestream-secret-access-key
                       litestream-version]
    :as               ctx}]
  (if-not (every? some? [litestream-bucket
                         litestream-access-key-id
                         litestream-secret-access-key])
    (do (log/info "Litestream: S3 config not present, skipping")
        ctx)
    (let [version  (or litestream-version default-version)
          bin-path (resolve-bin! version)]
      (write-config! ctx)
      (restore! ctx bin-path)
      (let [process (start-replicate! ctx bin-path)]
        (update ctx :biff.core/stop conj #(stop-replicate! process))))))
