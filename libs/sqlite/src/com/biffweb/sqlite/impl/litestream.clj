(ns com.biffweb.sqlite.impl.litestream
  (:require [clojure.java.io :as io]
            [clojure.java.process :as process]
            [clojure.tools.logging :as log]
            [com.biffweb.core :as biff.core]
            [com.biffweb.stuff.bin :as stuff.bin]
            [com.biffweb.sqlite.impl.defaults :as impl.defaults]))

(def ^:private supported-platforms
  #{[:linux :amd64]
    [:linux :arm64]
    [:macos :amd64]
    [:macos :arm64]
    [:windows :amd64]
    [:windows :arm64]})

(defn litestream-url [{:keys [os arch version]}]
  (stuff.bin/check-platform {:supported-platforms supported-platforms
                             :binary              "litestream"
                             :version             version
                             :os                  os
                             :arch                arch})
  (let [os-str   (case os
                   :linux "linux"
                   :macos "darwin"
                   :windows "windows")
        arch-str (case arch
                   :amd64 "x86_64"
                   :arm64 "arm64")
        ext      (case os
                   (:linux :macos) "tar.gz"
                   :windows "zip")]
    (str "https://github.com/benbjohnson/litestream/releases/download/v"
         version "/litestream-" version "-" os-str "-" arch-str "." ext)))

(defn- get-litestream-version [command]
  (some->> (process/exec command "version")
           (re-find #"[\d]+\.[\d]+\.[\d]+")))

(defn ensure-litestream-binary! [ctx]
  (let [{:biff.sqlite/keys [litestream-version]}
        (merge impl.defaults/defaults ctx)

        {:keys [os arch]} (stuff.bin/platform-info)
        url               (litestream-url {:version litestream-version
                                           :os      os
                                           :arch    arch})]
    (stuff.bin/ensure-binary
     {:executable-basename "litestream"
      :get-version         get-litestream-version
      :target-version      litestream-version
      :url                 url})))

(defn- credential-env
  [{:biff.sqlite/keys [litestream-access-key-id litestream-secret-access-key]}]
  {"LITESTREAM_ACCESS_KEY_ID"     litestream-access-key-id
   "LITESTREAM_SECRET_ACCESS_KEY" (force litestream-secret-access-key)})

(defn- write-config! [{:biff.sqlite/keys [db-path
                                          litestream-dir
                                          litestream-bucket
                                          litestream-endpoint
                                          litestream-region]}]
  (let [config-path  (str litestream-dir "/litestream.yml")
        replica-path (str litestream-dir "/" (.getName (io/file db-path)))

        config
        (str "dbs:\n"
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
    (io/make-parents config-path)
    (spit config-path config)))

(defn- restore! [{:biff.sqlite/keys [db-path litestream-dir] :as ctx} bin-path]
  (when-not (.exists (io/file db-path))
    (io/make-parents db-path)
    (process/exec {:env (credential-env ctx)}
                  bin-path "restore"
                  "-config" (str litestream-dir "/litestream.yml")
                  "-if-replica-exists"
                  db-path)))

(defn- start-replicate!
  [{:biff.sqlite/keys [litestream-dir] :as ctx} bin-path]
  (let [proc (process/start {:env (credential-env ctx)}
                            bin-path "replicate"
                            "-config" (str litestream-dir "/litestream.yml"))]
    (doseq [[from-fn log-level] [[process/stdout :info]
                                 [process/stderr :error]]]
      (.start
       (Thread.
        (fn []
          (try
            (with-open [reader (io/reader (from-fn proc))]
              (doseq [line (line-seq reader)]
                (log/log log-level "[litestream]" line)))
            (catch Exception e
              (log/error e "Error reading litestream output")))))))
    (Thread/sleep 1000)
    (when-not (.isAlive proc)
      (throw (ex-info "Litestream replicate failed to start"
                      {:exit-code (.exitValue proc)})))
    proc))

(defn start [ctx]
  (if-not (:biff.sqlite/litestream-access-key-id ctx)
    ctx
    (let [ctx      (biff.core/validate
                    (merge impl.defaults/defaults ctx)
                    {:required [:biff.sqlite/litestream-access-key-id
                                :biff.sqlite/litestream-bucket
                                :biff.sqlite/litestream-secret-access-key]})
          bin-path (ensure-litestream-binary! ctx)]
      (write-config! ctx)
      (restore! ctx bin-path)
      (assoc ctx ::process (start-replicate! ctx bin-path)))))

(defn stop [{::keys [process]}]
  (when (and process (.isAlive process))
    (.destroy process)
    (let [exited (.waitFor process 5 java.util.concurrent.TimeUnit/SECONDS)]
      (when-not exited
        (.destroyForcibly process)))))
