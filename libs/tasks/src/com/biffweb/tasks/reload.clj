(ns com.biffweb.tasks.reload
  (:require [clojure.java.io :as io]
            [clojure.repl :as repl]
            [clojure.string :as str]
            [clojure.tools.namespace.dir :as dir]
            [clojure.tools.namespace.file :as ns-file]
            [clojure.tools.namespace.reload :as reload]
            clojure.tools.namespace.repl
            [clojure.tools.namespace.track :as track]))

(defonce global-tracker (atom (track/tracker)))

;; clojure.tools.namespace.repl/remove-disabled is private, so we hold onto its
;; var directly. It removes namespaces marked as disabled from the tracker before
;; tools.namespace computes load order.
(def remove-disabled #'clojure.tools.namespace.repl/remove-disabled)

(defn- classpath-entries []
  (->> (str/split (System/getProperty "java.class.path")
                  (re-pattern (java.util.regex.Pattern/quote java.io.File/pathSeparator)))
       set))

(defn- classpath-directories [directories]
  (let [entries (classpath-entries)]
    (filterv entries directories)))

(defn- print-pending-reloads [tracker]
  (when-let [reloads (seq (::track/load tracker))]
    (prn :reloading reloads)))

(defn- print-and-return [tracker]
  (if-let [e (::reload/error tracker)]
    (do
      (when (thread-bound? #'*e)
        (set! *e e))
      (prn :error-while-loading (::reload/error-ns tracker))
      (repl/pst e)
      e)
    :ok))

(defn- relative-path [project-root file]
  (-> (.relativize (.toPath (.getCanonicalFile (io/file project-root)))
                   (.toPath (.getCanonicalFile ^java.io.File file)))
      str
      (str/replace "\\" "/")))

(defn full-reload-plan
  "Builds a dependency-ordered list of source files to load from scratch."
  [project-root directories]
  (let [tracker  (-> (track/tracker)
                     (dir/scan-dirs directories {:add-all? true})
                     remove-disabled)
        ns->file (into {}
                       (map (fn [[file ns-sym]]
                              [ns-sym file]))
                       (::ns-file/filemap tracker))]
    {:load-files (mapv #(relative-path project-root (ns->file %))
                       (::track/load tracker))}))

(defn refresh!
  "Reloads changed namespaces using the same tracker-driven tools.namespace flow
  Biff used previously."
  [directories]
  (let [directories  (classpath-directories directories)
        new-tracker  (dir/scan-dirs @global-tracker directories)
        new-tracker  (remove-disabled new-tracker)
        _            (print-pending-reloads new-tracker)
        new-tracker  (reload/track-reload (assoc new-tracker ::track/unload []))
        refresh-exit (print-and-return new-tracker)]
    (reset! global-tracker new-tracker)
    refresh-exit))
