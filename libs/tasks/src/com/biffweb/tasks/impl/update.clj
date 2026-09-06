(ns com.biffweb.tasks.impl.update
  (:refer-clojure :exclude [update])
  (:require [com.biffweb.tasks.impl.lint :as tasks-lint]
            [com.biffweb.tasks.impl.util :as util]))

(def ^:private valid-flags #{"--deps-only"
                             "--clj-kondo-files-only"})

(defn- upgradeable-deps [{:keys [deps aliases]}]
  (->> (concat deps (mapcat :extra-deps (vals aliases)))
       (remove (fn [[_dep coord]] (contains? coord :local/root)))
       (into {})))

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
      (util/update-clj-kondo-cache!
       (tasks-lint/ensure-clj-kondo-binary! clj-kondo-version)
       (if deps-updated
         (util/refreshed-classpath)
         (System/getProperty "java.class.path"))))))
