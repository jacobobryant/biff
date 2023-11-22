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

(def config
  (delay (:tasks (edn/read-string (slurp "config.edn")))))

(def ^:dynamic *shell-env* nil)

(defn windows? []
  (-> (System/getProperty "os.name")
      (str/lower-case)
      (str/includes? "windows")))

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

(defn sh-success? [& args]
  (try
    (= 0 (:exit (apply sh/sh args)))
    (catch Exception _
      false)))

(defn host-ssh-config [host]
  "Runs `ssh -G host` and parses it into a map"
  (let [;; NB: `ssh -G host` rarely returns an error, for example
        ;;     there's no error if you give a host that's not
        ;;     mentioned in your configs.
        {:keys [exit out err]} (sh/sh "ssh" "-G" host :in-enc "UTF-8")]
    (when (not= exit 0)
      (throw (str "ssh -G " host " returned non-zero exit code " exit " stderr follows: \n" err)))
    (into {} (map #(str/split % #" " 2) (str/split-lines out)))))

(defn ssh-agent-disabled-for-host? [host]
  "Returns true if the user has disabled ssh-agent in their SSH
  configuration"

  (let [{:as config :strs [identitiesonly addkeystoagent]} (host-ssh-config host)]
    ;; From SSH_CONFIG(5):
    ;;
    ;;      IdentitiesOnly
    ;;           Specifies that ssh(1) should only use the configured
    ;;           authentication identity and certificate files (either the default
    ;;           files, or those explicitly configured in the ssh_config files or
    ;;           passed on the ssh(1) command-line), even if ssh-agent(1) or a
    ;;           PKCS11Provider or SecurityKeyProvider offers more identities.
    ;;           The argument to this keyword must be yes or no (the default).
    ;;           This option is intended for situations where ssh-agent offers
    ;;           many different identities.
    ;;
    ;; and
    ;;
    ;;      AddKeysToAgent
    ;;           Specifies whether keys should be automatically added to a running
    ;;           ssh-agent(1).  If this option is set to yes and a key is loaded
    ;;           from a file, the key and its passphrase are added to the agent
    ;;           with the default lifetime, as if by ssh-add(1).  If this option
    ;;           is set to ask, ssh(1) will require confirmation using the
    ;;           SSH_ASKPASS program before adding a key (see ssh-add(1) for
    ;;           details).  If this option is set to confirm, each use of the key
    ;;           must be confirmed, as if the -c option was specified to
    ;;           ssh-add(1).  If this option is set to no, no keys are added to
    ;;           the agent.  Alternately, this option may be specified as a time
    ;;           interval using the format described in the TIME FORMATS section
    ;;           of sshd_config(5) to specify the key's lifetime in ssh-agent(1),
    ;;           after which it will automatically be removed.  The argument must
    ;;           be no (the default), yes, confirm (optionally followed by a time
    ;;           interval), ask or a time interval."
    (and (= identitiesonly "yes") (= addkeystoagent "false"))))

(defn with-ssh-agent* [f]
  (if-let [env (and (not (ssh-agent-disabled-for-host? (:biff.tasks/server @config)))
                    (not (:biff.tasks/skip-ssh-agent @config))
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
              (st/print-throwable e)
              (println "\nssh-add failed. You may have to enter your password multiple times. You can avoid this if you set up `keychain`:"
                       "https://www.funtoo.org/Funtoo:Keychain"))))
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

(defn server [& args]
  (apply shell "ssh" (str "root@" (:biff.tasks/server @config)) args))

(defn trench [& args]
  (apply server "trench" "-p" "7888" "-e" args))

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

(def css-output "target/resources/public/css/main.css")

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
                       (sh-success? "npm" "list" "tailwindcss") :npm
                       (and (fs/which "tailwindcss")
                            (not local-bin-installed)) :global-bin
                       :else :local-bin)]
    (when (and (= tailwind-cmd :local-bin) (not local-bin-installed))
      (install-tailwind))
    (when (= tailwind-cmd :local-bin)
      ;; This normally will be handled by install-tailwind, but we set it here
      ;; in case that function was interrupted. Assuming the download was
      ;; incomplete, the 139 exit code handler will be triggered below.
      (.setExecutable (io/file (local-tailwind-path)) true))
    (try
      (apply shell (concat (case tailwind-cmd
                             :npm        ["npx" "tailwindcss"]
                             :global-bin [(str (fs/which "tailwindcss"))]
                             :local-bin  [(local-tailwind-path)])
                           ["-c" "resources/tailwind.config.js"
                            "-i" "resources/tailwind.css"
                            "-o" css-output]
                           args))
      (catch Exception e
        (when (and (= 139 (:babashka/exit (ex-data e)))
                   (#{:local-bin :global-bin} tailwind-cmd))
          (binding [*out* *err*]
            (println "It looks like your Tailwind installation is corrupted. Try deleting it and running this command again:")
            (println)
            (println "  rm" (if (= tailwind-cmd :local-bin)
                              (local-tailwind-path)
                              (str (fs/which "tailwindcss"))))
            (println)))
        (throw e)))))

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

;; Algorithm adapted from dotenv-java:
;; https://github.com/cdimascio/dotenv-java/blob/master/src/main/java/io/github/cdimascio/dotenv/internal/DotenvParser.java
;; Wouldn't hurt to take a more thorough look at Ruby dotenv's algorithm:
;; https://github.com/bkeepers/dotenv/blob/master/lib/dotenv/parser.rb
(defn parse-env-var [line]
  (let [line (str/trim line)
        [_ _ k v] (re-matches #"^\s*(export\s+)?([\w.\-]+)\s*=\s*(['][^']*[']|[\"][^\"]*[\"]|[^#]*)?\s*(#.*)?$"
                              line)]
    (when-not (or (str/starts-with? line "#")
                  (str/starts-with? line "////")
                  (empty? v))
      (let [v (str/trim v)
            v (if (or (re-matches #"^\".*\"$" v)
                      (re-matches #"^'.*'$" v))
                (subs v 1 (dec (count v)))
                v)]
        [k v]))))

(comment
  [(parse-env-var "FOO=BAR")
   (parse-env-var "FOO='BAR'")
   (parse-env-var "FOO=\"BAR\"")
   (parse-env-var "FOO=\"BAR\" # hello")
   (parse-env-var " export FOO=\"BAR\" # hello")
   (parse-env-var "# FOO=\"BAR\"")
   (parse-env-var "   ")])

(defn secrets []
  (cond
    (not (fs/exists? "secrets.env"))
    nil

    (not (windows?))
    (get-env-from ". ./secrets.env")

    :else
    (->> (slurp "secrets.env")
         str/split-lines
         (keep parse-env-var)
         (into {}))))

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
  (binding [*out* *err*]
    (println "This command has been removed. Instead, you can connect your editor to the server with"
             "`bb prod-dev` or `bb prod-repl`, then call the (refresh) function from your editor.")))

(defn restart
  "Restarts the app process via `systemctl restart app` (on the server)."
  []
  (server "systemctl reset-failed app.service; systemctl restart app"))

(defn- push-files-rsync []
  (let [{:biff.tasks/keys [server]} @config
        files (->> (:out (sh/sh "git" "ls-files"))
                   str/split-lines
                   (map #(str/replace % #"/.*" ""))
                   distinct
                   (concat ["config.edn"
                            "secrets.env"
                            css-output])
                   (filter fs/exists?))]
    (when-not (windows?)
      (fs/set-posix-file-permissions "config.edn" "rw-------")
      (when (fs/exists? "secrets.env")
        (fs/set-posix-file-permissions "secrets.env" "rw-------")))
    (->> (concat ["rsync" "--archive" "--verbose" "--relative" "--include='**.gitignore'"
                  "--exclude='/.git'" "--filter=:- .gitignore" "--delete-after"]
                 files
                 [(str "app@" server ":")])
         (apply shell))))

(defn- push-files-git []
  (let [{:biff.tasks/keys [server deploy-to deploy-from deploy-cmd]} @config]
    (apply shell (concat ["scp" "config.edn"]
                         (when (fs/exists? "secrets.env") ["secrets.env"])
                         [(str "app@" server ":")]))
    (when (fs/exists? css-output)
      (shell "ssh" (str "app@" server) "mkdir" "-p" "target/resources/public/css/")
      (shell "scp" css-output (str "app@" server ":" css-output)))
    (time (if deploy-cmd
            (apply shell deploy-cmd)
            ;; For backwards compatibility
            (shell "git" "push" deploy-to deploy-from)))))

(defn- push-files []
  (if (fs/which "rsync")
    (push-files-rsync)
    (push-files-git)))

(defn soft-deploy
  "Pushes code to the server and evaluates changed files.

  Uploads config and code to the server (see `deploy`), then `eval`s any
  changed files and regenerates HTML and CSS files. Does not refresh or
  restart."
  []
  (with-ssh-agent
    (let [{:biff.tasks/keys [soft-deploy-fn on-soft-deploy]} @config]
      (css "--minify")
      (push-files)
      (trench (or on-soft-deploy
                  ;; backwards compatibility
                  (str "\"(" soft-deploy-fn " @com.biffweb/system)\""))))))

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
