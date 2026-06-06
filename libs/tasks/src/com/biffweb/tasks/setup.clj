(ns com.biffweb.tasks.setup
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [com.biffweb.tasks.generate :as generate]
            [com.biffweb.tasks.impl.bin :as bin]
            [com.biffweb.tasks.update :as tasks-update]
            [com.biffweb.tasks.util :as util]))

(def ^:private template-main-ns "com.example")

(defn- project-root []
  (io/file (System/getProperty "user.dir")))

(defn- prompt [msg]
  (print msg)
  (flush)
  (or (not-empty (read-line))
      (recur msg)))

(defn- ns->path [s]
  (-> s
      (str/replace "-" "_")
      (str/replace "." "/")))

(defn- tracked-files []
  (util/git-ls-files))

(defn- rewrite-main-namespace! [new-main-ns]
  (let [old-path (ns->path template-main-ns)
        new-path (ns->path new-main-ns)
        root     (project-root)
        files    (map (fn [relative-path]
                        [(io/file root relative-path) relative-path])
                      (tracked-files))]
    (doseq [[file relative-path] files]
      (let [dest-path    (str/replace relative-path old-path new-path)
            dest-file    (io/file root dest-path)
            contents     (slurp file)
            new-contents (str/replace contents template-main-ns new-main-ns)]
        (cond
          (not= relative-path dest-path)
          (do
            (io/make-parents dest-file)
            (spit dest-file new-contents)
            (io/delete-file file))

          (not= contents new-contents)
          (spit file new-contents))))
    (doseq [root ["src" "test"]]
      (let [dir (io/file (project-root) root old-path)]
        (loop [dir dir]
          (when (and (.exists dir)
                     (.isDirectory dir)
                     (empty? (seq (.listFiles dir))))
            (io/delete-file dir)
            (when-let [parent (.getParentFile dir)]
              (recur parent))))))))

(defn- needs-main-namespace-setup? []
  (let [configured-main-ns (some-> (util/read-config)
                                   :biff.tasks/main-ns
                                   str)]
    (or (= configured-main-ns template-main-ns)
        (.exists (io/file (project-root) "src" (str (ns->path template-main-ns) ".clj"))))))

(defn- ensure-task-binaries-installed! []
  (let [{:biff.tasks/keys [clj-kondo-version cljfmt-version tailwind-build tailwind-version]}
        (util/read-config)]
    (bin/ensure-local-binary! :cljfmt cljfmt-version)
    (bin/ensure-local-binary! :clj-kondo clj-kondo-version)
    (bin/ensure-local-binary! :tailwindcss
                              tailwind-version
                              (cond-> {}
                                tailwind-build
                                (assoc :asset-name (str "tailwindcss-" tailwind-build))))))

(defn setup
  "Initializes a freshly cloned Biff project."
  [& [main-ns]]
  (when (needs-main-namespace-setup?)
    (let [main-ns (str (or main-ns
                           (prompt "Enter main namespace (e.g. com.example): ")))]
      (rewrite-main-namespace! main-ns)
      (println "Updated the main namespace to" main-ns ".")))
  (generate/ensure-config-files)
  (ensure-task-binaries-installed!)
  (tasks-update/update "--files-only"))
