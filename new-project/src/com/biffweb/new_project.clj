(ns com.biffweb.new-project
  (:require [clojure.java.shell :as shell]
            [clojure.java.io :as io]
            [clojure.string :as str])
  (:import java.security.SecureRandom))

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

(defn biff-coordinates [sha]
  ;; TODO switch to mvn
  (str ":git/url \"https://github.com/jacobobryant/biff\" :sha \"" sha "\""))

(defn -main []
  ;; TODO switch dev to HEAD
  (let [sha (-> (sh "git" "ls-remote" "https://github.com/jacobobryant/biff.git" "dev")
                (str/split #"\s+")
                first)
        coordinates (biff-coordinates sha)
        cookie-secret (new-secret 16)
        jwt-secret (new-secret 32)
        dir (->> (prompt "Enter name for project directory: ")
                 (str "echo -n ")
                 (sh "bash" "-c")
                 (io/file))
        main-ns (prompt "Enter main namespace (e.g. com.example): ")
        tmp (io/file dir "tmp")
        example (io/file tmp "biff/example")]
    (io/make-parents (io/file tmp "_"))
    (sh "git" "clone"
        ;; TODO remove --branch dev
        "--branch" "dev"
        "https://github.com/jacobobryant/biff"
        :dir tmp)
    (doseq [src (->> (file-seq example)
                     (filter #(.isFile %)))
            :let [relative (-> (.getPath src)
                               (str/replace-first #".*?example/" "")
                               (str/replace "com/example" (ns->path main-ns)))
                  dest (io/file dir relative)]]
      (io/make-parents dest)
      (spit dest
            (-> src
                slurp
                (str/replace "com.example" main-ns)
                (str/replace #"cookie-secret nil" (str "cookie-secret " (pr-str cookie-secret)))
                (str/replace #"jwt-secret nil" (str "jwt-secret " (pr-str jwt-secret)))
                (str/replace ":local/root \"..\"" coordinates))))
    (.renameTo (io/file dir "config.edn.TEMPLATE") (io/file dir "config.edn"))
    (.renameTo (io/file dir "config.sh.TEMPLATE") (io/file dir "config.sh"))
    (.setExecutable (io/file dir "task") true)
    (rmrf tmp)
    (println)
    (println "Your project is ready. Run the following commands to get started:")
    (println)
    (println "  cd" (.getPath dir))
    (println "  git init")
    (println "  ./task dev")
    (System/exit 0)))
