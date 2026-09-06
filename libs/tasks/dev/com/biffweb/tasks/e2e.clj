(ns com.biffweb.tasks.e2e
  (:require [babashka.fs :as fs]
            [babashka.process :as process]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [nrepl.core :as nrepl]))

(def ^:private container-prefix
  (str "biff-tasks-e2e-" (.pid (java.lang.ProcessHandle/current))))

(defn- run-command! [dir & command]
  (let [{:keys [exit]} @(process/process command
                                         {:dir     (str dir)
                                          :inherit true})]
    (when-not (zero? exit)
      (throw (ex-info (str "Command failed: " (str/join " " command))
                      {:command command :exit exit})))))

(defn- output! [dir & command]
  (let [{:keys [exit out err]} (apply process/sh {:dir (str dir)} command)]
    (when-not (zero? exit)
      (throw (ex-info (str "Command failed: " (str/join " " command))
                      {:command command :exit exit :out out :err err})))
    (str/trim out)))

(defn- verify! [description value]
  (when-not value
    (throw (ex-info (str "Verification failed: " description) {}))))

(defn- task! [dir & args]
  (let [command                (concat ["clojure" "-M:run"] args)
        {:keys [exit out err]} (apply process/sh {:dir (str dir) :in ""}
                                      command)]
    (print out)
    (binding [*out* *err*]
      (print err))
    (when-not (zero? exit)
      (throw (ex-info (str "Command failed: " (str/join " " command))
                      {:command command :exit exit :out out :err err})))))

(defn- task-output! [dir & args]
  (let [{:keys [exit out err]}
        (apply process/sh {:dir (str dir) :in ""}
               "clojure" "-M:run" args)]
    (when-not (zero? exit)
      (throw (ex-info (str "Command failed: clojure -M:run "
                           (str/join " " args))
                      {:command args :exit exit :out out :err err})))
    (str/trim out)))

(defn- wait-for! [description f]
  (loop [attempts 30]
    (cond
      (f) true

      (zero? attempts)
      (throw (ex-info (str "Timed out waiting for " description) {}))

      :else (do (Thread/sleep 1000)
                (recur (dec attempts))))))

(defn- process! [dir & command]
  (process/process command {:dir (str dir) :in "" :out :inherit :err :inherit}))

(defn- stop-process! [p]
  (when p
    (.destroy ^Process (:proc p))
    (when-not (.waitFor ^Process (:proc p) 5
                        java.util.concurrent.TimeUnit/SECONDS)
      (.destroyForcibly ^Process (:proc p)))
    @p))

(defn- nrepl-working? [port]
  (try
    (with-open [connection (nrepl/connect :host "localhost" :port port)]
      (let [client (nrepl/client connection 5000)]
        (= [5] (nrepl/response-values
                (nrepl/message client {:op "eval" :code "(+ 2 3)"})))))
    (catch Exception _
      false)))

(defn- copy-fixture! [work-dir]
  (let [source (io/file (io/resource "com/biffweb/tasks/e2e-app"))]
    (fs/copy-tree source work-dir)
    (let [deps-file (io/file (str work-dir) "deps.edn")]
      (spit deps-file
            (str/replace (slurp deps-file)
                         "TASKS_ROOT"
                         (str (fs/absolutize (fs/path "."))))))
    (spit (io/file (str work-dir) "tests.edn")
          "#kaocha/v1\n{:tests [{:id :unit,:test-paths [\"test\"]}]}")))

(defn- init-git! [work-dir]
  (run-command! work-dir "git" "init" "--initial-branch=main")
  (run-command! work-dir "git" "config" "user.email" "e2e@example.com")
  (run-command! work-dir "git" "config" "user.name" "E2E")
  (run-command! work-dir "git" "add" ".")
  (run-command! work-dir "git" "commit" "-m" "Initial commit"))

