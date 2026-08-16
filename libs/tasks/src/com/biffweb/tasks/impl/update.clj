(ns com.biffweb.tasks.impl.update
  (:refer-clojure :exclude [update])
  (:require [clojure.java.io :as io]
            [clojure.java.shell :as sh]
            [clojure.string :as str]
            [com.biffweb.tasks.impl.lint :as tasks-lint]
            [com.biffweb.tasks.impl.util :as util]))

(def ^:private valid-flags #{"--deps-only"
                             "--clj-kondo-files-only"})

(def ^:private max-classpath-batch-length 16000)

(defn- upgradeable-deps [{:keys [deps aliases]}]
  (->> (concat deps (mapcat :extra-deps (vals aliases)))
       (remove (fn [[_dep coord]] (contains? coord :local/root)))
       (into {})))

(defn- refreshed-classpath []
  (let [{:keys [exit out err] :as result} (sh/sh "clojure" "-Spath")
        classpath                         (str/trim out)]
    (when (or (not= 0 exit) (str/blank? classpath))
      (throw (ex-info "Failed to refresh classpath"
                      {:exit exit :err err :result result})))
    classpath))

(defn- classpath-batches [classpath]
  (let [separator java.io.File/pathSeparator]
    (reduce
     (fn [batches path]
       (let [current  (peek batches)
             combined (str current (when-not (str/blank? current) separator)
                           path)]
         (if (<= (count combined) max-classpath-batch-length)
           (conj (pop batches) combined)
           (conj batches path))))
     [""]
     (str/split classpath
                (re-pattern (java.util.regex.Pattern/quote separator))))))

(defn- update-clj-kondo-cache! [version deps-updated]
  (.mkdirs (io/file (util/project-root) ".clj-kondo"))
  (let [binary    (tasks-lint/ensure-clj-kondo-binary! version)
        classpath (if deps-updated
                    (refreshed-classpath)
                    (System/getProperty "java.class.path"))]
    (doseq [batch (classpath-batches classpath)]
      (util/shell binary
                  "--parallel" "--dependencies" "--copy-configs"
                  "--lint" batch))))

(defn- update-deps! []
  (let [outdated-deps (requiring-resolve 'antq.api/outdated-deps)
        upgrade-deps! (requiring-resolve 'antq.api/upgrade-deps!)
        deps-edn      (util/read-deps-edn)
        outdated      (outdated-deps (upgradeable-deps deps-edn)
                                     {:file-path    "deps.edn"
                                      :repositories (:mvn/repos deps-edn)
                                      :no-changes   true})
        need-update   (boolean (not-empty outdated))]
    (when need-update
      (upgrade-deps! (mapv #(hash-map :file "deps.edn" :dependency %)
                           outdated)))
    need-update))

(defn- validate-flags! [args]
  (let [unknown (remove valid-flags args)
        flags   (set args)]
    (when (seq unknown)
      (throw (ex-info "Unknown update flags" {:args args :unknown unknown})))
    (when (= 2 (count flags))
      (throw (ex-info "Choose at most one update mode flag."
                      {:args args :flags flags})))
    flags))

(defn update [& args]
  (let [clj-kondo-version (:biff.tasks/clj-kondo-version (util/read-config))
        flags             (validate-flags! args)
        deps-updated      (when-not (flags "--clj-kondo-files-only")
                            (update-deps!))]
    (when-not (flags "--deps-only")
      (update-clj-kondo-cache! clj-kondo-version deps-updated))))
