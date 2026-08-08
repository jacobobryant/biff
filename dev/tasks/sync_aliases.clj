(ns tasks.sync-aliases
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.pprint :as pprint]
            [borkdude.rewrite-edn :as r]
            [com.biffweb.run :as biff.run]))

(def ^:private root-deps-file "deps.edn")

(defn- read-edn [path]
  (-> path slurp edn/read-string))

(defn- library-deps-files []
  (->> (.listFiles (io/file "libs"))
       (filter #(.isDirectory %))
       (map #(io/file % "deps.edn"))
       (filter #(.isFile %))
       (sort-by str)))

(defn- root-path [deps-file path]
  (let [path' (-> deps-file
                  .getParentFile
                  (io/file path)
                  .toPath
                  .normalize
                  str)]
    (.replace path' "\\" "/")))

(defn- root-coord [deps-file coord]
  (cond-> coord
    (:local/root coord) (update :local/root #(root-path deps-file %))))

(defn- merge-dep [deps deps-file [dep coord]]
  (let [coord' (root-coord deps-file coord)]
    (when-some [existing (get deps dep)]
      (when-not (= existing coord')
        (throw (ex-info "Conflicting :run dependencies"
                        {:dependency dep
                         :existing   existing
                         :incoming   coord'
                         :file       (str deps-file)}))))
    (assoc deps dep coord')))

(defn- collected-run-alias []
  (reduce (fn [{:keys [extra-paths extra-deps jvm-opts]} deps-file]
            (let [run (get-in (read-edn deps-file) [:aliases :run])]
              {:extra-paths (into extra-paths
                                  (map #(root-path deps-file %))
                                  (:extra-paths run))
               :extra-deps  (reduce #(merge-dep %1 deps-file %2)
                                    extra-deps (:extra-deps run))
               :jvm-opts    (into jvm-opts (:jvm-opts run))}))
          {:extra-paths #{}
           :extra-deps  {}
           :jvm-opts    #{}}
          (library-deps-files)))

(defn- generated-path? [path]
  (.startsWith path "libs/"))

(defn- value-node [value]
  (r/parse-string
   (binding [*print-namespace-maps* false]
     (with-out-str (pprint/pprint value)))))

(defn sync-aliases []
  (let [contents        (slurp root-deps-file)
        form            (edn/read-string contents)
        current-run     (get-in form [:aliases :run])
        collected       (collected-run-alias)
        root-paths      (remove generated-path?
                                (:extra-paths current-run))
        root-deps       (:extra-deps current-run)
        extra-paths     (->> (concat root-paths
                                     (:extra-paths collected))
                             distinct sort vec)
        extra-deps      (reduce #(merge-dep
                                  %1 (io/file "." root-deps-file) %2)
                                root-deps
                                (:extra-deps collected))
        jvm-opts        (-> (:jvm-opts collected) sort vec)
        changed?        (or (not= extra-paths
                                  (:extra-paths current-run))
                            (not= extra-deps
                                  (:extra-deps current-run))
                            (not= jvm-opts (:jvm-opts current-run)))
        node            (r/parse-string contents)
        extra-deps-node (value-node (into (sorted-map) extra-deps))
        updated         (-> node
                            (r/assoc-in [:aliases :run :extra-paths]
                                        (value-node extra-paths))
                            (r/assoc-in [:aliases :run :extra-deps]
                                        extra-deps-node)
                            (r/assoc-in [:aliases :run :jvm-opts]
                                        (value-node jvm-opts)))
        updated-str     (str updated)]
    (when changed?
      (spit root-deps-file updated-str))
    (biff.run/run-task "format")
    nil))
