(ns com.biffweb.tasks.impl.init-test
  (:require [babashka.fs :as fs]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is]]
            [com.biffweb.tasks.impl.init :as init]
            [com.biffweb.tasks.impl.update :as tasks-update]
            [com.biffweb.tasks.impl.util :as util]))

(deftest git-initialization-confirmation
  (doseq [answer   ["\n" "y\n" "YES\n" "n\n" ""]
          metadata [:directory :file]]
    (let [root     (fs/create-temp-dir)
          git-path (io/file (str root) ".git")
          calls    (atom [])
          accept?  (contains? #{"\n" "y\n" "YES\n"} answer)]
      (try
        (if (= metadata :directory)
          (do
            (fs/create-dir git-path)
            (spit (io/file git-path "config") "original"))
          (spit git-path "gitdir: elsewhere"))
        (with-redefs [util/project-root  (constantly (io/file (str root)))
                      util/shell-inherit (fn [& args]
                                           (is (not (fs/exists? git-path)))
                                           (swap! calls conj (vec args)))]
          (let [output (with-in-str answer
                         (with-out-str
                           (#'init/initialize-git-repository!)))]
            (is (re-find #"\(Y/n\)" output))))
        (is (= (not accept?) (fs/exists? git-path)))
        (is (= (if accept?
                 (mapv #(into ["git" "-C" (str root)] %)
                       [["init"] ["add" "."] ["commit" "-m" "First commit"]])
                 [])
               @calls))
        (finally
          (fs/delete-tree root))))))

(deftest git-initialization-when-namespace-needs-init
  (doseq [[needs-rename? renamed?] [[false false] [true false] [true true]]
          skip-project-files?      [false true]]
    (let [calls (atom [])]
      (with-redefs-fn
        {#'util/read-config
         (constantly {:biff.tasks/main-ns            'com.example
                      :biff.tasks/skip-project-files skip-project-files?})

         #'init/new-project?            (constantly needs-rename?)
         #'init/rewrite-main-namespace! (fn []
                                          (swap! calls conj :rename)
                                          renamed?)
         #'init/ensure-config-files     #(swap! calls conj :config)

         #'init/ensure-task-binaries-installed!
         (fn [_] (swap! calls conj :binaries))

         #'init/ensure-clj-kondo-cache! #(swap! calls conj :update)
         #'init/ensure-biff-gitignore!  #(swap! calls conj :gitignore)
         #'init/ensure-docs!            #(swap! calls conj :docs)
         #'init/ensure-skills!          #(swap! calls conj :skills)

         #'init/initialize-git-repository! #(swap! calls conj :git)
         #'util/ensure-paths!              #(swap! calls conj :paths)}
        init/init)
      (is (= (cond-> []
               needs-rename? (into [:rename :git])
               true (conj :update)
               (not skip-project-files?) (into [:gitignore :docs :skills])
               true (into [:config :binaries :paths]))
             @calls)))))

(deftest clj-kondo-cache-is-initialized-once
  (doseq [cache-exists? [false true]]
    (let [root  (fs/create-temp-dir)
          calls (atom [])]
      (try
        (when cache-exists?
          (fs/create-dirs (fs/path root ".clj-kondo/.cache")))
        (with-redefs [util/project-root (constantly (io/file (str root)))

                      tasks-update/update
                      (fn [& args] (swap! calls conj (vec args)))]
          (#'init/ensure-clj-kondo-cache!))
        (is (= (if cache-exists?
                 []
                 [["--clj-kondo-files-only"]])
               @calls))
        (finally
          (fs/delete-tree root))))))

(deftest biff-directory-is-gitignored
  (doseq [[existing expected] [[nil "*\n"]
                               ["custom" "*\n"]
                               ["custom\n*\n" "*\n"]]]
    (let [root (fs/create-temp-dir)
          file (io/file (str root) ".biff/.gitignore")]
      (try
        (when existing
          (io/make-parents file)
          (spit file existing))
        (with-redefs [util/project-root (constantly (io/file (str root)))]
          (#'init/ensure-biff-gitignore!))
        (is (= expected (slurp file)))
        (finally
          (fs/delete-tree root))))))

(deftest skills-are-synchronized
  (let [root   (fs/create-temp-dir)
        source (fs/path root "resources/com/biffweb/tasks/skills")
        dest   (fs/path root "project/.agents/skills")]
    (try
      (fs/create-dirs (fs/path source "one"))
      (spit (io/file (str source) "one/SKILL.md") "new")
      (fs/create-dirs (fs/path dest "one"))
      (spit (io/file (str dest) "one/SKILL.md") "old")
      (spit (io/file (str dest) "unchanged") "same")
      (.setLastModified (io/file (str dest) "unchanged") 1000)
      (spit (io/file (str source) "unchanged") "same")
      (spit (io/file (str dest) "unrelated") "keep")
      (with-redefs [util/project-root
                    (constantly (io/file (str root) "project"))

                    io/resource
                    (fn [path]
                      (when (= path "com/biffweb/tasks/skills")
                        (.toURL (.toUri source))))]
        (#'init/ensure-skills!))
      (is (= "new" (slurp (io/file (str dest) "one/SKILL.md"))))
      (is (= 1000 (.lastModified (io/file (str dest) "unchanged"))))
      (is (= "keep" (slurp (io/file (str dest) "unrelated"))))
      (finally
        (fs/delete-tree root)))))

(deftest jar-resources-include-repository-docs
  (let [root       (fs/create-temp-dir)
        tasks-root (fs/path root "libs/tasks")]
    (try
      (fs/create-dirs (fs/path root ".git"))
      (fs/create-dirs tasks-root)
      (spit (io/file (str tasks-root) "deps.edn") "{}")
      (fs/create-dirs (fs/path root "docs"))
      (spit (io/file (str root) "README.md") "root")
      (spit (io/file (str root) "docs/guide.md") "guide")
      (spit (io/file (str root) "AGENTS.md") "excluded")
      (with-redefs [util/project-root (constantly (io/file (str root)))]
        (init/build-jar-resources))
      (let [dest (io/file (str tasks-root)
                          "target/resources/com/biffweb/tasks/docs")]
        (is (= "root" (slurp (io/file dest "README.md"))))
        (is (= "guide" (slurp (io/file dest "docs/guide.md"))))
        (is (not (.exists (io/file dest "AGENTS.md"))))
        (is (= 64 (count (slurp (io/file dest "hash.txt"))))))
      (finally
        (fs/delete-tree root)))))

(deftest docs-are-refreshed-when-hash-changes
  (let [root    (fs/create-temp-dir)
        source  (fs/path root "resources/com/biffweb/tasks/docs")
        project (fs/path root "project")]
    (try
      (fs/create-dirs source)
      (spit (io/file (str source) "hash.txt") "new-hash")
      (spit (io/file (str source) "README.md") "new docs")
      (fs/create-dirs (fs/path project ".biff/docs"))
      (spit (io/file (str project) ".biff/docs/hash.txt") "old-hash")
      (spit (io/file (str project) ".biff/docs/removed.md") "old docs")
      (with-redefs [util/project-root (constantly (io/file (str project)))

                    io/resource
                    (fn [path]
                      (when (= path "com/biffweb/tasks/docs/hash.txt")
                        (.toURL (.toUri (fs/path source "hash.txt")))))]
        (#'init/ensure-docs!))
      (is (= "new docs"
             (slurp (io/file (str project) ".biff/docs/README.md"))))
      (is (not (.exists
                (io/file (str project) ".biff/docs/removed.md"))))
      (finally
        (fs/delete-tree root)))))
