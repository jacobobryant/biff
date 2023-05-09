(ns com.biffweb.tasks
  (:require [babashka.curl :as curl]
            [babashka.fs :as fs]
            [babashka.process :as process]
            [babashka.tasks :refer [shell clojure]]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.java.shell :as sh]
            [clojure.string :as str]
            [clojure.stacktrace :as st]))

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

(defn shell-some [& args]
  (apply shell (filter some? args)))

(defn windows? []
  (-> (System/getProperty "os.name")
      (str/lower-case)
      (str/includes? "windows")))

(defn local-tailwind-path []
  (if (windows?)
    "bin/tailwindcss.exe"
    "bin/tailwindcss"))

(defn tailwind-path []
  (or (some-> (fs/which "tailwindcss") str)
      (local-tailwind-path)))

(def config
  (delay (:tasks (edn/read-string (slurp "config.edn")))))

(defn server [& args]
  (apply shell "ssh" (str "root@" (:biff.tasks/server @config)) args))

(defn trench [& args]
  (apply server "trench" "-p" "7888" "-e" args))

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
    (println)
    (println (str "Auto-detected build: " file ". If that's incorrect, set :biff.tasks/tailwind-file in config.edn."))
    (println)
    (println "After the download finishes, you can avoid downloading Tailwind again for"
             "future projects if you copy it to your path, e.g. by running:")
    (println "  sudo cp bin/tailwindcss /usr/local/bin/")
    (println)
    (io/copy (:body (curl/get url {:compressed false :as :stream})) dest)
    (.setExecutable dest true)))

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

(defn css [& args]
  (apply shell
         (concat (if (fs/exists? (tailwind-path))
                   [(tailwind-path)]
                   ["npx" "tailwindcss"])
                 ["-c" "resources/tailwind.config.js"
                  "-i" "resources/tailwind.css"
                  "-o" "target/resources/public/css/main.css"]
                 args)))

(defn secrets []
  (when (fs/exists? "secrets.env")
    (->> (slurp "secrets.env")
         str/split-lines
         (keep (fn [s]
                 (some-> s
                         (str/replace #"^export\s+" "")
                         (str/replace #"#.*" "")
                         str/trim
                         not-empty)))
         (filter #(str/includes? % "="))
         (map #(vec (str/split % #"=" 2)))
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
  (when-not (fs/exists? (tailwind-path))
    (install-tailwind))
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
  "Internal. Runs on the server after a git push."
  []
  (apply clojure "-P" (run-args))
  (shell "sudo" "systemctl" "reset-failed" "app.service")
  (shell "sudo" "systemctl" "restart" "app"))

(defn deploy
  "Deploys the app via `git push`.

  Copies config.edn to the server, deploys code via `git push`, and
  restarts the app process on the server (via git push hook). You must set up a
  server first. See https://biffweb.com/docs/reference/production/."
  []
  (let [{:biff.tasks/keys [server deploy-to deploy-from deploy-cmd]} @config]
    (css "--minify")
    (if (windows?)
      (do
        (shell-some "scp"
                    "config.edn"
                    (when (fs/exists? "secrets.env") "secrets.env")
                    (str "app@" server ":"))
        (shell "ssh" (str "app@" server) "mkdir" "-p" "target/resources/public/css/")
        (shell "scp" "target/resources/public/css/main.css"
               (str "app@" server ":target/resources/public/css/main.css")))
      (do
        (fs/set-posix-file-permissions "config.edn" "rw-------")
        (when (fs/exists? "secrets.env")
          (fs/set-posix-file-permissions "secrets.env" "rw-------"))
        (shell-some "rsync" "-a" "--relative"
                    "config.edn"
                    (when (fs/exists? "secrets.env") "secrets.env")
                    "target/resources/public/css/main.css"
                    (str "app@" server ":"))))
    (time (if deploy-cmd
            (apply shell deploy-cmd)
            ;; For backwards compatibility
            (shell "git" "push" deploy-to deploy-from)))))

(defn soft-deploy
  "Hotswaps modified code into the server.

  `rsync`s config and code to the server, then `eval`s any changed files and
  regenerates HTML and CSS files. Does not refresh or restart."
  []
  (when-not (fs/which "rsync")
    (binding [*out* *err*]
      (println "`rsync` command not found. Please install it."))
    (System/exit 1))
  (future-verbose
   (css "--minify")
   (shell "rsync" "--relative" "--info=name1"
          "target/resources/public/css/main.css"
          (str "app@" (:biff.tasks/server @config) ":")))
  (let [{:biff.tasks/keys [server soft-deploy-fn on-soft-deploy]} @config
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
    (->> (concat ["rsync" "-a" "--info=name1" "--include='**.gitignore'"
                  "--exclude='/.git'" "--filter=:- .gitignore" "--delete-after"]
                 files
                 [(str "app@" server ":")])
         (apply shell))
    (trench (or on-soft-deploy
                ;; backwards compatibility
                (str "\"(" soft-deploy-fn " @com.biffweb/system)\"")))))

(defn refresh
  "Reloads code and restarts the system via `clojure.tools.namespace.repl/refresh` (on the server)."
  []
  (trench "\"(com.biffweb/refresh)\""))

(defn restart
  "Restarts the app process via `systemctl restart app` (on the server)."
  []
  (server "systemctl" "reset-failed" "app.service")
  (server "systemctl" "restart" "app"))

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
      (println "`rsync` command not found. Please install it."))
    (System/exit 1))
  (when-not (fs/which "fswatch")
    (println "`fswatch` command not found. Please install it: https://emcrisostomo.github.io/fswatch/getting.html")
    (println " - Ubuntu: sudo apt install fswatch")
    (println " - Mac: brew install fswatch")
    (System/exit 2))
  (future-verbose (prod-repl))
  (future-verbose (auto-soft-deploy))
  (logs))
