(ns com.biffweb.build
  (:refer-clojure :exclude [future])
  (:require [com.biffweb.build.lazy.clojure.java.io :as io]
            [com.biffweb.build.lazy.clojure.java.shell :as sh]
            [com.biffweb.build.lazy.clojure.string :as str]
            [com.biffweb.build.lazy.babashka.fs :as fs]
            [com.biffweb.build.lazy.babashka.process :as process]
            [com.biffweb.build.lazy.babashka.curl :as curl]
            [com.biffweb.build.lazy.com.biffweb.impl.config :as config]
            [com.biffweb.build.lazy.clojure.stacktrace :as st]))

(defmacro future [& body]
  `(clojure.core/future
     (try
       ~@body
       (catch Exception e#
         (binding [*err* *out*]
           (st/print-stack-trace e#))))))

(defn- print-help [{:keys [biff.tasks/tasks]}]
  (let [col-width (apply max (mapv count (keys tasks)))]
    (println "Available commands:")
    (println)
    (doseq [[task-name task-var] (sort-by key tasks)
            :let [doc (some-> (:doc (meta task-var))
                              str/split-lines
                              first)]]
      (printf (str "  %-" col-width "s%s\n")
              task-name
              (if doc
                (str " - " doc)
                "")))))

(defn- print-help-for [task-fn]
  (let [{:keys [doc] :or {doc ""}} (meta task-fn)
        lines (str/split-lines doc)
        indent (some->> lines
                        rest
                        (remove (comp empty? str/trim))
                        not-empty
                        (mapv #(count (take-while #{\ } %)))
                        (apply min))
        doc (str (first lines) "\n"
                 (->> (rest lines)
                      (map #(subs % (min (count %) indent)))
                      (str/join "\n")))]
    (println doc)))

(defn- print-error [message]
  (binding [*err* *out*]
    (println message)))

(def ^:private ^:dynamic *shell-env* nil)

(defn- windows? []
  (-> (System/getProperty "os.name")
      (str/lower-case)
      (str/includes? "windows")))

(defn- shell [& args]
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
  (apply shell "ssh" (str "root@" server) args))

(defn- trench [ctx & args]
  (apply ssh-run ctx "trench" "-p" "7888" "-e" args))

(defn- local-tailwind-path []
  (if (windows?)
    "bin/tailwindcss.exe"
    "bin/tailwindcss"))

(defn- tailwind-file []
  (let [os-name (str/lower-case (System/getProperty "os.name"))
        os-type (cond
                  (str/includes? os-name "windows") "windows"
                  (str/includes? os-name "linux") "linux"
                  :else "macos")
        arch (case (System/getProperty "os.arch")
               "amd64" "x64"
               "arm64")]
    (str "tailwindcss-" os-type "-" arch (when (= os-type "windows") ".exe"))))

(defn- install-tailwind [ctx]
  (let [file (cond
               (:biff.tasks/tailwind-file ctx)
               (:biff.tasks/tailwind-file ctx)

               ;; Backwards compatibility.
               (:biff.tasks/tailwind-build ctx)
               (str "tailwindcss-" (:biff.tasks/tailwind-build ctx))

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

(defn- push-files-rsync [{:biff.tasks/keys [server css-output]}]
  (let [files (->> (:out (sh/sh "git" "ls-files"))
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

(defn- push-files-git [{:biff.tasks/keys [server deploy-to deploy-from deploy-cmd css-output]}]
  (apply shell (concat ["scp" "config.edn"]
                       (when (fs/exists? "secrets.env") ["secrets.env"])
                       [(str "app@" server ":")]))
  (when (fs/exists? css-output)
    (shell "ssh" (str "app@" server) "mkdir" "-p" "target/resources/public/css/")
    (shell "scp" css-output (str "app@" server ":" css-output)))
  (time (if deploy-cmd
          (apply shell deploy-cmd)
          ;; For backwards compatibility
          (shell "git" "push" deploy-to deploy-from))))

(defn- push-files []
  (if (fs/which "rsync")
    (push-files-rsync)
    (push-files-git)))

(defn- auto-soft-deploy [{:keys [biff.tasks/tasks] :as ctx}]
  ((tasks "soft-deploy") ctx)
  (let [last-ran (atom (System/nanoTime))
        ;; TODO replace with beholder
        p (process/process ["fswatch" "-orl" "0.1" "--event=Updated" "--event=Removed" "--allow-overflow" "."]
                           {:err :inherit})]
    (with-open [rdr (io/reader (:out p))]
      (doseq [l (line-seq rdr)]
        (when (< (Math/pow 10 9) (- (System/nanoTime) @last-ran))
          ((tasks "soft-deploy") ctx))
        (reset! last-ran (System/nanoTime))))))

;;;;;; PUBLIC INTERFACE ==========================================================

(def ctx-defaults
  {:biff.tasks/css-output "target/resources/public/css/main.css"})

(defn run-task [{:keys [biff.tasks/tasks] :as ctx} [task & args]]
  (let [task-fn (get tasks task)]
    (cond
      (or (nil? task)
          (#{"help" "--help" "-h"} task))
      (print-help ctx)

      (nil? task-fn)
      (print-error (str "Unrecognized task: " task))

      (#{"help" "--help" "-h"} (first args))
      (print-help-for task-fn)

      :else
      (apply task-fn (merge ctx-defaults ctx) args))))

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
  [{:keys [biff.tasks/css-output]} & args]
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

(defn dev
  "Dev docstring

   additional dev info"
  [{:biff.tasks/keys [tasks main-ns] :as ctx}]
  (io/make-parents "target/resource/_")
  (when (fs/exists? "package.json")
    (shell "npm install"))
  (future ((tasks "css") ctx "--watch"))
  (spit ".nrepl-port" "7888")
  ((requiring-resolve (symbol (str main-ns) "-main"))))

;;(defn uberjar
;;  "Uberjar docstring
;;
;;  additional uberjar info
;;  That's what I'm talkin about"
;;  [_]
;;  (println "uberjar"))

(defn generate-secrets
  "Prints new secrets to put in config.env"
  [_]
  (println "Put these in your config.env file:")
  (println)
  (println (str "COOKIE_SECRET=" (new-secret 16)))
  (println (str "JWT_SECRET=" (new-secret 32)))
  (println))

(defn restart
  "Restarts the app process via `systemctl restart app` (on the server)."
  [ctx]
  (ssh-run ctx "systemctl reset-failed app.service; systemctl restart app"))

(defn soft-deploy
  "Pushes code to the server and evaluates changed files.

  Uploads config and code to the server (see `deploy`), then `eval`s any
  changed files and regenerates HTML and CSS files. Does not refresh or
  restart."
  [{:biff.tasks/keys [tasks soft-deploy-fn on-soft-deploy] :as ctx}]
  (with-ssh-agent ctx
    ((tasks "css") ctx "--minify")
    (push-files)
    (trench (or on-soft-deploy
                ;; backwards compatibility
                (str "\"(" soft-deploy-fn " @com.biffweb/system)\"")))))

(defn deploy
  "Pushes code to the server and restarts the app.

  Uploads config (config.edn and secrets.env) and code to the server, using
  `rsync` if it's available, and `git push` by default otherwise. Then restarts
  the app.

  You must set up a server first. See https://biffweb.com/docs/reference/production/"
  [{:keys [biff.tasks/tasks] :as ctx}]
  (with-ssh-agent ctx
    ((tasks "css") ctx "--minify")
    (push-files)
    ((tasks "restart") ctx)))

(defn logs
  "Tails the server's application logs."
  [ctx & [n-lines]]
  (ssh-run ctx "journalctl" "-u" "app" "-f" "-n" (or n-lines "300")))

(defn prod-repl
  "Opens an SSH tunnel so you can connect to the server via nREPL."
  [{:keys [biff.tasks/server]}]
  (println "Connect to nrepl port 7888")
  (spit ".nrepl-port" "7888")
  (shell "ssh" "-NL" "7888:localhost:7888" (str "root@" server)))

(defn prod-dev
  "Runs the soft-deploy command whenever a file is modified. Also runs prod-repl and logs."
  [{:keys [biff.tasks/tasks] :as ctx}]
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
  (with-ssh-agent ctx
    (future ((tasks "prod-repl") ctx))
    (future (auto-soft-deploy ctx))
    ((tasks "logs") ctx)))

(defn wrap-config [task]
  (fn [ctx & args]
    (let [ctx (if (::loaded ctx)
                ctx
                (assoc (config/use-aero-config ctx) ::loaded true))]
      (apply task ctx args))))

(def tasks
  {"css"              (wrap-config css)
   "deploy"           (wrap-config deploy)
   "dev"              (wrap-config dev)
   "generate-secrets" generate-secrets
   "logs"             (wrap-config logs)
   "prod-dev"         (wrap-config prod-dev)
   "prod-repl"        (wrap-config prod-repl)
   "restart"          (wrap-config restart)
   "soft-deploy"      (wrap-config soft-deploy)
   ;"uberjar"          #'uberjar
   
   })
