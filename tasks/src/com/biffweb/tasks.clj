(ns com.biffweb.tasks
  (:require [babashka.curl :as curl]
            [babashka.fs :as fs]
            [babashka.tasks :refer [shell clojure]]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.java.shell :as sh]
            [clojure.string :as str]
            [babashka.fs :as fs]
            [babashka.process :as process]))

(defn windows? []
  (not (fs/which "uname")))

(defn tailwind-path []
  (if (windows?)
    "bin/tailwindcss.exe"
    "bin/tailwindcss"))

(def config
  (delay (:tasks (edn/read-string (slurp "config.edn")))))

(defn server [& args]
  (apply shell "ssh" (str "root@" (:biff.tasks/server @config)) args))

(defn trench [& args]
  (apply server "trench" "-p" "7888" "-e" args))

(defn install-tailwind []
  (let [build (or (:biff.tasks/tailwind-build @config)
                  (if (windows?)
                    "windows-x64.exe"
                    (str (if (= (str/trim (:out (sh/sh "uname"))) "Linux")
                           "linux"
                           "macos")
                         "-"
                         (if (= (str/trim (:out (sh/sh "uname" "-m"))) "x86_64")
                           "x64"
                           "arm64"))))
        file (str "tailwindcss-" build)
        url (str "https://github.com/tailwindlabs/tailwindcss/releases/latest/download/"
                 file)
        dest (io/file (tailwind-path))]
    (io/make-parents dest)
    (println "Downloading the latest version of Tailwind CSS...")
    (io/copy (:body (curl/get url {:compressed false :as :stream})) dest)
    (.setExecutable dest true)))

(defn run-args []
  ["-J-XX:-OmitStackTraceInFastThrow"
   "-M" "-m" (:biff.tasks/main-ns @config)
   "--port" "7888"
   "--middleware" "[cider.nrepl/cider-middleware,refactor-nrepl.middleware/wrap-refactor]"])

(defn run-cmd
  "Internal. Used by the server to start the app."
  []
  (io/make-parents "target/resources/_")
  (when (fs/exists? "package.json")
    (shell "npm" "install"))
  (apply println "clj" (run-args)))

(defn css [& args]
  (apply shell
         (concat (if (fs/exists? (tailwind-path))
                   [(tailwind-path)]
                   ["npx" "tailwindcss"])
                 ["-c" "resources/tailwind.config.js"
                  "-i" "resources/tailwind.css"
                  "-o" "target/resources/public/css/main.css"]
                 args)))

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
  (future (css "--watch"))
  (spit ".nrepl-port" "7888")
  (apply clojure {:extra-env {"BIFF_ENV" "dev"}}
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
  (let [{:biff.tasks/keys [server deploy-to deploy-from]} @config]
    (css "--minify")
    (if (windows?)
      (do
        (shell "scp" "config.edn" (str "app@" server ":"))
        (shell "ssh" (str "app@" server) "mkdir" "-p" "target/resources/public/css/")
        (shell "scp" "target/resources/public/css/main.css"
               (str "app@" server ":target/resources/public/css/main.css")))
      (do
        (fs/set-posix-file-permissions "config.edn" "rw-------")
        (shell "rsync" "-a" "--relative"
               "config.edn" "target/resources/public/css/main.css"
               (str "app@" server ":"))))
    (time (shell "git" "push" deploy-to deploy-from))))

(defn soft-deploy
  "Hotswaps modified code into the server.

  `rsync`s config and code to the server, then `eval`s any changed files and
  regenerates HTML and CSS files. Does not refresh or restart."
  []
  (when-not (fs/which "rsync")
    (binding [*out* *err*]
      (println "`rsync` command not found. Please install it."))
    (System/exit 1))
  (let [{:biff.tasks/keys [server soft-deploy-fn]} @config]
    (css "--minify")
    (when-not (windows?)
      (fs/set-posix-file-permissions "config.edn" "rw-------"))
    (shell "rsync" "-a" "--relative" "--info=name1" "--delete"
           "config.edn" "deps.edn" "bb.edn" "src" "resources" "target/resources/public/css/main.css"
           (str "app@" server ":"))
    (trench (str "\"(" soft-deploy-fn " @com.biffweb/system)\""))))

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
  (future (prod-repl))
  (future (auto-soft-deploy))
  (logs))
