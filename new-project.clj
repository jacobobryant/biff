(ns com.biffweb.new-project
  (:require [clojure.java.shell :as shell]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def repo-url "https://github.com/jacobobryant/biff")

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

(defn fetch-refs []
  (-> (sh "git" "ls-remote" (str repo-url ".git"))
      (str/split #"\s+")
      (->> (partition 2)
           (map (comp vec reverse))
           (into {}))))

(defn die [& message]
  (binding [*out* *err*]
    (apply println message)
    (System/exit 1)))

(defn shell-expand [s]
  (try
    (sh "bash" "-c" (str "echo -n " s))
    (catch Exception e
      s)))

(defn -main
  ([] (-main "release"))
  ([branch]
   (let [ref->commit (fetch-refs)
         commit (ref->commit (str "refs/heads/" branch))
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
                        :git/sha (subs commit 0 7)
                        :git/tag tag}
                       {:git/url repo-url
                        :git/sha commit})
         dir (->> (prompt "Enter name for project directory: ")
                  shell-expand
                  (io/file))
         main-ns (prompt "Enter main namespace (e.g. com.example): ")
         tmp (io/file dir "tmp")
         starter (io/file tmp "biff" "starter")]
     (io/make-parents (io/file tmp "_"))
     (sh "git" "clone" "--single-branch" "--branch" branch repo-url :dir tmp)
     (doseq [src (->> (file-seq starter)
                      (filter #(.isFile %)))
             :let [relative (-> (.getPath src)
                                (str/replace #"\\" "/")
                                (str/replace-first #".*?biff/starter/" "")
                                (str/replace "com/example" (ns->path main-ns)))
                   dest (io/file dir relative)]]
       (io/make-parents dest)
       (spit dest
             (-> src
                 slurp
                 (str/replace "com.example" main-ns)
                 (str/replace "{:local/root \"..\"}" (pr-str coordinates))
                 (str/replace "{:local/root \"../libs/tasks\"}"
                              (pr-str (assoc coordinates :deps/root "libs/tasks"))))))
     (rmrf tmp)
     (io/make-parents dir "target/resources/_")
     (println)
     (println "Your project is ready. Run the following commands to get started:")
     (println)
     (println "  cd" (.getPath dir))
     (println "  git init")
     (println "  git add .")
     (println "  git commit -m \"First commit\"")
     (println "  clj -M:dev dev")
     (println)
     (println "Run `clj -M:dev --help` for a list of available commands.")
     (println "(Consider adding `alias biff='clj -M:dev'` to your .bashrc)")
     (println)
     (System/exit 0))))

;; Workaround since *command-line-args* now includes options passed to bb. The docs now tell people
;; to run this script with clj instead of bb, but it does still work with bb.
(apply -main (cond->> *command-line-args*
               (= "-e" (first *command-line-args*)) (drop 2)))
