(ns com.biffweb.tasks
  (:require [babashka.curl :as curl]
            [babashka.fs :as fs]
            [babashka.process :as process]
            [babashka.tasks :as tasks :refer [clojure]]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.java.shell :as sh]
            [clojure.string :as str]
            [clojure.stacktrace :as st]))

(def ^:dynamic *shell-env* {})

(defn shell [& args]
  (apply tasks/shell {:extra-env *shell-env*} args))

(defn get-env-from [cmd]
  (let [{:keys [exit out]} (sh/sh "sh" "-c" (str cmd "; printenv"))]
    (when (= 0 exit)
      (->> out
           str/split-lines
           (map #(vec (str/split % #"=" 2)))
           (filter #(= 2 (count %)))
           (into {})))))

(defn with-ssh-agent* [f]
  (if-let [env (and (fs/which "ssh-agent")
                    (not= 0 (:exit (sh/sh "ssh-add" "-l")))
                    (empty? *shell-env*)
                    (get-env-from "eval $(ssh-agent)"))]
    (binding [*shell-env* env]
      (try
        (println "Starting an ssh-agent session. If you set up `keychain`, you won't have to enter your password"
                 "each time you run this command: https://www.funtoo.org/Funtoo:Keychain")
        (shell "ssh-add")
        (f)
        (finally
         (sh/sh "ssh-agent" "-k" :env *shell-env*))))
    (f)))

(defmacro with-ssh-agent [& body]
  `(with-ssh-agent* (fn [] ~@body)))

(defmacro future-verbose [& body]
  `(future
     (try
       ~@body
       (catch Exception e#
         ;; st/print-stack-trace just prints Babashka's internal stack trace.
         (st/print-throwable e#)
         (println)))))

(defn new-secret [length]
  (let [buffer (byte-array length)]
    (.nextBytes (java.security.SecureRandom/getInstanceStrong) buffer)
    (.encodeToString (java.util.Base64/getEncoder) buffer)))

(defn generate-secrets
  "Prints new values to put in secrets.env."
  []
  (println "Put these in your secrets.env file:")
  (println)
  (println (str "export COOKIE_SECRET=" (new-secret 16)))
  (println (str "export JWT_SECRET=" (new-secret 32)))
  (println))

(def config
  (delay (:tasks (edn/read-string (slurp "config.edn")))))

(defn server [& args]
  (apply shell "ssh" (str "root@" (:biff.tasks/server @config)) args))

(defn trench [& args]
  (apply server "trench" "-p" "7888" "-e" args))

(defn windows? []
  (-> (System/getProperty "os.name")
      (str/lower-case)
      (str/includes? "windows")))

(defn local-tailwind-path []
  (if (windows?)
    "bin/tailwindcss.exe"
    "bin/tailwindcss"))

(defn tailwind-file []
  (let [os-name (str/lower-case (System/getProperty "os.name"))
        os-type (cond
                  (str/includes? os-name "windows") "windows"
                  (str/includes? os-name "linux") "linux"
                  :else "macos")
        arch (case (System/getProperty "os.arch")
               "amd64" "x64"
               "arm64")]
    (str "tailwindcss-" os-type "-" arch (when (= os-type "windows") ".exe"))))

(defn install-tailwind []
  (let [file (cond
               (:biff.tasks/tailwind-file @config)
               (:biff.tasks/tailwind-file @config)

               ;; Backwards compatibility.
               (:biff.tasks/tailwind-build @config)
               (str "tailwindcss-" (:biff.tasks/tailwind-build @config))

               :else
               (tailwind-file))
        url (str "https://github.com/tailwindlabs/tailwindcss/releases/latest/download/"
                 file)
        dest (io/file (local-tailwind-path))]
    (io/make-parents dest)
    (println "Downloading the latest version of Tailwind CSS...")
    (println (str "Auto-detected build: " file ". If that's incorrect, set :biff.tasks/tailwind-file in config.edn."))
    (println)
    (println "After the download finishes, you can avoid downloading Tailwind again for"
             "future projects if you copy it to your path, e.g. by running:")
    (println "  sudo cp" (local-tailwind-path) "/usr/local/bin/tailwindcss")
    (println)
    (io/copy (:body (curl/get url {:compressed false :as :stream})) dest)
    (.setExecutable dest true)))

(defn css
  "Generates the target/resources/public/css/main.css file.

  The logic for running and installing Tailwind is:

  1. If tailwindcss has been installed via npm, then `npx tailwindcss` will be
     used.

  2. Otherwise, if the tailwindcss standalone binary has been downloaded to
     ./bin/, that will be used.

  3. Otherwise, if the tailwindcss standalone binary has been installed to the
     path (e.g. /usr/local/bin/tailwindcss), that will be used.

  4. Otherwise, the tailwindcss standalone binary will be downloaded to ./bin/,
     and that will be used."
  [& args]
  (let [local-bin-installed (fs/exists? (local-tailwind-path))
        tailwind-cmd (cond
                      (= 0 (:exit (sh/sh "npm" "list" "tailwindcss"))) :npm
                      (and (fs/which "tailwindcss")
                           (not local-bin-installed)) :global-bin
                      :else :local-bin)]
    (when (and (= tailwind-cmd :local-bin) (not local-bin-installed))
      (install-tailwind))
    (apply shell (concat (case tailwind-cmd
                           :npm        ["npx" "tailwindcss"]
                           :global-bin [(str (fs/which "tailwindcss"))]
                           :local-bin  [(local-tailwind-path)])
                         ["-c" "resources/tailwind.config.js"
                          "-i" "resources/tailwind.css"
                          "-o" "target/resources/public/css/main.css"]
                         args))))

(defn run-args []
  (:biff.tasks/clj-args
   @config
   ;; For backwards compatibility
   ["-J-XX:-OmitStackTraceInFastThrow"
    "-M" "-m" (:biff.tasks/main-ns @config)
    "--port" "7888"
    "--middleware" "[cider.nrepl/cider-middleware,refactor-nrepl.middleware/wrap-refactor]"]))

;; Algorithm copied from Python's shlex.quote()
;; https://github.com/python/cpython/blob/db65a326a4022fbd43648858b460f52734faf1b5/Lib/shlex.py#L325
(defn shell-escape [s]
  (str \'
       (some-> s (str/replace "'" "'\"'\"'"))
       \'))

(defn run-cmd
  "Internal. Used by the server to start the app."
  []
  (let [commands (filter some?
                         ["mkdir -p target/resources"
                          (when (fs/exists? "package.json")
                            "npm install")
                          (when (fs/exists? "secrets.env")
                            ". ./secrets.env")
                          (->> (run-args)
                               (map shell-escape)
                               (str/join " ")
                               (str "clj "))])]
    (println "eval" (str/join " ; " commands))))

(defn secrets []
  (when (fs/exists? "secrets.env")
    (get-env-from ". ./secrets.env")))

(defn dev
  "Starts the app locally.

  After running, wait for the `System started` message. Connect your editor to
  nrepl port 7888. Whenever you save a file, Biff will:

   - Evaluate any changed Clojure files
   - Regenerate static HTML and CSS files
   - Run tests"
  [& args]
  (io/make-parents "target/resources/_")
  (when (fs/exists? "package.json")
    (shell "npm" "install"))
  (future-verbose (css "--watch"))
  (spit ".nrepl-port" "7888")
  (apply clojure {:extra-env (merge (secrets) {"BIFF_ENV" "dev"})}
         (concat args (run-args))))

(defn format
  "Formats the code with cljfmt."
  []
  (clojure
   "-Sdeps" (pr-str '{:deps {cljfmt/cljfmt {:mvn/version "0.8.2"}}})
   "-M" "-m" "cljfmt.main" "fix" "--indents" "cljfmt-indents.edn"))

(defn clean
  "Deletes generated files."
  []
  (fs/delete-tree "target"))

(defn post-receive
  "Deprecated."
  []
  nil)

(defn refresh
  "Reloads code and restarts the system via `clojure.tools.namespace.repl/refresh` (on the server)."
  []
  (trench "\"(com.biffweb/refresh)\""))

(defn restart
  "Restarts the app process via `systemctl restart app` (on the server)."
  []
  (with-ssh-agent
    (apply shell "ssh" (str "app@" (:biff.tasks/server @config))
           "clj" "-P" (run-args))
    (server "systemctl" "reset-failed" "app.service")
    (server "systemctl" "restart" "app")))

(defn- push-files-rsync []
  (let [{:biff.tasks/keys [server]} @config
        files (->> (:out (sh/sh "git" "ls-files"))
                   str/split-lines
                   (map #(str/replace % #"/.*" ""))
                   distinct
                   (concat ["config.edn"
                            "secrets.env"])
                   (filter fs/exists?))]
    (when-not (windows?)
      (fs/set-posix-file-permissions "config.edn" "rw-------")
      (when (fs/exists? "secrets.env")
        (fs/set-posix-file-permissions "secrets.env" "rw-------")))
    (->> (concat ["rsync" "--archive" "--verbose" "--include='**.gitignore'"
                  "--exclude='/.git'" "--filter=:- .gitignore" "--delete-after"]
                 files
                 [(str "app@" server ":")])
         (apply shell))))

(defn- push-files-git []
  (let [{:biff.tasks/keys [server deploy-to deploy-from deploy-cmd]} @config]
    (apply shell (concat ["scp"
                          "config.edn"]
                         (when (fs/exists? "secrets.env") ["secrets.env"])
                         [(str "app@" server ":")]))
    (time (if deploy-cmd
            (apply shell deploy-cmd)
            ;; For backwards compatibility
            (shell "git" "push" deploy-to deploy-from)))))

(defn- push-files []
  (if (fs/which "rsync")
    (push-files-rsync)
    (push-files-git)))

(defn- push-css []
  (if (fs/which "rsync")
    (do
      (shell "rsync" "--relative"
             "target/resources/public/css/main.css"
             (str "app@" (:biff.tasks/server @config) ":"))
      (println "target/resources/public/css/main.css"))
    (do
      (shell "ssh" (str "app@" server) "mkdir" "-p" "target/resources/public/css/")
      (shell "scp" "target/resources/public/css/main.css"
             (str "app@" server ":target/resources/public/css/main.css")))))

(defn soft-deploy
  "Pushes code to the server and evaluates changed files.

  Uploads config and code to the server (see `deploy`), then `eval`s any
  changed files and regenerates HTML and CSS files. Does not refresh or
  restart."
  []
  (with-ssh-agent
    (let [{:biff.tasks/keys [soft-deploy-fn on-soft-deploy]} @config
          css-proc (future (css "--minify"))]
      (push-files)
      (trench (or on-soft-deploy
                  ;; backwards compatibility
                  (str "\"(" soft-deploy-fn " @com.biffweb/system)\"")))
      (println "waiting for css")
      @css-proc
      (println "done building css")
      (push-css))))

(defn deploy
  "Pushes code to the server and restarts the app.

  Uploads config (config.edn and secrets.env) and code to the server, using
  `rsync` if it's available, and `git push` by default otherwise. Then restarts
  the app.

  You must set up a server first. See https://biffweb.com/docs/reference/production/"
  []
  (with-ssh-agent
    (css "--minify")
    (push-files)
    (push-css)
    (restart)))

(defn auto-soft-deploy []
  (soft-deploy)
  (let [last-ran (atom (System/nanoTime))
        p (process/process ["fswatch" "-orl" "0.1" "--event=Updated" "--event=Removed" "--allow-overflow" "."]
                           {:err :inherit})]
    (with-open [rdr (io/reader (:out p))]
      (doseq [l (line-seq rdr)]
        (when (< (Math/pow 10 9) (- (System/nanoTime) @last-ran))
          (soft-deploy))
        (reset! last-ran (System/nanoTime))))))

(defn logs
  "Tails the server's application logs."
  [& [n-lines]]
  (server "journalctl" "-u" "app" "-f" "-n" (or n-lines "300")))

(defn prod-repl
  "Opens an SSH tunnel so you can connect to the server via nREPL."
  []
  (println "Connect to nrepl port 7888")
  (spit ".nrepl-port" "7888")
  (shell "ssh" "-NL" "7888:localhost:7888" (str "root@" (:biff.tasks/server @config))))

(defn prod-dev
  "Runs the auto-soft-deploy command whenever a file is modified. Also runs prod-repl and logs."
  []
  (when-not (fs/which "rsync")
    (binding [*out* *err*]
      (println "`rsync` command not found. Please install it.")
      (println "Alternatively, you can deploy without downtime by running `git add .; git commit; bb soft-deploy`"))
    (System/exit 1))
  (when-not (fs/which "fswatch")
    (println "`fswatch` command not found. Please install it: https://emcrisostomo.github.io/fswatch/getting.html")
    (println " - Ubuntu: sudo apt install fswatch")
    (println " - Mac: brew install fswatch")
    (System/exit 2))
  (with-ssh-agent
    (future-verbose (prod-repl))
    (future-verbose (auto-soft-deploy))
    (logs)))
