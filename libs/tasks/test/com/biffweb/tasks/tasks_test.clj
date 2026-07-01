(ns com.biffweb.tasks.tasks-test
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.java.shell :as sh]
            [clojure.string :as str]
            [clojure.test :as t :refer [deftest is]]
            [antq.api]
            [cljfmt.config :as cljfmt-config]
            [com.biffweb.tasks :as tasks]
            [com.biffweb.tasks.css :as css]
            [com.biffweb.tasks.deploy :as deploy]
            [com.biffweb.tasks.dev :as dev-task]
            [com.biffweb.tasks.docs :as tasks-docs]
            [com.biffweb.tasks.format :as tasks-format]
            [com.biffweb.tasks.impl.bin :as task-bin]
            [com.biffweb.tasks.lib :as biff.tasks.lib]
            [com.biffweb.tasks.lint :as tasks-lint]
            [com.biffweb.tasks.nrepl :as tasks-nrepl]
            [com.biffweb.tasks.publish :as publish]
            [com.biffweb.tasks.reload :as reload]
            [com.biffweb.tasks.test :as task-test]
            [com.biffweb.tasks.update :as tasks-update]
            [com.biffweb.tasks.util :as util]
            [cognitect.test-runner]
            [deps-deploy.deps-deploy]
            [deps-deploy.gpg]
            [hato.client]
            [nrepl.server]
            [refactor-nrepl.middleware]))

(defn- rmrf [file]
  (when (.exists file)
    (when (.isDirectory file)
      (run! rmrf (.listFiles file)))
    (io/delete-file file)))