(defn- run-local-tasks! [work-dir]
  (let [tests-file   (io/file (str work-dir) "tests.edn")
        tests-before (slurp tests-file)]
    (task! work-dir "add" "org.clojure/data.json")
    (verify! "add updates deps.edn"
             (str/includes? (slurp (io/file (str work-dir) "deps.edn"))
                            "org.clojure/data.json"))
    (task! work-dir "update" "--clj-kondo-files-only")
    (verify! "update installs clj-kondo configuration"
             (fs/exists? (fs/path work-dir ".clj-kondo" "imports")))
    (task! work-dir "init")
    (doseq [path ["config.env" "config.prod.env"]]
      (verify! (str "init creates " path)
               (fs/exists? (fs/path work-dir path))))
    (doseq [[command version] [["clj-kondo" "2026.04.15"]
                               ["cljfmt" "0.16.3"]
                               ["tailwindcss" "4.2.4"]]
            :let              [binary (str (fs/path work-dir "target/bin"
                                                    command))]]
      (verify! (str "init installs " command " to target/bin")
               (fs/exists? binary))
      (verify! (str "init installs the configured " command " version")
               (str/includes? (output! work-dir binary "--version") version)))
    (task! work-dir "css" "--minify")
    (verify! "css writes a non-empty stylesheet"
             (pos? (fs/size (fs/path work-dir
                                     "target/resources/public/css/main.css"))))
    (task! work-dir "format")
    (verify! "format rewrites an unformatted file"
             (not= tests-before (slurp tests-file))))
  (verify! "lint reports a completed lint run"
           (str/includes? (task-output! work-dir "lint") "linting took"))
  (verify! "test reports the fixture assertion count"
           (str/includes? (task-output! work-dir "test")
                          "1 tests, 1 assertions"))
  (task! work-dir "docs")
  (verify! "docs generates API documentation"
           (str/includes?
            (slurp (io/file (str work-dir) "target/docs/com.example.app.md"))
            "com.example.app"))
  (task! work-dir "uberjar")
  (verify! "uberjar contains the application namespace"
           (str/includes? (output! work-dir "unzip" "-Z1" "target/jar/app.jar")
                          "com/example/app"))
  (let [uberjar-process (process/process ["java" "-jar" "target/jar/app.jar"]
                                         {:dir       (str work-dir)
                                          :extra-env {"PORT" "18081"}
                                          :inherit   true})]
    (try
      (wait-for! "the uberjar server"
                 #(try
                    (= "ok" (output! work-dir "curl" "-fsS"
                                     "http://localhost:18081"))
                    (catch Exception _ false)))
      (finally
        (stop-process! uberjar-process))))
  (let [code-quality-output (task-output! work-dir "code-quality")]
    (verify! "code-quality runs lint"
             (str/includes? code-quality-output "linting took"))
    (verify! "code-quality runs tests"
             (str/includes? code-quality-output "1 tests, 1 assertions")))
  (fs/delete-tree (fs/path work-dir "target/resources"))
  (let [source        (io/file (str work-dir) "src/com/example/app.clj")
        source-code   (slurp source)
        dev-out       (io/file (str work-dir) "target/dev.out")
        dev-err       (io/file (str work-dir) "target/dev.err")
        tailwind-runs #(count (re-seq #"Done in"
                                      (str (when (.exists dev-out)
                                             (slurp dev-out))
                                           (when (.exists dev-err)
                                             (slurp dev-err)))))
        dev-process   (process/process ["clojure" "-M:run" "dev"]
                                       {:dir (str work-dir)
                                        :in  ""
                                        :out dev-out
                                        :err dev-err})]
    (try
      (wait-for! "the dev server"
                 #(try
                    (= "ok" (output! work-dir "curl" "-fsS"
                                     "http://localhost:18080"))
                    (catch Exception _ false)))
      (wait-for! "initial Tailwind output" #(pos? (tailwind-runs)))
      (let [initial-tailwind-runs (tailwind-runs)]
        (spit source (str/replace source-code "(atom \"ok\")"
                                  "(atom \"dev-ok\")"))
        (wait-for! "the dev server to reload source changes"
                   #(try
                      (= "dev-ok" (output! work-dir "curl" "-fsS"
                                           "http://localhost:18080"))
                      (catch Exception _ false)))
        (wait-for! "Tailwind output after recompiling a source change"
                   #(< initial-tailwind-runs (tailwind-runs))))
      (finally
        (spit source source-code)
        (stop-process! dev-process))))
  (let [nrepl-process (process! work-dir "clojure" "-M:run" "nrepl"
                                "--bind" "localhost")]
    (try
      (wait-for! "the local nREPL server"
                 #(nrepl-working? 17888))
      (finally
        (stop-process! nrepl-process))))
  (run-command! work-dir "git" "add" ".")
  (run-command! work-dir "git" "commit" "-m" "Run local tasks"))

(defn- container-exists? [container-name]
  (zero? (:exit (process/sh "incus" "info" container-name))))

(defn- delete-container! [container-name]
  (when (container-exists? container-name)
    (process/sh "incus" "delete" "--force" container-name)))

(defn- container-ip [container-name]
  (let [state (json/read-str
               (output! "." "incus" "query"
                        (str "/1.0/instances/" container-name "/state"))
               :key-fn keyword)]
    (some (fn [{:keys [address family scope]}]
            (when (and (= family "inet") (= scope "global"))
              address))
          (get-in state [:network :eth0 :addresses]))))

