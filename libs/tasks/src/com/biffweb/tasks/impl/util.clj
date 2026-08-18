(ns com.biffweb.tasks.impl.util
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.java.shell :as sh]
            [clojure.stacktrace :as st]
            [clojure.string :as str]))

;;;; config ====================================================================

(def config-defaults
  {:biff.tasks/deployment-name        "app"
   :biff.tasks/tailwind-version       "4.3.0"
   :biff.tasks/cljfmt-version         "0.16.4"
   :biff.tasks/clj-kondo-version      "2026.05.25"
   :biff.tasks/css-output-path        "target/resources/public/css/main.css"
   :biff.tasks/docs-directory         "docs/api"
   :biff.tasks/deploy-untracked-files ["target/resources/public/css/main.css"
                                       {:src  "config.prod.env"
                                        :dest "config.env"}]})

;; Do this instead of biff.core/register so we don't have to require Malli
(def ^:private config-rules
  [[:biff.tasks/clojars-secret "a string or biff.core/secret-delay"
    #(or (string? %) (delay? %))]
   [:biff.tasks/clj-kondo-version "a string" string?]
   [:biff.tasks/clojars-username "a string" string?]
   [:biff.tasks/cljfmt-version "a string" string?]
   [:biff.tasks/css-output-path "a string" string?]
   [:biff.tasks/docs-directory "a string" string?]
   [:biff.tasks/docs-namespaces "a vector of symbols"
    #(and (vector? %) (every? symbol? %))]
   [:biff.tasks/deploy-untracked-files "a sequential value" sequential?]
   [:biff.tasks/deployment-name "a string" string?]
   [:biff.tasks/domain "a string" string?]
   [:biff.tasks/gpg-sign-with-passphrase "a boolean" boolean?]
   [:biff.tasks/gpg-sign-key-id "a string" string?]
   [:biff.tasks/group-name "a string" string?]
   [:biff.tasks/lib-name "a string" string?]
   [:biff.tasks/lib-version "a string" string?]
   [:biff.tasks/main-ns "a symbol" symbol?]
   [:biff.tasks/monorepo "a boolean" boolean?]
   [:biff.tasks/nrepl-port "an integer" integer?]
   [:biff.tasks/pom-data "a vector" vector?]
   [:biff.tasks/pom-scm "a map" map?]
   [:biff.tasks/skip-ssh-agent "a boolean" boolean?]
   [:biff.tasks/tailwind-version "a string" string?]])

(defn- validate-config [config]
  (doseq [[key expected valid?] config-rules]
    (when (and (contains? config key)
               (not (valid? (get config key))))
      (throw (ex-info (str "Invalid task config value for " key
                           "; expected " expected)
                      {:key key :expected expected}))))
  config)

;; Any 3rd party projects that want to read user config should use
;; com.biffweb.config/use-aero-config instead of this non-public function.
;;
;; Tasks in this project should only call this function from the top-level
;; function and then pass needed config to other functions. You should only need
;; to read the top-level function to know what part of the config a task needs.
(let [normalize (fn [k]
                  (if (symbol? k)
                    (keyword "biff.tasks" (str k))
                    k))]
  (defn read-config [& {:keys [required select]}]
    (let [config-file-exists (some? (io/resource "config.edn"))

          config
          (validate-config
           (merge config-defaults
                  (when config-file-exists
                    ((requiring-resolve 'com.biffweb.config/use-aero-config)
                     {}))
                  @(requiring-resolve 'com.biffweb.tasks/*extra-config*)))

          required (not-empty (mapv normalize required))
          select   (not-empty (mapv normalize select))
          selected (concat required select)]
      (doseq [k required]
        (when-not (some? (get config k))
          (throw (ex-info (str "Missing required key: " k) {:key k}))))
      (cond-> config
        select (select-keys selected)))))

;;;; shell =====================================================================

(defn- windows? []
  (-> (System/getProperty "os.name")
      str/lower-case
      (str/includes? "windows")))

(defn- which [& args]
  (apply (requiring-resolve 'babashka.fs/which) args))

(defn- sh-success? [& args]
  (try
    (= 0 (:exit (apply sh/sh args)))
    (catch Exception _
      false)))

(defn shell-quote [s]
  (str "'"
       (str/replace (str s) "'" "'\"'\"'")
       "'"))

(def ^:private ^:dynamic *shell-env* nil)

(defn shell
  "Difference between this and clojure.java.shell/sh:

   - inherits std{in,out,err}
   - throws on non-zero exit code
   - puts *shell-env* in the environment"
  [& args]
  (try
    (apply (requiring-resolve 'babashka.process/shell)
           {:extra-env *shell-env*
            :out       *out*
            :err       *err*}
           args)
    (finally
      (.flush ^java.io.Writer *out*)
      (.flush ^java.io.Writer *err*))))

(defn shell-inherit [& args]
  (apply (requiring-resolve 'babashka.process/shell)
         {:extra-env *shell-env*
          :inherit   true}
         args))

(defn- get-env-from [cmd]
  (let [{:keys [exit out]} (sh/sh "sh" "-c" (str cmd "; printenv"))]
    (when (= 0 exit)
      (->> out
           str/split-lines
           (map #(vec (str/split % #"=" 2)))
           (filter #(= 2 (count %)))
           (into {})))))

(defn with-ssh-agent* [{:keys [biff.tasks/skip-ssh-agent]} f]
  (if-let [env (and (not skip-ssh-agent)
                    (not (windows?))
                    (which "ssh-agent")
                    (not (sh-success? "ssh-add" "-l"))
                    (nil? *shell-env*)
                    (get-env-from "eval $(ssh-agent)"))]
    (binding [*shell-env* env]
      (try
        (try
          (shell "ssh-add")
          (println "Started an ssh-agent session. If you set up `keychain`,"
                   "you won't have to enter your password each time you run"
                   "this command: https://www.funtoo.org/Funtoo:Keychain")
          (catch Exception e
            (binding [*out* *err*]
              (st/print-stack-trace e)
              (println "\nssh-add failed. You may have to enter your password"
                       "multiple times. You can avoid this if you set up"
                       "https://www.funtoo.org/Funtoo:Keychain"))))
        (f)
        (finally
          (sh/sh "ssh-agent" "-k" :env *shell-env*))))
    (f)))

(defmacro with-ssh-agent [ctx & body]
  `(with-ssh-agent* ~ctx (fn [] ~@body)))

(defn ssh-target [{:biff.tasks/keys [deployment-name domain]}]
  (str deployment-name "@" domain))

(defn ssh-run [ctx & args]
  (shell "ssh" (ssh-target ctx) (str/join " " (map shell-quote args))))

(defn ssh-run-shell [ctx command]
  (shell "ssh" (ssh-target ctx) (str "sh -lc " (shell-quote command))))

;;;; project files =============================================================

(defn- hidden-dir? [path]
  (->> (str/split path #"/")
       butlast
       (some #(str/starts-with? % "."))))

(defn relative-path [from-dir to-file]
  (-> (.relativize (.toPath (.getCanonicalFile (io/file from-dir)))
                   (.toPath (.getCanonicalFile (io/file to-file))))
      str
      (str/replace "\\" "/")))

(defn project-root []
  (io/file (System/getProperty "user.dir")))

(defn read-deps-edn []
  (-> (io/file (project-root) "deps.edn")
      slurp
      edn/read-string))

(defn ensure-prod-alias! []
  (when-not (seq (get-in (read-deps-edn) [:aliases :prod :main-opts]))
    (throw (ex-info "deps.edn must have a :prod alias with :main-opts set."
                    {}))))

(defn deps-paths []
  (let [{:keys [paths]} (read-deps-edn)]
    (into [] (distinct) paths)))

(defn all-deps-paths []
  (let [{:keys [paths aliases]} (read-deps-edn)]
    (into []
          (distinct)
          (concat paths (mapcat :extra-paths (vals aliases))))))

(defn project-files []
  (let [root    (project-root)
        tracked (try
                  (let [{:keys [exit out]} (sh/sh "git" "ls-files"
                                                  :dir (.getPath root))]
                    (when (zero? exit)
                      (->> out
                           str/split-lines
                           (remove str/blank?)
                           (remove hidden-dir?)
                           (mapv #(io/file root %)))))
                  (catch Exception _ nil))
        files   (or (not-empty tracked)
                    (into []
                          (mapcat #(file-seq (io/file root %)))
                          (all-deps-paths)))]
    (->> files
         (filterv #(.isFile %))
         (mapv #(.getCanonicalFile %))
         distinct
         vec)))

(defn clojure-files []
  (filterv #(some (fn [extension]
                    (str/ends-with? (.getPath %) extension))
                  [".clj" ".cljc" ".cljs" ".edn"])
           (project-files)))