(defmacro with-temp-dir [[sym] & body]
  `(let [~sym (.toFile (java.nio.file.Files/createTempDirectory "biff-tasks-task-test"
                                                                (make-array java.nio.file.attribute.FileAttribute 0)))]
     (try
       ~@body
       (finally
         (rmrf ~sym)))))

(defmacro with-user-dir [dir & body]
  `(let [original# (System/getProperty "user.dir")]
     (try
       (System/setProperty "user.dir" (.getPath ~dir))
       ~@body
       (finally
         (System/setProperty "user.dir" original#)))))

(defn- write-file [dir relative-path contents]
  (let [file (io/file dir relative-path)]
    (io/make-parents file)
    (spit file contents)))

(defn- sh! [dir & args]
  (let [{:keys [exit err]} (apply sh/sh (concat args [:dir (.getPath dir)]))]
    (when-not (zero? exit)
      (throw (ex-info "Command failed" {:args args :err err :dir (.getPath dir)})))))

(defn- init-git! [dir]
  (sh! dir "git" "init")
  (sh! dir "git" "config" "user.name" "Copilot")
  (sh! dir "git" "config" "user.email" "copilot@example.com")
  (sh! dir "git" "add" "."))

(deftest task-surface-matches-spec
  (is (= #{"css"
           "deploy"
           "dev"
           "format"
           "lint"
           "nrepl"
           "prod-install"
           "prod-logs"
           "prod-nrepl"
           "prod-restart"
           "setup"
           "test"
           "update"
           "uberjar"}
         (set (keys tasks/tasks)))))

(deftest lib-task-surface-matches-spec
  (is (= #{"docs"
           "format"
           "lint"
           "nrepl"
           "publish"
           "test"
           "update"}
         (set (keys biff.tasks.lib/tasks)))))

(deftest docs-task-generates-api-markdown
  (with-temp-dir [dir]
    (let [ns-sym      'example.docs
          source-file (io/file dir "src/example/docs.clj")
          output-file (io/file dir "docs/api/example.docs.md")]
      (write-file dir "src/example/docs.clj"
                  (str "(ns example.docs\n"
                       "  \"## Schema\n"
                       "\n"
                       "   ### :example/value\n"
                       "\n"
                       "   Namespace summary\")\n\n"
                       "(defn beta\n"
                       "  \"Beta doc\n"
                       "     second line\"\n"
                       "  []\n"
                       "  nil)\n\n"
                       "(def alpha\n"
                       "  \"Alpha doc\n"
                       "       indented detail\"\n"
                       "  1)\n"))
      (let [example-ns (create-ns ns-sym)
            beta-var   (intern example-ns 'beta (fn [] nil))
            alpha-var  (intern example-ns 'alpha 1)]
        (alter-meta! example-ns assoc
                     :doc "## Schema\n\n   ### :example/value\n\n   Namespace summary")
        (alter-meta! beta-var assoc
                     :arglists '([])
                     :doc "Beta doc\n     second line"
                     :file "example/docs.clj"
                     :line 4)
        (alter-meta! alpha-var assoc
                     :doc "Alpha doc\n       indented detail"
                     :file "example/docs.clj"
                     :line 9)
        (try
          (with-user-dir dir
            (with-redefs [io/resource      (fn [path]
                                             (when (= path "example/docs.clj")
                                               (.toURL (.toURI source-file))))
                          require          (fn [sym]
                                             (is (= ns-sym sym)))
                          util/read-config (constantly {:biff.tasks/docs-namespaces [ns-sym]})]
              (tasks-docs/docs)))
          (is (= (str "# example.docs reference\n\n"
                      "- [Schema](#schema)\n"
                      "  - [:example/value](#examplevalue)\n"
                      "- [API](#api)\n"
                      "  - [beta](#beta)\n"
                      "  - [alpha](#alpha)\n\n"
                      "## Schema\n\n"
                      "### :example/value\n\n"
                      "Namespace summary\n"
                      "\n"
                      "## API\n\n"
                      "### beta\n\n"
                      "[view source](../../src/example/docs.clj#L4)\n\n"
                      "```\n"
                      "(beta)\n\n"
                      "Beta doc\n"
                      "second line\n"
                      "```\n\n"
                      "### alpha\n\n"
                      "[view source](../../src/example/docs.clj#L9)\n\n"
                      "```\n"
                      "Alpha doc\n"
                      "indented detail\n"
                      "```\n")
                 (slurp output-file)))
          (finally
            (remove-ns ns-sym)))))))

(deftest ensure-binary-uses-system-binary-when-pinned-version-already-matches
  (let [installs (atom [])]
    (with-redefs [task-bin/preferred-bin-path   (constantly "/usr/bin/cljfmt")
                  task-bin/installed-version    (constantly "0.16.4")
                  task-bin/install-binary!      (fn [tool opts]
                                                  (swap! installs conj [tool opts]))
                  task-bin/local-bin-installed? (constantly false)
                  task-bin/system-bin-path      (constantly "/usr/bin/cljfmt")]
      (is (= "/usr/bin/cljfmt"
             (task-bin/ensure-binary! :cljfmt "0.16.4"))))
    (is (empty? @installs))))

(deftest ensure-binary-installs-pinned-version-when-preferred-version-mismatches
  (let [installs (atom [])]
    (with-redefs [task-bin/preferred-bin-path   (constantly "/usr/bin/cljfmt")
                  task-bin/installed-version    (constantly "0.16.3")
                  task-bin/install-binary!      (fn [tool opts]
                                                  (swap! installs conj [tool opts])
                                                  "bin/cljfmt")
                  task-bin/local-bin-installed? (constantly true)
                  task-bin/local-bin-path       (constantly "bin/cljfmt")]
      (is (= "bin/cljfmt"
             (task-bin/ensure-binary! :cljfmt "0.16.4"))))
    (is (= [[:cljfmt {:version "0.16.4"}]]
           @installs))))

(deftest ensure-local-binary-installs-managed-copy-even-when-global-binary-exists
  (let [installs   (atom [])
        installed? (atom false)]
    (with-redefs [task-bin/local-bin-installed? (fn [_] @installed?)
                  task-bin/local-bin-path       (constantly "bin/cljfmt")
                  task-bin/install-binary!      (fn [tool opts]
                                                  (reset! installed? true)
                                                  (swap! installs conj [tool opts])
                                                  "bin/cljfmt")
                  task-bin/system-bin-path      (constantly "/usr/bin/cljfmt")]
      (is (= "bin/cljfmt"
             (task-bin/ensure-local-binary! :cljfmt))))
    (is (= [[:cljfmt {:version "0.16.4"}]]
           @installs))))

(deftest download-to-times-out-on-stalled-response-body
  (let [server (com.sun.net.httpserver.HttpServer/create
                (java.net.InetSocketAddress. "127.0.0.1" 0)
                0)]
    (try
      (.createContext
       server
       "/tailwindcss"
       (reify com.sun.net.httpserver.HttpHandler
         (handle [_ exchange]
           (.sendResponseHeaders exchange 200 5)
           (Thread/sleep 150)
           (with-open [out (.getResponseBody exchange)]
             (.write out (.getBytes "hello"))))))
      (.start server)
      (with-temp-dir [dir]
        (let [url  (str "http://127.0.0.1:" (.getPort (.getAddress server)) "/tailwindcss")
              dest (io/file dir "tailwindcss")
              ex   (with-redefs-fn
                     {#'com.biffweb.tasks.impl.bin/download-connect-timeout-ms 50
                      #'com.biffweb.tasks.impl.bin/download-read-timeout-ms    50}
                     #(try
                        (#'com.biffweb.tasks.impl.bin/download-to! :tailwindcss url dest)
                        nil
                        (catch Exception e
                          e)))]
          (is (instance? clojure.lang.ExceptionInfo ex))
          (is (= :tailwindcss (:tool (ex-data ex))))
          (is (= url (:url (ex-data ex))))
          (is (= 50 (:connect-timeout-ms (ex-data ex))))
          (is (= 50 (:read-timeout-ms (ex-data ex))))
          (is (re-find #"Timed out downloading tailwindcss" (ex-message ex)))))
      (finally
        (.stop server 0)))))

(deftest tailwind-command-ignores-node-installations-when-version-is-pinned
  (let [ensures (atom [])]
    (with-redefs [util/read-config        (constantly {:biff.tasks/tailwind-version "4.3.0"})
                  util/bun-pkg-installed? (constantly true)
                  util/sh-success?        (constantly true)
                  task-bin/ensure-binary! (fn [tool version]
                                            (swap! ensures conj [tool version])
                                            "bin/tailwindcss")
                  task-bin/local-bin-path (constantly "bin/tailwindcss")]
      (is (= {:tailwind-cmd :local-bin
              :command      ["bin/tailwindcss"]}
             (task-bin/tailwind-command))))
    (is (= [[:tailwindcss "4.3.0"]] @ensures))))

(deftest tailwind-command-installs-default-binary-when-nothing-else-is-available
  (let [installs (atom [])]
    (with-redefs [util/read-config              (constantly {})
                  util/bun-pkg-installed?       (constantly false)
                  util/sh-success?              (constantly false)
                  task-bin/local-bin-installed? (constantly false)
                  task-bin/system-bin-path      (constantly nil)
                  task-bin/install-binary!      (fn [tool opts]
                                                  (swap! installs conj [tool opts])
                                                  "bin/tailwindcss")]
      (is (= {:tailwind-cmd :local-bin
              :command      ["bin/tailwindcss"]}
             (task-bin/tailwind-command))))
    (is (= [[:tailwindcss {:version "4.3.0"}]]
           @installs))))

(deftest css-uses-resolved-tailwind-command
  (let [commands (atom [])]
    (with-redefs [util/read-config          (constantly {:biff.tasks/css-output "target/resources/public/css/main.css"})
                  task-bin/tailwind-command (constantly {:tailwind-cmd :local-bin
                                                         :command      ["bin/tailwindcss"]})
                  util/shell                (fn [& args]
                                              (swap! commands conj (vec args)))]
      (css/css "--minify"))
    (is (= [["bin/tailwindcss"
             "-i"
             "resources/tailwind.css"
             "-o"
             "target/resources/public/css/main.css"
             "--minify"]]
           @commands))))

(deftest format-uses-git-ls-files-for-clojure-files
  (with-temp-dir [dir]
    (write-file dir "deps.edn" "{:paths [\"src\"], :aliases {:test {:extra-paths [\"test\"]}}}\n")
    (write-file dir "src/com/example.clj" "(defn add [x y]\n(+ x y))\n")
    (write-file dir "test/com/example_test.clj" "(ns com.example-test)\n")
    (write-file dir ".clj-kondo/ignored.clj" "(defn hidden [x]\n(+ x 10))\n")
    (write-file dir "config.edn" "{:foo 1\n :bar 2}\n")
    (write-file dir "notes.txt" "not clojure\n")
    (init-git! dir)
    (let [calls  (atom [])
          config (atom nil)]
      (with-user-dir dir
        (with-redefs [task-bin/ensure-binary! (constantly "cljfmt")
                      util/read-config        (constantly {})
                      util/shell              (fn [& args]
                                                (swap! calls conj (vec args))
                                                (reset! config (read-string (slurp (nth args 3)))))]
          (tasks-format/format)))
      (let [[binary subcommand flag _config-path & paths] (first @calls)]
        (is (= "cljfmt" binary))
        (is (= "fix" subcommand))
        (is (= "--config" flag))
        (is (= #{(.getPath (io/file dir "config.edn"))
                 (.getPath (io/file dir "deps.edn"))
                 (.getPath (io/file dir "src/com/example.clj"))
                 (.getPath (io/file dir "test/com/example_test.clj"))}
               (set paths)))
        (is (not (contains? (set paths)
                            (.getPath (io/file dir ".clj-kondo/ignored.clj")))))
        (is (true? (:align-map-columns? @config)))))))

(deftest format-allows-project-config-to-override-defaults
  (let [config (atom nil)]
    (with-redefs [task-bin/ensure-binary!        (constantly "cljfmt")
                  util/read-config               (constantly {})
                  util/shell                     (fn [& args]
                                                   (reset! config (read-string (slurp (nth args 3)))))
                  cljfmt-config/find-config-file (constantly ".cljfmt.edn")
                  cljfmt-config/read-config      (constantly {:align-form-columns? false})
                  util/git-ls-files              (constantly ["src/example.clj" "config.edn" "notes.txt"])]
      (tasks-format/format))
    (is (false? (:align-form-columns? @config)))))

(deftest format-aligns-map-columns-by-default
  (let [config (atom nil)]
    (with-redefs [task-bin/ensure-binary!        (constantly "cljfmt")
                  util/read-config               (constantly {})
                  util/shell                     (fn [& args]
                                                   (reset! config (read-string (slurp (nth args 3)))))
                  cljfmt-config/find-config-file (constantly nil)
                  util/git-ls-files              (constantly ["deps.edn"])]
      (tasks-format/format))
    (is (true? (:align-map-columns? @config)))))

(deftest lint-uses-git-ls-files-excluding-hidden-directories
  (with-temp-dir [dir]
    (write-file dir "deps.edn" "{:paths [\"src\"]}\n")
    (write-file dir "src/app.clj" "(ns app)\n")
    (write-file dir "test/app_test.clj" "(ns app-test)\n")
    (write-file dir ".clj-kondo/ignored.clj" "(ns ignored)\n")
    (write-file dir "notes.txt" "not clojure\n")
    (init-git! dir)
    (let [calls (atom [])]
      (with-user-dir dir
        (with-redefs [task-bin/ensure-binary! (constantly "clj-kondo")
                      util/read-config        (constantly {})
                      util/shell              (fn [& args]
                                                (swap! calls conj (vec args)))]
          (tasks-lint/lint)))
      (is (= [["clj-kondo" "--parallel" "--lint" "deps.edn" "src/app.clj" "test/app_test.clj"]]
             @calls)))))

(deftest lint-propagates-shell-failures
  (with-redefs [task-bin/ensure-binary! (constantly "clj-kondo")
                util/read-config        (constantly {})
                util/git-ls-files       (constantly ["src/app.clj"])
                util/shell              (fn [& _args]
                                          (throw (ex-info "lint failed" {:exit 3})))]
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"lint failed"
                          (tasks-lint/lint)))))

(deftest update-deps-only-targets-current-directory-deps-edn
  (with-temp-dir [dir]
    (write-file dir "deps.edn"
                (str "{:deps {org.clojure/clojure {:mvn/version \"1.12.0\"}}\n"
                     " :aliases {:run {:extra-deps {com.biffweb/tasks {:local/root \"../libs/tasks\"}\n"
                     "                              ring/ring-core {:mvn/version \"1.15.4\"}}}}}\n"))
    (let [outdated-args (atom nil)
          upgrade-args  (atom nil)
          shell-calls   (atom [])
          dep           {:name           "org.clojure/clojure"
                         :project        :clojure
                         :type           :java
                         :version        "1.12.0"
                         :latest-version "1.12.5"}]
      (with-user-dir dir
        (with-redefs [antq.api/outdated-deps
                      (fn [deps-map options]
                        (reset! outdated-args [deps-map options])
                        [dep])
                      antq.api/upgrade-deps!
                      (fn [pairs]
                        (reset! upgrade-args pairs)
                        {})
                      util/shell
                      (fn [& args]
                        (swap! shell-calls conj (vec args)))]
          (tasks-update/update "--deps-only")))
      (let [[deps-map options] @outdated-args]
        (is (= '{org.clojure/clojure {:mvn/version "1.12.0"}
                 ring/ring-core      {:mvn/version "1.15.4"}}
               deps-map))
        (is (= (.getPath (io/file dir "deps.edn"))
               (:file-path options))))
      (is (= [{:file (.getPath (io/file dir "deps.edn")) :dependency dep}]
             @upgrade-args))
      (is (empty? @shell-calls)))))

(deftest update-default-runs-files-only-subprocess-after-deps
  (with-temp-dir [dir]
    (write-file dir "deps.edn" "{:deps {org.clojure/clojure {:mvn/version \"1.12.0\"}}}\n")
    (let [steps (atom [])]
      (with-user-dir dir
        (with-redefs [antq.api/outdated-deps (fn [& _]
                                               (swap! steps conj :outdated)
                                               [])
                      antq.api/upgrade-deps! (fn [& _]
                                               (swap! steps conj :upgrade)
                                               {})
                      util/shell             (fn [& args]
                                               (swap! steps conj (vec args)))]
          (tasks-update/update)))
      (is (= [:outdated
              ["clojure" "-M:run" "update" "--files-only"]]
             @steps)))))

(deftest update-default-does-not-resolve-kondo-before-subprocess
  (with-temp-dir [dir]
    (write-file dir "deps.edn" "{:deps {org.clojure/clojure {:mvn/version \"1.12.0\"}}}\n")
    (with-user-dir dir
      (with-redefs [antq.api/outdated-deps (constantly [])
                    antq.api/upgrade-deps! (fn [& _] {})
                    util/shell             (fn [& _] nil)
                    task-bin/ensure-binary!
                    (fn [& _]
                      (throw (ex-info "should not install clj-kondo in default mode" {})))]
        (is (nil? (tasks-update/update)))))))

(deftest update-files-only-writes-workflow-and-refreshes-kondo
  (with-temp-dir [dir]
    (write-file dir "deps.edn" "{:deps {org.clojure/clojure {:mvn/version \"1.12.0\"}}}\n")
    (with-user-dir dir
      (with-redefs [task-bin/ensure-binary! (fn [tool version]
                                              (is (= :clj-kondo tool))
                                              (is (nil? version))
                                              "clj-kondo")
                    util/shell              (fn [& args]
                                              (is (= ["clj-kondo"
                                                      "--parallel"
                                                      "--dependencies"
                                                      "--copy-configs"
                                                      "--lint"
                                                      (System/getProperty "java.class.path")]
                                                     (vec args))))]
        (tasks-update/update "--files-only")))
    (is (str/includes? (slurp (io/file dir ".github/workflows/code-quality.yml"))
                       "name: code quality"))
    (is (str/includes? (slurp (io/file dir ".github/workflows/code-quality.yml"))
                       "actions/setup-java@v4"))
    (is (str/includes? (slurp (io/file dir ".github/workflows/code-quality.yml"))
                       "distribution: temurin"))
    (is (str/includes? (slurp (io/file dir ".github/workflows/code-quality.yml"))
                       "java-version: '25'"))
    (is (str/includes? (slurp (io/file dir ".github/workflows/code-quality.yml"))
                       "clojure -M:run update --cache-only"))
    (is (str/includes? (slurp (io/file dir ".github/workflows/code-quality.yml"))
                       "DeLaGuardo/setup-clojure@13.2"))
    (is (str/includes? (slurp (io/file dir ".github/workflows/code-quality.yml"))
                       "clojure -M:run lint"))
    (is (str/includes? (slurp (io/file dir ".github/workflows/code-quality.yml"))
                       "clojure -M:run format"))
    (is (.exists (io/file dir ".clj-kondo")))))

(deftest update-cache-only-refreshes-kondo-without-copying-configs
  (with-temp-dir [dir]
    (write-file dir "deps.edn" "{:deps {org.clojure/clojure {:mvn/version \"1.12.0\"}}}\n")
    (with-user-dir dir
      (with-redefs [task-bin/ensure-binary! (fn [tool version]
                                              (is (= :clj-kondo tool))
                                              (is (nil? version))
                                              "clj-kondo")
                    util/shell              (fn [& args]
                                              (is (= ["clj-kondo"
                                                      "--parallel"
                                                      "--dependencies"
                                                      "--lint"
                                                      (System/getProperty "java.class.path")]
                                                     (vec args))))]
        (tasks-update/update "--cache-only")))
    (is (.exists (io/file dir ".clj-kondo")))))

(deftest publish-skips-current-version-already-on-clojars
  (let [deploy-opts (atom nil)]
    (with-redefs [util/read-config                (constantly {:biff.tasks/group-name       "com.biffweb"
                                                               :biff.tasks/lib-name         "ring"
                                                               :biff.tasks/lib-version      "1.2.3"
                                                               :biff.tasks/clojars-username "alice"
                                                               :biff.tasks/clojars-secret   (constantly "token")
                                                               :biff.tasks/pom-license      {:name "MIT" :url "https://example.com/mit"}
                                                               :biff.tasks/pom-scm          {:connection          "scm:git:git://example.com/ring.git"
                                                                                             :developerConnection "scm:git:ssh://git@example.com/ring.git"
                                                                                             :tag                 "HEAD"
                                                                                             :url                 "https://example.com/ring"}})
                  hato.client/get                 (fn [_url _opts]
                                                    {:status 200
                                                     :body   "{\"latest_version\":\"1.2.3\",\"recent_versions\":[]}"})
                  deps-deploy.deps-deploy/deploy* #(reset! deploy-opts %)]
      (publish/publish))
    (is (nil? @deploy-opts))))

(deftest publish-builds-pom-with-released-biff-dependencies
  (with-temp-dir [dir]
    (let [project     (io/file dir "ring")
          fx          (io/file dir "fx")
          deploy-opts (atom nil)]
      (.mkdirs project)
      (.mkdirs fx)
      (write-file project "deps.edn"
                  "{:paths [\"src\" \"resources\"]\n :deps {org.clojure/clojure {:mvn/version \"1.12.5\"}\n        com.biffweb/fx {:local/root \"../fx\"}}}\n")
      (write-file project "src/com/biffweb/ring.clj"
                  "(ns com.biffweb.ring)\n(defn hello [] :ok)\n")
      (write-file project "resources/example.txt" "hello\n")
      (write-file fx "deps.edn"
                  "{:paths [\"src\"]}\n")
      (write-file fx "src/com/biffweb/fx.clj"
                  "(ns com.biffweb.fx)\n")
      (with-user-dir project
        (with-redefs [util/read-config                (constantly {:biff.tasks/group-name       "com.biffweb"
                                                                   :biff.tasks/lib-name         "ring"
                                                                   :biff.tasks/lib-version      "1.2.3"
                                                                   :biff.tasks/monorepo         true
                                                                   :biff.tasks/clojars-username "alice"
                                                                   :biff.tasks/clojars-secret   (constantly "token")
                                                                   :biff.tasks/pom-license      {:name "MIT" :url "https://example.com/mit"}
                                                                   :biff.tasks/pom-scm          {:connection          "scm:git:git://example.com/ring.git"
                                                                                                 :developerConnection "scm:git:ssh://git@example.com/ring.git"
                                                                                                 :tag                 "HEAD"
                                                                                                 :url                 "https://example.com/ring"}})
                      hato.client/get                 (fn [_url _opts] {:status 404 :body ""})
                      deps-deploy.deps-deploy/deploy* #(reset! deploy-opts %)]
          (publish/publish)))
      (let [{:keys [repository]} @deploy-opts
            artifact             (.getPath (io/file project "target/ring-1.2.3.jar"))
            pom-content          (slurp (io/file project "target/classes/META-INF/maven/com.biffweb/ring/pom.xml"))]
        (is (.exists (io/file artifact)))
        (is (= "alice" (get-in repository ["clojars" :username])))
        (is (= "token" (get-in repository ["clojars" :password])))
        (is (str/includes? pom-content "<artifactId>ring</artifactId>"))
        (is (str/includes? pom-content "<groupId>com.biffweb</groupId>"))
        (is (str/includes? pom-content "<artifactId>fx</artifactId>"))
        (is (str/includes? pom-content "<name>MIT</name>"))
        (is (str/includes? pom-content "<connection>scm:git:git://example.com/ring.git</connection>"))
        (is (str/includes? pom-content "<version>1.2.3</version>"))))))

(deftest publish-skips-local-root-rewrites-without-monorepo
  (with-temp-dir [dir]
    (let [project     (io/file dir "ring")
          fx          (io/file dir "fx")
          deploy-opts (atom nil)]
      (.mkdirs project)
      (.mkdirs fx)
      (write-file project "deps.edn"
                  "{:paths [\"src\"]\n :deps {org.clojure/clojure {:mvn/version \"1.12.5\"}\n        com.biffweb/fx {:local/root \"../fx\"}}}\n")
      (write-file project "src/com/biffweb/ring.clj"
                  "(ns com.biffweb.ring)\n(defn hello [] :ok)\n")
      (write-file fx "deps.edn"
                  "{:paths [\"src\"]}\n")
      (write-file fx "src/com/biffweb/fx.clj"
                  "(ns com.biffweb.fx)\n")
      (with-user-dir project
        (with-redefs [util/read-config                (constantly {:biff.tasks/group-name       "com.biffweb"
                                                                   :biff.tasks/lib-name         "ring"
                                                                   :biff.tasks/lib-version      "1.2.3"
                                                                   :biff.tasks/clojars-username "alice"
                                                                   :biff.tasks/clojars-secret   (constantly "token")
                                                                   :biff.tasks/pom-license      {:name "MIT" :url "https://example.com/mit"}
                                                                   :biff.tasks/pom-scm          {:connection          "scm:git:git://example.com/ring.git"
                                                                                                 :developerConnection "scm:git:ssh://git@example.com/ring.git"
                                                                                                 :tag                 "HEAD"
                                                                                                 :url                 "https://example.com/ring"}})
                      hato.client/get                 (fn [_url _opts] {:status 404 :body ""})
                      deps-deploy.deps-deploy/deploy* #(reset! deploy-opts %)]
          (publish/publish)))
      (let [pom-content (slurp (io/file project "target/classes/META-INF/maven/com.biffweb/ring/pom.xml"))]
        (is (not (str/includes? pom-content "<artifactId>fx</artifactId>")))))))

(deftest publish-only-rewrites-matching-group-in-monorepo
  (with-temp-dir [dir]
    (let [project     (io/file dir "ring")
          fx          (io/file dir "fx")
          other       (io/file dir "other-lib")
          deploy-opts (atom nil)]
      (.mkdirs project)
      (.mkdirs fx)
      (.mkdirs other)
      (write-file project "deps.edn"
                  "{:paths [\"src\"]\n :deps {org.clojure/clojure {:mvn/version \"1.12.5\"}\n        com.biffweb/fx {:local/root \"../fx\"}\n        somebody/other-lib {:local/root \"../other-lib\"}}}\n")
      (write-file project "src/com/biffweb/ring.clj"
                  "(ns com.biffweb.ring)\n(defn hello [] :ok)\n")
      (write-file fx "deps.edn" "{:paths [\"src\"]}\n")
      (write-file fx "src/com/biffweb/fx.clj" "(ns com.biffweb.fx)\n")
      (write-file other "deps.edn" "{:paths [\"src\"]}\n")
      (write-file other "src/somebody/other_lib.clj" "(ns somebody.other-lib)\n")
      (with-user-dir project
        (with-redefs [util/read-config                (constantly {:biff.tasks/group-name       "com.biffweb"
                                                                   :biff.tasks/lib-name         "ring"
                                                                   :biff.tasks/lib-version      "1.2.3"
                                                                   :biff.tasks/monorepo         true
                                                                   :biff.tasks/clojars-username "alice"
                                                                   :biff.tasks/clojars-secret   (constantly "token")
                                                                   :biff.tasks/pom-license      {:name "MIT" :url "https://example.com/mit"}
                                                                   :biff.tasks/pom-scm          {:connection          "scm:git:git://example.com/ring.git"
                                                                                                 :developerConnection "scm:git:ssh://git@example.com/ring.git"
                                                                                                 :tag                 "HEAD"
                                                                                                 :url                 "https://example.com/ring"}})
                      hato.client/get                 (fn [_url _opts] {:status 404 :body ""})
                      deps-deploy.deps-deploy/deploy* #(reset! deploy-opts %)]
          (publish/publish)))
      (let [pom-content (slurp (io/file project "target/classes/META-INF/maven/com.biffweb/ring/pom.xml"))]
        (is (str/includes? pom-content "<artifactId>fx</artifactId>"))
        (is (not (str/includes? pom-content "<artifactId>other-lib</artifactId>")))))))

(deftest publish-signs-when-passphrase-is-configured
  (let [deploy-opts (atom nil)]
    (with-redefs [util/read-config                             (constantly {:biff.tasks/group-name       "com.biffweb"
                                                                            :biff.tasks/lib-name         "ring"
                                                                            :biff.tasks/lib-version      "1.2.3"
                                                                            :biff.tasks/clojars-username "alice"
                                                                            :biff.tasks/clojars-secret   (constantly "token")
                                                                            :biff.tasks/gpg-passphrase   (constantly "secret")
                                                                            :biff.tasks/pom-license      {:name "MIT" :url "https://example.com/mit"}
                                                                            :biff.tasks/pom-scm          {:connection          "scm:git:git://example.com/ring.git"
                                                                                                          :developerConnection "scm:git:ssh://git@example.com/ring.git"
                                                                                                          :tag                 "HEAD"
                                                                                                          :url                 "https://example.com/ring"}})
                  hato.client/get                              (fn [_url _opts]
                                                                 {:status 200
                                                                  :body   "{\"latest_version\":\"1.2.3\",\"recent_versions\":[]}"})
                  deps-deploy.deps-deploy/deploy               #(reset! deploy-opts %)
                  deps-deploy.deps-deploy/coordinates-from-pom (constantly {:artifact-id "ring"
                                                                            :group-id    "com.biffweb"
                                                                            :version     "1.2.3"})
                  deps-deploy.deps-deploy/deploy*              #(reset! deploy-opts %)
                  deps-deploy.gpg/sign!                        (fn [_passphrase path]
                                                                 (spit (str path ".asc") "")
                                                                 (str path ".asc"))]
      (.mkdirs (io/file "target"))
      (spit "target/pom.xml" "<project/>")
      (spit "target/ring-1.2.3.jar" "")
      (#'publish/deploy! {:biff.tasks/clojars-secret   (constantly "token")
                          :biff.tasks/clojars-username "alice"
                          :biff.tasks/gpg-passphrase   (constantly "secret")}
                         {:jar-file "target/ring-1.2.3.jar"
                          :pom-file "target/pom.xml"}))
    (is (true? (:sign-releases? @deploy-opts)))))

(deftest nrepl-adds-cider-and-refactor-middleware
  (is (some #{#'refactor-nrepl.middleware/wrap-refactor}
            @#'com.biffweb.tasks.nrepl/middleware))
  (is (some #{(requiring-resolve 'cider.nrepl/wrap-apropos)}
            @#'com.biffweb.tasks.nrepl/middleware))
  (let [err (java.io.StringWriter.)]
    (binding [*err* err]
      (apply nrepl.server/default-handler
             @#'com.biffweb.tasks.nrepl/middleware))
    (is (= "" (str err))))
  (with-temp-dir [dir]
    (with-user-dir dir
      (spit ".nrepl-port" "4567")
      (#'com.biffweb.tasks.nrepl/delete-port-file)
      (is (not (.exists (io/file dir ".nrepl-port")))))))

(deftest full-reload-plan-uses-dependency-order-from-source-paths
  (with-temp-dir [dir]
    (write-file dir "src/com/example/util.clj"
                "(ns com.example.util)\n(defn meaning [] 42)\n")
    (write-file dir "src/com/example/app.clj"
                "(ns com.example.app\n  (:require [com.example.util :as util]))\n(defn run [] (util/meaning))\n")
    (write-file dir "test/com/example/app_test.clj"
                "(ns com.example.app-test)\n")
    (is (= ["src/com/example/util.clj"
            "src/com/example/app.clj"]
           (:load-files (reload/full-reload-plan (.getPath dir)
                                                 [(.getPath (io/file dir "src"))]))))))

(deftest soft-deploy-sends-plain-load-file-form-over-nrepl
  (let [commands (atom [])]
    (with-redefs [reload/full-reload-plan (constantly {:load-files ["src/com/example/util.clj"
                                                                    "src/com/example/app.clj"]})
                  util/source-paths       (constantly ["src"])
                  util/ssh-run            (fn [_ctx & args]
                                            (swap! commands conj (vec args)))]
      (#'com.biffweb.tasks.deploy/soft-deploy!
       {:biff.tasks/deployment-name "app"
        :biff.tasks/nrepl-port      7888}))
    (let [[command port-flag port eval-flag form] (first @commands)]
      (is (= ["trench" "-p" "7888" "-e"] [command port-flag port eval-flag]))
      (is (str/includes? form "src/com/example/util.clj"))
      (is (str/includes? form "src/com/example/app.clj"))
      (is (< (.indexOf form "src/com/example/util.clj")
             (.indexOf form "src/com/example/app.clj")))
      (is (str/includes? form "/home/app/repo"))
      (is (not (str/includes? form "com.biffweb.tasks.dev"))))))

(deftest run-tests-captures-structured-failures-and-preserves-reporter-output
  (with-redefs [cognitect.test-runner/test
                (fn [_]
                  (binding [t/*test-out*         *out*
                            t/*report-counters*  (ref {})
                            t/*testing-vars*     [(with-meta (gensym "bad-test")
                                                    {:name 'bad-test})]
                            t/*testing-contexts* ["inside testing"]]
                    (t/report {:type     :fail
                               :message  "broken assertion"
                               :expected '(= 1 2)
                               :actual   '(not (= 1 2))
                               :file     "failing_test.clj"
                               :line     6})
                    (t/report {:type     :error
                               :message  "Unexpected exception"
                               :expected 'nil?
                               :actual   (ex-info "boom" {:x 1})
                               :file     "failing_test.clj"
                               :line     9})
                    {:test 1 :pass 0 :fail 1 :error 1}))]
    (let [output   (java.io.StringWriter.)
          result   (binding [*out* output]
                     (task-test/run-tests))
          failures (:failures result)]
      (is (= 1 (:fail result)))
      (is (= 1 (:error result)))
      (is (= 2 (count failures)))
      (is (= {:type     :fail
              :message  "broken assertion"
              :expected '(= 1 2)
              :actual   '(not (= 1 2))
              :file     "failing_test.clj"
              :line     6}
             (select-keys (first failures) [:type :message :expected :actual :file :line])))
      (is (= {:type     :error
              :message  "Unexpected exception"
              :expected 'nil?
              :file     "failing_test.clj"
              :line     9}
             (select-keys (second failures) [:type :message :expected :file :line])))
      (is (= "boom" (ex-message (:actual (second failures)))))
      (is (= {:x 1} (ex-data (:actual (second failures)))))
      (is (str/includes? (str output) "FAIL in"))
      (is (str/includes? (str output) "ERROR in"))
      (is (str/includes? (str output) "expected: (= 1 2)")))))

(deftest dev-process-changes-runs-format-eval-lint-test-and-records-step-results
  (let [statuses (atom [])
        steps    (atom [])
        lint-ex  (ex-info "lint failed" {:exit 3})
        state    (#'dev-task/watcher-state)]
    (with-redefs [com.biffweb.tasks.dev/write-status!   #(swap! statuses conj %)
                  com.biffweb.tasks.format/format-paths (constantly ["src/app.clj"])
                  com.biffweb.tasks.format/format       (fn []
                                                          (swap! steps conj :format))
                  com.biffweb.tasks.reload/refresh!     (fn [_]
                                                          (swap! steps conj :eval)
                                                          nil)
                  com.biffweb.tasks.lint/lint           (fn []
                                                          (swap! steps conj :lint)
                                                          (throw lint-ex))
                  com.biffweb.tasks.test/run-tests      (fn []
                                                          (swap! steps conj :test)
                                                          {:fail     1
                                                           :error    0
                                                           :failures [{:type     :fail
                                                                       :var      ["bad-test"]
                                                                       :expected "(= 1 2)"
                                                                       :actual   "(not (= 1 2))"}]})]
      (#'dev-task/process-changes! state))
    (is (= [:format :eval :lint :test] @steps))
    (is (= 2 (count @statuses)))
    (is (= :running (:status (first @statuses))))
    (is (= :lint-failure (:status (second @statuses))))
    (is (= {:status :ok} (:format (second @statuses))))
    (is (= {:status :ok :result nil} (:eval (second @statuses))))
    (is (= :failure (get-in (second @statuses) [:lint :status])))
    (is (= lint-ex (get-in (second @statuses) [:lint :result])))
    (is (= {:status :failure
            :result {:fail     1
                     :error    0
                     :failures [{:type     :fail
                                 :var      ["bad-test"]
                                 :expected "(= 1 2)"
                                 :actual   "(not (= 1 2))"}]}}
           (:test (second @statuses))))
    (is (instance? java.time.Instant (:started-at (first @statuses))))
    (is (instance? java.time.Instant (:started-at (second @statuses))))
    (is (instance? java.time.Instant (:finished-at (second @statuses))))))

(deftest dev-process-changes-skips-tests-when-eval-fails
  (let [statuses (atom [])
        steps    (atom [])
        eval-ex  (ex-info "eval failed" {:ns 'app})
        state    (#'dev-task/watcher-state)]
    (with-redefs [com.biffweb.tasks.dev/write-status!   #(swap! statuses conj %)
                  com.biffweb.tasks.format/format-paths (constantly ["src/app.clj"])
                  com.biffweb.tasks.format/format       (fn []
                                                          (swap! steps conj :format))
                  com.biffweb.tasks.reload/refresh!     (fn [_]
                                                          (swap! steps conj :eval)
                                                          eval-ex)
                  com.biffweb.tasks.lint/lint           (fn []
                                                          (swap! steps conj :lint))
                  com.biffweb.tasks.test/run-tests      (fn []
                                                          (swap! steps conj :test)
                                                          {:fail 0 :error 0 :failures []})]
      (#'dev-task/process-changes! state))
    (is (= [:format :eval :lint] @steps))
    (is (= :eval-failure (:status (second @statuses))))
    (is (= {:status :failure :result eval-ex}
           (:eval (second @statuses))))
    (is (= {:status :skipped :reason :eval-failure}
           (:test (second @statuses))))))

(deftest dev-ignores-watcher-events-during-suppression-window
  (let [flushes (atom 0)
        state   (atom {:ignore-events-until Long/MAX_VALUE
                       :processing?         false
                       :rerun-requested?    false})]
    (#'dev-task/handle-watch-event! state #(swap! flushes inc) {:type :modify})
    (is (zero? @flushes))
    (swap! state assoc :ignore-events-until 0)
    (#'dev-task/handle-watch-event! state #(swap! flushes inc) {:type :modify})
    (is (= 1 @flushes))))

(deftest dev-status-file-round-trips-without-default-reader
  (let [status-str (#'dev-task/status->string
                    {:status     :eval-failure
                     :started-at "2026-05-27T00:00:00Z"
                     :eval       {:status :failure
                                  :result (ex-info "boom" {:x 1})}})
        status     (edn/read-string status-str)]
    (is (= :eval-failure (:status status)))
    (is (= "2026-05-27T00:00:00Z" (:started-at status)))
    (is (= :failure (get-in status [:eval :status])))
    (is (= "boom" (get-in status [:eval :result :cause])))
    (is (= {:x 1} (get-in status [:eval :result :data])))
    (is (= 'clojure.lang.ExceptionInfo
           (get-in status [:eval :result :via 0 :type])))))
