(ns com.biffweb.tasks.impl.reload
  "Adapted from clojure.tools.namespace.repl. Licensed under EPL 1.0."
  (:require [clojure.java.io :as io]
            [clojure.repl :as repl]
            [clojure.string :as str]
            [clojure.tools.namespace.dir :as dir]
            [clojure.tools.namespace.file :as ns-file]
            [clojure.tools.namespace.reload :as reload]
            [clojure.tools.namespace.track :as track]
            [com.biffweb.tasks.impl.util :as util]))

(defonce ^:private global-tracker (atom (track/tracker)))

(defn- load-disabled? [sym]
  (false? (:clojure.tools.namespace.repl/load (meta (find-ns sym)))))

(defn- remove-disabled [tracker]
  (update tracker ::track/load #(remove load-disabled? %)))

(defn- print-pending-reloads [tracker]
  (prn :reloading (seq (::track/load tracker))))

(defn- print-and-return [tracker]
  (if-let [e (::reload/error tracker)]
    (do
      (when (thread-bound? #'*e)
        (set! *e e))
      (prn :error-while-loading (::reload/error-ns tracker))
      (repl/pst e)
      e)
    (doto :ok prn)))

(defn- classpath-entries []
  (->> (str/split (System/getProperty "java.class.path")
                  (re-pattern
                   (java.util.regex.Pattern/quote
                    java.io.File/pathSeparator)))
       (mapv #(.getCanonicalPath (io/file %)))
       set))

(defn- classpath-directories [directories]
  (let [entries (classpath-entries)]
    (filterv #(entries (.getCanonicalPath (io/file %))) directories)))

(defn full-reload-plan
  "Returns all the source files from this project in dependency order. Used by
   the soft-deploy task to evaluate files on the production server without that
   process needing to have c.t.n.r on the classpath: we compute the plan locally
   but execute it on the server."
  [project-root directories]
  (let [tracker  (-> (track/tracker)
                     (dir/scan-dirs directories {:add-all? true})
                     remove-disabled)
        ns->file (into {}
                       (map (fn [[file ns-sym]]
                              [ns-sym file]))
                       (::ns-file/filemap tracker))]
    {:load-files (mapv #(util/relative-path project-root (ns->file %))
                       (::track/load tracker))}))

(defn refresh
  "Similar to c.t.n.r/refresh but doesn't unload namespaces first."
  []
  (let [directories  (classpath-directories (util/src-paths))
        new-tracker  (-> (dir/scan-dirs @global-tracker directories)
                         remove-disabled
                         (assoc ::track/unload []))
        _            (print-pending-reloads new-tracker)
        new-tracker  (reload/track-reload (assoc new-tracker ::track/unload []))
        refresh-exit (print-and-return new-tracker)]
    (reset! global-tracker new-tracker)
    refresh-exit))
