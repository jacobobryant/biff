(ns com.biffweb.tasks.util
  "Helper functions that don't have external dependencies.

   Or at least, if they do have external deps, they use `requiring-resolve` so as not to slow down
   other tasks."
  (:refer-clojure :exclude [future])
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.java.shell :as sh]
            [clojure.stacktrace :as st]
            [clojure.string :as str]
            [com.biffweb.core :as biff.core]))

(biff.core/register
 {:biff.tasks/clojars-secret         [:or 'string? 'ifn?]
  :biff.tasks/clj-kondo-version      'string?
  :biff.tasks/clojars-username       'string?
  :biff.tasks/cljfmt-version         'string?
  :biff.tasks/css-output             'string?
  :biff.tasks/docs-namespaces        [:vector 'symbol?]
  :biff.tasks/deploy-untracked-files 'sequential?
  :biff.tasks/deployment-name        'string?
  :biff.tasks/domain                 'string?
  :biff.tasks/generate-assets-fn     'qualified-symbol?
  :biff.tasks/gpg-passphrase         [:or 'string? 'ifn?]
  :biff.tasks/gpg-sign-key-id        'string?
  :biff.tasks/group-name             'string?
  :biff.tasks/lib-name               'string?
  :biff.tasks/lib-version            'string?
  :biff.tasks/main-ns                'symbol?
  :biff.tasks/monorepo               'boolean?
  :biff.tasks/nrepl-port             'integer?
  :biff.tasks/pom-license            'map?
  :biff.tasks/pom-scm                'map?
  :biff.tasks/skip-ssh-agent         'boolean?
  :biff.tasks/tailwind-build         'string?
  :biff.tasks/tailwind-version       'string?})

(defmacro future [& body]
  `(clojure.core/future
     (try
       ~@body
       (catch Exception e#
         (binding [*err* *out*]
           (st/print-stack-trace e#))))))

(defn windows? []
  (-> (System/getProperty "os.name")
      str/lower-case
      (str/includes? "windows")))

(defn which [& args]
  (apply (requiring-resolve 'babashka.fs/which) args))

(defn bun-pkg-installed? [package-name]
  (and (which "bun")
       (str/includes? (:out (sh/sh "bun" "pm" "ls"))
                      package-name)))

(defn sh-success? [& args]
  (try
    (= 0 (:exit (apply sh/sh args)))
    (catch Exception _
      false)))

(defn exists? [f]
  (.exists (io/file f)))

(defn read-deps-edn []
  (-> "deps.edn"
      slurp
      edn/read-string))

(defn source-paths []
  (let [{:keys [paths]} (read-deps-edn)]
    (->> paths
         distinct
         (filter exists?)
         vec)))

(defn deps-paths []
  (let [{:keys [paths aliases]} (read-deps-edn)]
    (->> (concat paths (mapcat :extra-paths (vals aliases)))
         distinct
         (filter exists?)
         vec)))

(defn hidden-dir? [path]
  (->> (str/split path #"/")
       butlast
       (some #(str/starts-with? % "."))))

(defn git-ls-files []
  (let [{:keys [exit out err]} (sh/sh "git" "ls-files"
                                      :dir (System/getProperty "user.dir"))]
    (when-not (zero? exit)
      (throw (ex-info "git ls-files failed" {:exit exit :err err})))
    (->> out
         str/split-lines
         (remove str/blank?)
         (remove hidden-dir?)
         vec)))

(defn shell-quote [s]
  (str "'"
       (str/replace (str s) "'" "'\"'\"'")
       "'"))

(def read-config
  (memoize
   (fn []
     (merge
      {:biff.tasks/deployment-name "app"}
      ((requiring-resolve 'com.biffweb.config/use-aero-config) {})))))

(def ^:dynamic *shell-env* nil)

(defn shell
  "Difference between this and clojure.java.shell/sh:

   - inherits std{in,out,err}
   - throws on non-zero exit code
   - puts *shell-env* in the environment"
  [& args]
  (apply (requiring-resolve 'babashka.process/shell)
         {:extra-env *shell-env*}
         args))

(defn shell-capture [& args]
  (apply sh/sh args))

(defn get-env-from [cmd]
  (let [{:keys [exit out]} (sh/sh "sh" "-c" (str cmd "; printenv"))]
    (when (= 0 exit)
      (->> out
           str/split-lines
           (map #(vec (str/split % #"=" 2)))
           (filter #(= 2 (count %)))
           (into {})))))

(defn with-ssh-agent* [{:keys [biff.tasks/skip-ssh-agent]} f]
  (if-let [env (and (not skip-ssh-agent)
                    (which "ssh-agent")
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

(defn ssh-target [{:biff.tasks/keys [deployment-name domain]}]
  (str deployment-name "@" domain))

(defn root-ssh-target [{:biff.tasks/keys [domain]}]
  (str "root@" domain))

(defn remote-app-home [{:biff.tasks/keys [deployment-name]}]
  (str "/home/" deployment-name))

(defn remote-repo-path [ctx]
  (str (remote-app-home ctx) "/repo"))

(defn ssh-run [ctx & args]
  (apply shell "ssh" (ssh-target ctx) args))

(defn ssh-root-run [ctx & args]
  (apply shell "ssh" (root-ssh-target ctx) args))

(defn ssh-run-shell [ctx command]
  (shell "ssh" (ssh-target ctx) "sh" "-lc" command))

(defn ssh-root-run-shell [ctx command]
  (shell "ssh" (root-ssh-target ctx) "sh" "-lc" command))

(defn ssh-capture [ctx & args]
  (apply sh/sh "ssh" (ssh-target ctx) args))

(defn ssh-capture-shell [ctx command]
  (sh/sh "ssh" (ssh-target ctx) "sh" "-lc" command))

(defn- deploy-file-spec [file]
  (if (string? file)
    {:src file :dest file}
    file))

(defn deploy-file-specs [deploy-untracked-files]
  (mapv deploy-file-spec deploy-untracked-files))

(defn push-deploy-files! [{:biff.tasks/keys [deploy-untracked-files] :as ctx}]
  (let [files (->> (deploy-file-specs deploy-untracked-files)
                   (filterv (comp exists? :src)))]
    (when-some [dirs (not-empty (->> files
                                     (keep (comp not-empty
                                                 (requiring-resolve 'babashka.fs/parent)
                                                 :dest))
                                     distinct
                                     vec))]
      (ssh-run-shell ctx
                     (str "mkdir -p "
                          (str/join " "
                                    (map #(shell-quote (str (remote-repo-path ctx) "/" %))
                                         dirs)))))
    (doseq [{:keys [src dest]} files]
      (shell "scp" src (str (ssh-target ctx) ":" (remote-repo-path ctx) "/" dest)))))

(defn current-git-branch []
  (let [{:keys [exit out]} (sh/sh "git" "branch" "--show-current")
        branch             (some-> out str/trim not-empty)]
    (when-not (zero? exit)
      (throw (ex-info "Failed to read the current git branch" {:exit exit})))
    (when-not branch
      (throw (ex-info "Deploy requires a branch checkout; HEAD is detached." {})))
    branch))

(defn ensure-clean-worktree! []
  (let [{:keys [exit out]} (sh/sh "git" "status" "--porcelain")]
    (when-not (zero? exit)
      (throw (ex-info "Failed to inspect git status" {:exit exit})))
    (when (not-empty (str/trim out))
      (throw (ex-info "Deploy requires a clean worktree." {:status out})))))

(defn git-ref-exists? [ref]
  (= 0 (:exit (sh/sh "git" "show-ref" "--verify" "--quiet" ref))))