(defn- configure-ssh! [work-dir container-name ip]
  (let [key-file (str (io/file (str work-dir) "id_ed25519"))
        bin-dir  (io/file (str work-dir) "ssh-bin")]
    (run-command! work-dir "ssh-keygen" "-q" "-t" "ed25519" "-N" ""
                  "-f" key-file)
    (run-command! work-dir "incus" "exec" container-name "--" "mkdir"
                  "-p" "/root/.ssh")
    (run-command! work-dir "incus" "file" "push" (str key-file ".pub")
                  (str container-name "/root/.ssh/authorized_keys"))
    (spit (io/file (str work-dir) "ssh-config")
          (str "Host " ip "\n"
               "  IdentityFile " key-file "\n"
               "  StrictHostKeyChecking no\n"
               "  UserKnownHostsFile /dev/null\n"))
    (fs/create-dirs bin-dir)
    (doseq [command ["ssh" "scp"]]
      (let [script (io/file bin-dir command)]
        (spit script
              (str "#!/usr/bin/env bash\nexec /usr/bin/" command " -F "
                   (io/file (str work-dir) "ssh-config") " \"$@\"\n"))
        (.setExecutable script true)))
    (spit (io/file (str work-dir) "task.env")
          (str "SERVER=" ip "\n"
               "PATH='" bin-dir "':$PATH\n"))))

(defn- task-env-result [work-dir & args]
  (let [{:keys [out err] :as result}
        (process/sh {:dir (str work-dir) :in ""}
                    "bash" "-c"
                    (str "set -a; . ./task.env; set +a; exec clojure -M:run "
                         (str/join " " args)))]
    (print out)
    (binding [*out* *err*]
      (print err))
    result))

(defn- task-env! [work-dir & args]
  (let [{:keys [exit out err]} (apply task-env-result work-dir args)]
    (when-not (zero? exit)
      (throw (ex-info (str "Command failed: clojure -M:run "
                           (str/join " " args))
                      {:command args :exit exit :out out :err err})))))

(defn- soft-deploy! [work-dir]
  (let [{:keys [exit out err]} (task-env-result work-dir "deploy" "--soft")
        output                 (str out err)]
    (when-not (or (zero? exit)
                  (and (str/includes? output ":ok")
                       (str/includes? output "Connection reset")))
      (throw (ex-info "Command failed: clojure -M:run deploy --soft"
                      {:exit exit :out out :err err})))))

