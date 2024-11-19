(ns com.biffweb.tasks
  "A collection of tasks used by Biff projects."
  (:refer-clojure :exclude [future])
  (:require [com.biffweb.task-runner :refer [run-task]]
            [com.biffweb.tasks.lazy.clojure.java.io :as io]
            [com.biffweb.tasks.lazy.clojure.java.shell :as sh]
            [com.biffweb.tasks.lazy.clojure.string :as str]
            [com.biffweb.tasks.lazy.babashka.fs :as fs]
            [com.biffweb.tasks.lazy.babashka.process :as process]
            [com.biffweb.tasks.lazy.com.biffweb.config :as config]
            [com.biffweb.tasks.lazy.clojure.stacktrace :as st]
            [com.biffweb.tasks.lazy.hato.client :as hato]
            [com.biffweb.tasks.lazy.nrepl.cmdline :as nrepl-cmd]
            [com.biffweb.tasks.lazy.nextjournal.beholder :as beholder]
            [com.biffweb.tasks.lazy.clojure.tools.build.api :as clj-build])
  (:import [java.util Timer TimerTask]))

;; https://gist.github.com/oliyh/0c1da9beab43766ae2a6abc9507e732a
(defn- debounce
  ([f] (debounce f 1000))
  ([f timeout]
   (let [timer (Timer.)
         task (atom nil)]
     (with-meta
      (fn [& args]
        (when-let [t ^TimerTask @task]
          (.cancel t))
        (let [new-task (proxy [TimerTask] []
                         (run []
                           (apply f args)
                           (reset! task nil)
                           (.purge timer)))]
          (reset! task new-task)
          (.schedule timer new-task timeout)))
      {:task-atom task}))))

(defmacro future [& body]
  `(clojure.core/future
     (try
       ~@body
       (catch Exception e#
         (binding [*err* *out*]
           (st/print-stack-trace e#))))))

(def ^:private ^:dynamic *shell-env* nil)

(defn- windows? []
  (-> (System/getProperty "os.name")
      (str/lower-case)
      (str/includes? "windows")))

(defn- shell
  "Difference between this and clojure.java.shell/sh:

   - inherits std{in,out,err}
   - throws on non-zero exit code
   - puts *shell-env* in the environment"
  [& args]
  (apply process/shell {:extra-env *shell-env*} args))

(defn- sh-success? [& args]
  (try
    (= 0 (:exit (apply sh/sh args)))
    (catch Exception _
      false)))

(defn- get-env-from [cmd]
  (let [{:keys [exit out]} (sh/sh "sh" "-c" (str cmd "; printenv"))]
    (when (= 0 exit)
      (->> out
           str/split-lines
           (map #(vec (str/split % #"=" 2)))
           (filter #(= 2 (count %)))
           (into {})))))

(defn- with-ssh-agent* [{:keys [biff.tasks/skip-ssh-agent]} f]
  (if-let [env (and (not skip-ssh-agent)
                    (fs/which "ssh-agent")
                    (not (sh-success? "ssh-add" "-l"))
                    (nil? *shell-env*)
                    (if (windows?)
                      {}
                      (get-env-from "eval $(ssh-agent)")))]
    (binding [*shell-env* env]
      (try
        (try
          (shell "ssh-add")
          (println "Started an ssh-agent session. If you set up `keychain`, you won't have to enter your password"
                   "each time you run this command: https://www.funtoo.org/Funtoo:Keychain")
          (catch Exception e
            (binding [*out* *err*]
              (st/print-stack-trace e)
              (println "\nssh-add failed. You may have to enter your password multiple times. You can avoid this if you set up `keychain`:"
                       "https://www.funtoo.org/Funtoo:Keychain"))))
        (f)
        (finally
          (sh/sh "ssh-agent" "-k" :env *shell-env*))))
    (f)))

(defmacro with-ssh-agent [ctx & body]
  `(with-ssh-agent* ~ctx (fn [] ~@body)))

(defn- new-secret [length]
  (let [buffer (byte-array length)]
    (.nextBytes (java.security.SecureRandom/getInstanceStrong) buffer)
    (.encodeToString (java.util.Base64/getEncoder) buffer)))

(defn- ssh-run [{:keys [biff.tasks/server]} & args]
  (apply shell "ssh" (str "app@" server) args))

(defn- local-bun-path []
  (some-> (fs/which "bun") str))

(defn- install-js-deps-cmd []
  (cond
    (fs/exists? "bun.lockb") "bun install"
    :else                    "npm install"))

