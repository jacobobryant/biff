(ns com.biffweb.tasks.impl.init
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [com.biffweb.tasks.impl.css :as css]
            [com.biffweb.tasks.impl.format :as tasks-format]
            [com.biffweb.tasks.impl.lint :as tasks-lint]
            [com.biffweb.tasks.impl.update :as tasks-update]
            [com.biffweb.tasks.impl.util :as util]))

(def ^:private template-main-ns 'com.example)

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

(defn- needs-main-namespace-init? [configured-main-ns]
  (or (= configured-main-ns template-main-ns)
      (.exists (io/file (util/project-root)
                        "src"
                        (str (ns->path template-main-ns) ".clj")))))

(defn- ensure-task-binaries-installed!
  [{:biff.tasks/keys [clj-kondo-version cljfmt-version tailwind-version]}]
  (tasks-format/ensure-cljfmt-binary! cljfmt-version)
  (tasks-lint/ensure-clj-kondo-binary! clj-kondo-version)
  (css/ensure-tailwind-binary! tailwind-version))

(defn init []
  (let [{:biff.tasks/keys [main-ns] :as config}
        (util/read-config '{:select [main-ns
                                     clj-kondo-version
                                     cljfmt-version
                                     tailwind-version]})]
    (when (needs-main-namespace-init? main-ns)
      (rewrite-main-namespace!))
    (ensure-config-files)
    (ensure-task-binaries-installed! config)
    (tasks-update/update "--clj-kondo-files-only")))
