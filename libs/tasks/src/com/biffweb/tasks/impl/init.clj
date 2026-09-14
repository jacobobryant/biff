(ns com.biffweb.tasks.impl.init
  (:require [babashka.fs :as fs]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [com.biffweb.tasks.impl.css :as css]
            [com.biffweb.tasks.impl.format :as tasks-format]
            [com.biffweb.tasks.impl.lint :as tasks-lint]
            [com.biffweb.tasks.impl.update :as tasks-update]
            [com.biffweb.tasks.impl.util :as util])
  (:import [java.security MessageDigest]
           [java.util.jar JarFile]))

(def ^:private template-main-ns 'com.example)
(def ^:private docs-resource "com/biffweb/tasks/docs")
(def ^:private skills-resource "com/biffweb/tasks/skills")

(defn- sha256 [values]
  (let [digest (MessageDigest/getInstance "SHA-256")]
    (doseq [value values]
      (.update digest ^bytes value))
    (apply str (map #(format "%02x" %) (.digest digest)))))

(defn- repository-root []
  (or (some #(when (.exists (io/file % ".git")) %)
            (take-while some?
                        (iterate #(.getParentFile %) (util/project-root))))
      (util/project-root)))

;; used to package docs in the biff.tasks jar so the init task can write them to
;; the local project directory.
(defn build-jar-resources []
  (let [root       (.getCanonicalFile (repository-root))
        files      (->> (file-seq root)
                        (filterv #(.isFile %))
                        (filterv #(str/ends-with? (.getName %) ".md"))
                        (remove #(= "AGENTS.md" (.getName %)))
                        (sort-by #(util/relative-path root %)))
        tasks-root (if (.exists (io/file root "libs/tasks/deps.edn"))
                     (io/file root "libs/tasks")
                     root)
        dest       (io/file tasks-root "target/resources" docs-resource)]
    (doseq [file files]
      (let [target (io/file dest (util/relative-path root file))]
        (io/make-parents target)
        (io/copy file target)))
    (io/make-parents (io/file dest "hash.txt"))
    (spit (io/file dest "hash.txt")
          (sha256
           (mapv #(java.nio.file.Files/readAllBytes (.toPath %)) files)))))

(defn- copy-classpath-docs! [url dest]
  (case (.getProtocol url)
    "file"
    (fs/copy-tree (io/file (.toURI url)) dest)

    "jar"
    (let [connection (.openConnection url)
          prefix     (str docs-resource "/")]
      (with-open [jar ^JarFile (.getJarFile connection)]
        (doseq [entry (enumeration-seq (.entries jar))
                :let  [name (.getName entry)]
                :when (and (not (.isDirectory entry))
                           (str/starts-with? name prefix))]
          (let [target (io/file dest (subs name (count prefix)))]
            (io/make-parents target)
            (with-open [in (.getInputStream jar entry)]
              (io/copy in target))))))))

(defn- ensure-docs! []
  (when-some [hash-url (io/resource (str docs-resource "/hash.txt"))]
    (let [url            (java.net.URL. (subs (str hash-url)
                                              0
                                              (- (count (str hash-url))
                                                 (count "/hash.txt"))))
          dest           (io/file (util/project-root) ".biff/docs")
          bundled-hash   (slurp hash-url)
          installed-hash (when (.exists (io/file dest "hash.txt"))
                           (slurp (io/file dest "hash.txt")))]
      (when (not= bundled-hash installed-hash)
        (fs/delete-tree dest)
        (copy-classpath-docs! url dest)))))

(defn- write-if-different! [file bytes]
  (when (or (not (.exists file))
            (not (java.util.Arrays/equals
                  ^bytes bytes
                  ^bytes (java.nio.file.Files/readAllBytes (.toPath file)))))
    (io/make-parents file)
    (with-open [out (io/output-stream file)]
      (.write out ^bytes bytes))))

(defn- ensure-skills! []
  (when-some [url (io/resource skills-resource)]
    (let [dest (io/file (util/project-root) ".agents/skills")]
      (case (.getProtocol url)
        "file"
        (let [source (io/file (.toURI url))]
          (doseq [file  (file-seq source)
                  :when (.isFile file)]
            (write-if-different!
             (io/file dest (util/relative-path source file))
             (java.nio.file.Files/readAllBytes (.toPath file)))))

        "jar"
        (let [connection (.openConnection url)
              prefix     (str skills-resource "/")]
          (with-open [jar ^JarFile (.getJarFile connection)]
            (doseq [entry (enumeration-seq (.entries jar))
                    :let  [name (.getName entry)]
                    :when (and (not (.isDirectory entry))
                               (str/starts-with? name prefix))]
              (with-open [in (.getInputStream jar entry)]
                (write-if-different!
                 (io/file dest (subs name (count prefix)))
                 (.readAllBytes in))))))))))

(defn- ensure-biff-gitignore! []
  (let [file (io/file (util/project-root) ".biff/.gitignore")]
    (io/make-parents file)
    (spit file "*\n")))

(defn- new-secret [length]
  (let [buffer (byte-array length)]
    (.nextBytes (java.security.SecureRandom/getInstanceStrong) buffer)
    (.encodeToString (java.util.Base64/getEncoder) buffer)))

(defn- render-config-template [template-file]
  (-> (slurp template-file)
      (str/replace #"\{\{\s+new-secret\s+(\d+)\s+\}\}"
                   (fn [[_ n]]
                     (new-secret (parse-long n))))))

(defn- ensure-config-files []
  (doseq [[template dest]
          [["resources/TEMPLATE.config.env" "config.env"]
           ["resources/TEMPLATE.config.prod.env" "config.prod.env"]]

          :let  [template (io/file template)
                 dest     (io/file dest)]
          :when (and (.exists template)
                     (not (.exists dest)))]
    (spit dest (render-config-template template))
    (println "Generated" dest)))

(defn- prompt [msg]
  (print msg)
  (flush)
  (or (not-empty (read-line))
      (recur msg)))

(defn- ns->path [s]
  (-> (str s)
      (str/replace "-" "_")
      (str/replace "." "/")))

(defn- delete-empty-directory! [dir]
  (doseq [child (.listFiles dir)
          :when (.isDirectory child)]
    (delete-empty-directory! child))
  (when (empty? (seq (.listFiles dir)))
    (io/delete-file dir)))

(defn- top-level-directories [root files]
  (->> files
       (map #(-> (.toPath root)
                 (.relativize (.toPath %))
                 (.getName 0)
                 str))
       distinct
       (map #(io/file root %))
       (filter #(.isDirectory %))))

(defn- rewrite-main-namespace! []
  (let [new-main-ns    (prompt "Enter main namespace (e.g. com.example): ")
        old-path       (ns->path template-main-ns)
        new-path       (ns->path new-main-ns)
        root           (util/project-root)
        project-files  (util/project-files)
        top-level-dirs (top-level-directories root project-files)
        files          (map (fn [file]
                              [file (util/relative-path root file)])
                            project-files)]
    (doseq [[file relative-path] files]
      (let [dest-path    (str/replace relative-path old-path new-path)
            dest-file    (io/file root dest-path)
            contents     (slurp file)
            new-contents (str/replace contents
                                      (str template-main-ns)
                                      new-main-ns)]
        (cond
          (not= relative-path dest-path)
          (do
            (io/make-parents dest-file)
            (spit dest-file new-contents)
            (io/delete-file file))

          (not= contents new-contents)
          (spit file new-contents))))
    (doseq [dir   top-level-dirs
            child (.listFiles dir)
            :when (.isDirectory child)]
      (delete-empty-directory! child))
    (println (str "Updated the main namespace to " new-main-ns "."))))

(defn- initialize-git-repository! []
  (print (str "Initialize a new git repository "
              "(replaces existing git history)? (Y/n) "))
  (flush)
  (when (#{"" "y" "yes"} (some-> (read-line) str/trim str/lower-case))
    (let [root    (util/project-root)
          git-dir (io/file root ".git")]
      (if (fs/directory? git-dir)
        (fs/delete-tree git-dir)
        (fs/delete-if-exists git-dir))
      (doseq [args [["init"] ["add" "."] ["commit" "-m" "First commit"]]]
        (apply util/shell-inherit "git" "-C" (str root) args)))))

(defn- new-project? [configured-main-ns]
  (or (= configured-main-ns template-main-ns)
      (.exists (io/file (util/project-root)
                        "src"
                        (str (ns->path template-main-ns) ".clj")))))

(defn- ensure-task-binaries-installed!
  [{:biff.tasks/keys [clj-kondo-version cljfmt-version tailwind-version]}]
  (tasks-format/ensure-cljfmt-binary! cljfmt-version)
  (tasks-lint/ensure-clj-kondo-binary! clj-kondo-version)
  (css/ensure-tailwind-binary! tailwind-version))

(defn- ensure-clj-kondo-cache! []
  (when-not (.exists (io/file (util/project-root) ".clj-kondo/.cache"))
    (tasks-update/update "--clj-kondo-files-only")))

(defn init []
  (let [{:biff.tasks/keys [main-ns skip-project-files] :as config}
        (util/read-config {:select '[main-ns
                                     clj-kondo-version
                                     cljfmt-version
                                     tailwind-version
                                     skip-project-files]})

        new-project (new-project? main-ns)]
    (when new-project
      (rewrite-main-namespace!)
      (initialize-git-repository!))
    (ensure-clj-kondo-cache!)
    (when-not skip-project-files
      (ensure-biff-gitignore!)
      (ensure-docs!)
      (ensure-skills!))
    (ensure-config-files)
    (ensure-task-binaries-installed! config)
    (util/ensure-paths!)))
