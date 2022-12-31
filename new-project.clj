(ns com.biffweb.new-project
  (:require [babashka.fs :as fs]
            [clojure.java.shell :as shell]
            [clojure.java.io :as io]
            [clojure.string :as str])
  (:import java.security.SecureRandom))

(def repo-url "https://github.com/jacobobryant/biff")

(defn have-java-11? []
  (cond
   (fs/which "javap")
   (->> (shell/sh "javap" "-verbose" "java.lang.String")
        :out
        (re-find #"major version: (\d+)")
        second
        parse-long
        (<= 55))

   (fs/which "java")
   (->> (shell/sh "java" "-version")
        :err
        (re-find #"version[^\d]+(\d+)")
        second
        parse-long
        (<= 11))

   :else
   false))

(defn sh
  [& args]
  (let [result (apply shell/sh args)]
    (if (= 0 (:exit result))
      (:out result)
      (throw (ex-info (:err result) result)))))

(defn prompt [msg]
  (print msg)
  (flush)
  (or (not-empty (read-line))
      (recur msg)))

(defn ns->path [s]
  (-> s
      (str/replace "-" "_")
      (str/replace "." "/")))

(defn rmrf [file]
  (when (.isDirectory file)
    (run! rmrf (.listFiles file)))
  (io/delete-file file))

(defn new-secret [length]
  (let [buffer (byte-array length)]
    (.nextBytes (SecureRandom.) buffer)
    (.encodeToString (java.util.Base64/getEncoder) buffer)))

(defn fetch-refs []
  (-> (sh "git" "ls-remote" (str repo-url ".git"))
      (str/split #"\s+")
      (->> (partition 2)
           (map (comp vec reverse))
           (into {}))))

(defn die [exit-code & message]
  (binding [*out* *err*]
    (apply println message)
    (System/exit 1 exit-code)))

(defn -main [& [branch]]
  (when-not (have-java-11?)
    (die "Please install Java 11 or higher."))
  (when-not (fs/which "curl")
    (die "`curl` command not found. Please install it. (`scoop install curl` on Windows.)"))
  (let [ref->commit (fetch-refs)
        commit (if-not branch
                 (ref->commit "HEAD")
                 (ref->commit (str "refs/heads/" branch)))
        _ (when-not commit
            (die "Invalid git branch:" branch))
        tag (some-> (filter (fn [[ref_ commit_]]
                              (and (= commit commit_)
                                   (str/starts-with? ref_ "refs/tags/v")))
                            ref->commit)
                    ffirst
                    (str/replace "refs/tags/" ""))
        coordinates (if tag
                      {:git/url repo-url
                       :sha (subs commit 0 7)
                       :tag tag}
                      {:git/url repo-url
                       :sha commit})
        cookie-secret (new-secret 16)
        jwt-secret (new-secret 32)
        dir (->> (prompt "Enter name for project directory: ")
                 (str "echo -n ")
                 (sh "bash" "-c")
                 (io/file))
        main-ns (prompt "Enter main namespace (e.g. com.example): ")
        tmp (io/file dir "tmp")
        example (io/file tmp "biff" "example")]
    (io/make-parents (io/file tmp "_"))
    (if branch
      (sh "git" "clone" "--single-branch" "--branch" branch repo-url :dir tmp)
      (sh "git" "clone" repo-url :dir tmp))
    (doseq [src (->> (file-seq example)
                     (filter #(.isFile %)))
            :let [relative (-> (.getPath src)
                               (str/replace #"\\" "/")
                               (str/replace-first #".*?biff/example/" "")
                               (str/replace "com/example" (ns->path main-ns)))
                  dest (io/file dir relative)]]
      (io/make-parents dest)
      (spit dest
            (-> src
                slurp
                (str/replace "com.example" main-ns)
                (str/replace #"cookie-secret nil" (str "cookie-secret " (pr-str cookie-secret)))
                (str/replace #"jwt-secret nil" (str "jwt-secret " (pr-str jwt-secret)))
                (str/replace "{:local/root \"..\"}" (pr-str coordinates))
                (str/replace "{:local/root \"../../tasks\"}"
                             (pr-str (assoc coordinates :deps/root "tasks"))))))
    (.renameTo (io/file dir "config.edn.TEMPLATE") (io/file dir "config.edn"))
    (rmrf tmp)
    (sh "bb" "--force" "-e" "nil" :dir dir)
    (println)
    (println "Your project is ready. Run the following commands to get started:")
    (println)
    (println "  cd" (.getPath dir))
    (println "  git init")
    (println "  bb dev")
    (println)
    (println "And run `bb tasks` for a list of available commands.")
    (System/exit 0)))

(apply -main *command-line-args*)