(defn- local-tailwind-path []
  (if (windows?)
    "bin/tailwindcss.exe"
    "bin/tailwindcss"))

(defn- infer-tailwind-file []
  (let [os-name (str/lower-case (System/getProperty "os.name"))
        os-type (cond
                  (str/includes? os-name "windows") "windows"
                  (str/includes? os-name "linux") "linux"
                  :else "macos")
        arch (case (System/getProperty "os.arch")
               ("amd64" "x86_64") "x64"
               "arm64")]
    (str "tailwindcss-" os-type "-" arch (when (= os-type "windows") ".exe"))))

(defn- push-files-rsync [{:biff.tasks/keys [server deploy-untracked-files]}]
  (let [files (->> (:out (sh/sh "git" "ls-files"))
                   str/split-lines
                   (map #(str/replace % #"/.*" ""))
                   distinct
                   (concat deploy-untracked-files)
                   (filter fs/exists?))]
    (when (and (not (windows?)) (fs/exists? "config.env"))
      (fs/set-posix-file-permissions "config.env" "rw-------"))
    (->> (concat ["rsync" "--archive" "--verbose" "--relative" "--include='**.gitignore'"
                  "--exclude='/.git'" "--filter=:- .gitignore" "--delete-after" "--protocol=29"]
                 files
                 [(str "app@" server ":")])
         (apply shell))))

(defn- push-files-git [{:biff.tasks/keys [deploy-cmd
                                          git-deploy-cmd
                                          deploy-from
                                          deploy-to
                                          deploy-untracked-files
                                          server]}]
  (when-some [files (not-empty (filterv fs/exists? deploy-untracked-files))]
    (when-some [dirs (not-empty (keep (comp not-empty fs/parent) files))]
      (apply shell "ssh" (str "app@" server) "mkdir" "-p" dirs))
    (doseq [file files]
      (shell "scp" file (str "app@" server ":" file))))
  ;; deploy-cmd, deploy-from, and deploy-to are all deprecated (but still supported for backwards compatibility)
  (if-some [git-deploy-cmd (or git-deploy-cmd deploy-cmd)]
    (apply shell git-deploy-cmd)
    (shell "git" "push" deploy-to deploy-from)))

(defn- push-files [{:keys [biff.tasks/deploy-with] :as ctx}]
  (let [deploy-with (or deploy-with
                        (if (fs/which "rsync")
                          :rsync
                          :git))]
    (case deploy-with
      :rsync (push-files-rsync ctx)
      :git (push-files-git ctx)
      (binding [*out* *err*]
        (println "Unrecognized config option `:biff.tasks/deploy-with " deploy-with "`. Valid options are "
                 ":rsync and :git")
        (System/exit 2)))))

(defn- auto-soft-deploy [{:biff.tasks/keys [watch-dirs]
                          :or {watch-dirs ["src" "dev" "resources" "test"]}
                          :as ctx}]
  (run-task "soft-deploy")
  (apply beholder/watch
         (debounce (fn [_]
                     (run-task "soft-deploy"))
                   500)
         watch-dirs))

(def ^:private config (delay (config/use-aero-config {:biff.config/skip-validation true})))

;;;; TASKS =====================================================================

(defn clean
  "Deletes generated files"
  []
  (clj-build/delete {:path "target"}))

(defn install-tailwind
  "Downloads a Tailwind binary to bin/tailwindcss."
  [& [file]]
  (let [{:biff.tasks/keys [tailwind-build]} @config
        [file inferred] (or (when file
                              [file false])
                            ;; Backwards compatibility.
                            (when tailwind-build
                              [(str "tailwindcss-" tailwind-build) false])
                            [(infer-tailwind-file) true])
        url (str "https://github.com/tailwindlabs/tailwindcss/releases/latest/download/"
                 file)
        dest (io/file (local-tailwind-path))]
    (io/make-parents dest)
    (println "Downloading the latest version of " file "...")
    (when inferred
      (println "If that's the wrong file, run `clj -M:dev install-tailwind <correct file>`"))
    (println)
    (println "After the download finishes, you can avoid downloading Tailwind again for"
             "future projects if you copy it to your path, e.g. by running:")
    (println "  sudo cp" (local-tailwind-path) "/usr/local/bin/tailwindcss")
    (println)
    (io/copy (:body (hato/get url {:as :stream :http-client {:redirect-policy :normal}})) dest)
    (.setExecutable dest true)))

(defn- bun-pkg-installed? [package-name]
  (and (fs/which "bun")
       (str/includes? (:out (sh/sh "bun" "pm" "ls"))
                      package-name)))

(defn- tailwind-installation-info []
  (let [local-bin-installed (fs/exists? (local-tailwind-path))]
    {:local-bin-installed local-bin-installed
     :tailwind-cmd
     (cond
       (bun-pkg-installed? "tailwindcss")                       :bun
       (sh-success? "npm" "list" "tailwindcss")                 :npm
       (and (fs/which "tailwindcss") (not local-bin-installed)) :global-bin
       :else                                                    :local-bin)}))

(defn css
  "Generates the target/resources/public/css/main.css file.

   The logic for running and installing Tailwind is:

   1. If tailwindcss has been installed via npm or bun, then that installation
      will be used.

   2. Otherwise, if the tailwindcss standalone binary has been downloaded to
      ./bin/, that will be used.

   3. Otherwise, if the tailwindcss standalone binary has been installed to the
      path (e.g. /usr/local/bin/tailwindcss), that will be used.

   4. Otherwise, the tailwindcss standalone binary will be downloaded to ./bin/,
      and that will be used."
  [& tailwind-args]
  (let [{:biff.tasks/keys [css-output] :as ctx} @config
        {:keys [local-bin-installed tailwind-cmd]} (tailwind-installation-info)]
    (when (and (= tailwind-cmd :local-bin) (not local-bin-installed))
      (run-task "install-tailwind"))
    (when (= tailwind-cmd :local-bin)
      ;; This normally will be handled by install-tailwind, but we set it here in case that function
      ;; was interrupted. Assuming the download was incomplete, the 139 exit code (segfault) handler will be
      ;; triggered below. I've also had a report of exit code 137 (sigkill) being triggered.
      (.setExecutable (io/file (local-tailwind-path)) true))
    (try
      (apply shell (concat (case tailwind-cmd
                             :npm        ["npx" "tailwindcss"]
                             :bun        ["bunx" "tailwindcss"]
                             :global-bin [(str (fs/which "tailwindcss"))]
                             :local-bin  [(local-tailwind-path)])
                           ["-c" "resources/tailwind.config.js"
                            "-i" "resources/tailwind.css"
                            "-o" css-output]
                           tailwind-args))
      (catch Exception e
        (if (and (#{137 139} (:exit (ex-data e)))
                 (#{:local-bin :global-bin} tailwind-cmd))
          (binding [*out* *err*]
            (println "It looks like your Tailwind installation is corrupted. Try deleting it and running this command again:")
            (println)
            (println "  rm" (if (= tailwind-cmd :local-bin)
                              (local-tailwind-path)
                              (str (fs/which "tailwindcss"))))
            (println))
          (throw e))))))

(defn dev
  "Starts the app locally.

   After running, wait for the `System started` message. Connect your editor to
   nrepl port 7888 (by default). Whenever you save a file, Biff will:

   - Evaluate any changed Clojure files
   - Regenerate static HTML and CSS files
   - Run tests"
  []
  (if-not (fs/exists? "target/resources")
    ;; This is an awful hack. We have to run the app in a new process, otherwise
    ;; target/resources won't be included in the classpath. Downside of not
    ;; using bb tasks anymore -- no longer have a lightweight parent process
    ;; that can create the directory before starting the JVM.
    (do
      (io/make-parents "target/resources/_")
      (shell "clj" "-M:dev" "dev"))
    (let [{:keys [biff.tasks/main-ns biff.nrepl/port] :as ctx} @config]
      (when-not (fs/exists? "config.env")
        (run-task "generate-config"))
      (when (fs/exists? "package.json")
        (shell (install-js-deps-cmd)))
      (let [{:keys [local-bin-installed tailwind-cmd]} (tailwind-installation-info)]
        (when (and (= tailwind-cmd :local-bin) (not local-bin-installed))
          (run-task "install-tailwind")))
      (future (run-task "css" "--watch"))
      (spit ".nrepl-port" port)
      ((requiring-resolve (symbol (str main-ns) "-main"))))))

(defn uberjar
  "Compiles the app into an Uberjar.

   Options:

     --no-clean
            Don't call the `clean` task before building the Uberjar."
  [& args]
  (let [{:biff.tasks/keys [main-ns generate-assets-fn] :as ctx} @config
        class-dir "target/jar/classes"
        basis (clj-build/create-basis {:project "deps.edn"})
        uber-file "target/jar/app.jar"
        no-clean (some #{"--no-clean"} args)]
    (when-not no-clean
      (println "Cleaning...")
      (run-task "clean"))
    (println "Generating CSS...")
    (run-task "css" "--minify")
    (println "Calling" generate-assets-fn "...")
    ((requiring-resolve generate-assets-fn) ctx)
    (println "Compiling...")
    (clj-build/compile-clj {:basis basis
                            :ns-compile [main-ns]
                            :class-dir class-dir})
    (println "Building uberjar...")
    (clj-build/copy-dir {:src-dirs ["resources" "target/resources"]
                         :target-dir class-dir})
    (clj-build/uber {:class-dir class-dir
                     :uber-file uber-file
                     :basis basis
                     :main main-ns})
    (println "Done. Uberjar written to" uber-file)
    (println (str "Test with `BIFF_PROFILE=dev java -jar " uber-file "`"))))

(defn generate-secrets
  "Prints new secrets to put in config.env."
  []
  (println "Put these in your config.env file:")
  (println)
  (println (str "COOKIE_SECRET=" (new-secret 16)))
  (println (str "JWT_SECRET=" (new-secret 32)))
  (println))

(defn generate-config
  "Creates a new config.env file if one doesn't already exist."
  []
  (if (fs/exists? "config.env")
    (binding [*out* *err*]
      (println "config.env already exists. If you want to generate a new file, run `mv config.env config.env.backup` first.")
      (System/exit 3))
    (let [contents (slurp (io/resource "config.template.env"))
          contents (str/replace contents
                                #"\{\{\s+new-secret\s+(\d+)\s+\}\}"
                                (fn [[_ n]]
                                  (new-secret (parse-long n))))]
      (spit "config.env" contents)
      (println "New config generated and written to config.env."))))

(defn restart
  "Restarts the app process via `systemctl restart app` (on the server)."
  []
  (ssh-run @config "sudo systemctl reset-failed app.service; sudo systemctl restart app"))

(defn soft-deploy
  "Pushes code to the server and evaluates changed files.

   1. Builds css
   2. Uploads files
   3. `eval`s any changed files
   4. Regenerates static html files

   Does not refresh or restart, so there isn't any downtime."
  []
  (let [{:biff.tasks/keys [soft-deploy-fn on-soft-deploy]
         :keys [biff.nrepl/port]
         :as ctx} @config]
    (with-ssh-agent ctx
      (run-task "css" "--minify")
      (push-files ctx)
      (ssh-run ctx "trench"
               "-p" port
               "-e" (or on-soft-deploy
                        ;; backwards compatibility
                        (str "\"(" soft-deploy-fn " @com.biffweb/system)\""))))))

(defn deploy
  "Pushes code to the server and restarts the app.

   Uploads config and code to the server, using `rsync` if it's available, and
   `git push` otherwise. Then restarts the app.

   You must set up a server first. See https://biffweb.com/docs/reference/production/"
  []
  (with-ssh-agent @config
    (run-task "css" "--minify")
    (push-files @config)
    (run-task "restart")))

(defn logs
  "Tails the server's application logs."
  ([]
   (logs "300"))
  ([n-lines]
   (ssh-run @config "journalctl" "-u" "app" "-f" "-n" n-lines)))

(defn prod-repl
  "Opens an SSH tunnel so you can connect to the server via nREPL."
  []
  (let [{:keys [biff.tasks/server biff.nrepl/port]} @config]
    (println "Connect to nrepl port" port)
    (spit ".nrepl-port" port)
    (shell "ssh" "-NL" (str port ":localhost:" port) (str "app@" server))))

(defn prod-dev
  "Runs the soft-deploy task whenever a file is modified. Also runs prod-repl and logs."
  []
  (when-not (fs/which "rsync")
    (binding [*out* *err*]
      (println "`rsync` command not found. Please install it.")
      (println "Alternatively, you can deploy without downtime by running `git add .; git commit; bb soft-deploy`"))
    (System/exit 1))
  (with-ssh-agent @config
    (auto-soft-deploy @config)
    (future (run-task "prod-repl"))
    (run-task "logs")))

(defn nrepl
  "Starts an nrepl server without starting up the application."
  []
  (let [{:biff.nrepl/keys [port args]} @config]
    (spit ".nrepl-port" port)
    (apply nrepl-cmd/-main args)))

(def tasks
  {"clean"            #'clean
   "css"              #'css
   "deploy"           #'deploy
   "dev"              #'dev
   "nrepl"            #'nrepl
   "generate-secrets" #'generate-secrets
   "generate-config"  #'generate-config
   "logs"             #'logs
   "prod-dev"         #'prod-dev
   "prod-repl"        #'prod-repl
   "restart"          #'restart
   "soft-deploy"      #'soft-deploy
   "uberjar"          #'uberjar
   "install-tailwind" #'install-tailwind})