(defn- run-production-tasks!
  [work-dir container-name image nrepl-port]
  (delete-container! container-name)
  (run-command! work-dir "incus" "launch" image container-name
                "-c" "security.privileged=true")
  (wait-for! "an IP address for the Incus container"
             #(not (str/blank? (container-ip container-name))))
  (let [ip (container-ip container-name)]
    (run-command! work-dir "incus" "exec" container-name "--" "bash" "-lc"
                  "apt-get update && apt-get install -y openssh-server")
    (configure-ssh! work-dir container-name ip)
    (wait-for! "SSH in the Incus container"
               #(zero? (:exit (process/sh "ssh" "-F"
                                          (str (io/file (str work-dir)
                                                        "ssh-config"))
                                          (str "root@" ip) "true"))))
    (task-env! work-dir "prod-setup")
    (verify! "prod-setup creates the deployment user"
             (= "e2e-app" (output! work-dir "incus" "exec" container-name
                                   "--" "id" "-un" "e2e-app")))
    (verify! "prod-setup unlocks the deployment user"
             (not (str/includes?
                   (output! work-dir "incus" "exec" container-name "--"
                            "passwd" "-S" "e2e-app")
                   " L ")))
    (verify! "prod-setup installs the systemd unit"
             (str/includes?
              (output! work-dir "incus" "exec" container-name "--" "cat"
                       "/etc/systemd/system/e2e-app.service")
              "WorkingDirectory=/home/e2e-app/repo"))
    (task-env! work-dir "deploy")
    (verify! "deploy pushes the application source"
             (str/includes?
              (output! work-dir "incus" "exec" container-name "--" "cat"
                       "/home/e2e-app/repo/src/com/example/app.clj")
              "com.example.app"))
    (wait-for! "the production server"
               #(try
                  (= "ok" (output! work-dir "incus" "exec" container-name
                                   "--" "curl" "-fsS"
                                   "http://localhost:8080"))
                  (catch Exception _ false)))
    (let [pid-before (output! work-dir "incus" "exec" container-name "--"
                              "systemctl" "show" "--property" "MainPID"
                              "--value" "e2e-app")]
      (task-env! work-dir "prod-restart")
      (wait-for! "a new production process"
                 #(not= pid-before
                        (output! work-dir "incus" "exec" container-name "--"
                                 "systemctl" "show" "--property" "MainPID"
                                 "--value" "e2e-app"))))
    (wait-for! "the restarted production server"
               #(try
                  (= "ok" (output! work-dir "incus" "exec" container-name
                                   "--" "curl" "-fsS"
                                   "http://localhost:8080"))
                  (catch Exception _ false)))
    (let [logs-file    (io/file (str work-dir) "prod-logs.out")
          logs-process (process!
                        work-dir "bash" "-c"
                        (str "set -a; . ./task.env; set +a; "
                             "exec clojure -M:run prod-logs 100 "
                             "> prod-logs.out 2>&1"))]
      (try
        (wait-for! "prod-logs to start journalctl"
                   #(zero? (:exit (process/sh "incus" "exec" container-name
                                              "--" "pgrep" "-u" "e2e-app"
                                              "journalctl"))))
        (task-env! work-dir "prod-restart")
        (run-command! work-dir "incus" "exec" container-name "--" "pkill"
                      "-TERM" "-u" "e2e-app" "journalctl")
        (verify! "prod-logs exits after journalctl"
                 (.waitFor ^Process (:proc logs-process) 30
                           java.util.concurrent.TimeUnit/SECONDS))
        (finally
          (stop-process! logs-process)
          (verify! "prod-logs returns application output"
                   (str/includes? (slurp logs-file) "e2e-app-started")))))
    (let [tunnel-process
          (process! work-dir "bash" "-c"
                    (str "set -a; . ./task.env; set +a; "
                         "exec clojure -M:run prod-nrepl"))]
      (try
        (wait-for! "the production nREPL tunnel"
                   #(nrepl-working? nrepl-port))
        (finally
          (stop-process! tunnel-process))))
    (let [source (io/file (str work-dir) "src/com/example/app.clj")]
      (spit source (str/replace (slurp source) "(atom \"ok\")"
                                "(atom \"soft-ok\")"))
      (run-command! work-dir "git" "add" ".")
      (run-command! work-dir "git" "commit" "-m" "Change response")
      (soft-deploy! work-dir)
      (wait-for! "the soft-deployed application"
                 #(try
                    (= "soft-ok" (output! work-dir "incus" "exec"
                                          container-name "--" "curl" "-fsS"
                                          "http://localhost:8080"))
                    (catch Exception _ false))))))

(defn- configure-production-work-dir! [work-dir nrepl-port]
  (when-not (= nrepl-port 17888)
    (doseq [path ["resources/config.edn" "src/com/example/app.clj"]
            :let [file (io/file (str work-dir) path)]]
      (spit file (str/replace (slurp file) "17888" (str nrepl-port))))
    (run-command! work-dir "git" "add" ".")
    (run-command! work-dir "git" "commit" "-m" "Configure production port")))

(defn- run-production-target!
  [base-dir {:keys [image label nrepl-port]}]
  (let [work-dir       (fs/create-temp-dir
                        {:prefix (str "biff-tasks-e2e-" label "-")})
        container-name (str container-prefix "-" label)]
    (try
      (fs/copy-tree base-dir work-dir)
      (configure-production-work-dir! work-dir nrepl-port)
      (run-production-tasks! work-dir container-name image nrepl-port)
      (finally
        (delete-container! container-name)
        (fs/delete-tree work-dir)))))

(defn test-e2e []
  (when-not (zero? (:exit (process/sh "incus" "version")))
    (throw (ex-info "The test-e2e task requires Incus." {})))
  (let [work-dir (fs/create-temp-dir {:prefix "biff-tasks-e2e-local-"})]
    (try
      (copy-fixture! work-dir)
      (init-git! work-dir)
      (run-local-tasks! work-dir)
      (let [targets [{:image      "images:debian/13"
                      :label      "debian"
                      :nrepl-port 17888}
                     {:image      "images:ubuntu/24.04"
                      :label      "ubuntu"
                      :nrepl-port 17889}]
            results (->> targets
                         (mapv #(future
                                  (try
                                    (run-production-target! work-dir %)
                                    (catch Throwable e e))))
                         (mapv deref))]
        (when-some [failure (first (filter #(instance? Throwable %) results))]
          (throw failure)))
      (finally
        (fs/delete-tree work-dir)))))
