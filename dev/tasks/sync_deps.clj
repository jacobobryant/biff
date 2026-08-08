(ns tasks.sync-deps
  (:require [clojure.edn :as edn]
            [clojure.java.shell :as sh]
            [clojure.string :as str]
            [borkdude.rewrite-edn :as r]
            [com.biffweb.run :as biff.run]))

(def ^:private shared-deps-file "deps/deps.edn")

(defn- read-edn [path]
  (-> path slurp edn/read-string))

(defn- shared-deps []
  (:deps (read-edn shared-deps-file)))

(defn- deps-files []
  (let [{:keys [exit out err]} (sh/sh "git" "ls-files")]
    (when-not (zero? exit)
      (throw (ex-info "git ls-files failed" {:exit exit :err err})))
    (->> out
         str/split-lines
         (filter #(re-find #"(^|/)deps\.edn$" %))
         (remove #{shared-deps-file})
         sort
         vec)))

(defn- dep-paths
  ([form] (dep-paths form []))
  ([form path]
   (cond
     (map? form)
     (mapcat (fn [[k v]]
               (let [path' (conj path k)]
                 (concat
                  (when (and (symbol? k) (map? v))
                    [path'])
                  (dep-paths v path'))))
             form)

     (vector? form)
     (mapcat (fn [[idx v]]
               (dep-paths v (conj path idx)))
             (map-indexed vector form))

     (seq? form)
     (mapcat (fn [[idx v]]
               (dep-paths v (conj path idx)))
             (map-indexed vector form))

     :else
     [])))

(defn- dep-entries [form]
  (mapv (fn [path]
          [(peek path) (get-in form path)])
        (dep-paths form)))

(defn- add-shared-dep [{:keys [shared found] :as state} path [dep coord]]
  (cond
    (contains? shared dep)
    state

    (:local/root coord)
    state

    (contains? found dep)
    (let [{existing-path  :path
           existing-coord :coord} (get found dep)]
      (when-not (= existing-coord coord)
        (throw (ex-info "Conflicting dependency specs for shared deps"
                        {:dep            dep
                         :path           path
                         :coord          coord
                         :existing-path  existing-path
                         :existing-coord existing-coord})))
      state)

    :else
    (assoc-in state [:found dep] {:coord coord
                                  :path  path})))

(defn- missing-shared-deps [shared]
  (let [{:keys [found]} (reduce (fn [state path]
                                  (let [form (read-edn path)]
                                    (reduce (fn [state entry]
                                              (add-shared-dep state path entry))
                                            state
                                            (dep-entries form))))
                                {:shared shared
                                 :found  {}}
                                (deps-files))]
    (into (sorted-map)
          (map (fn [[dep {:keys [coord]}]]
                 [dep coord]))
          found)))

(defn- merge-shared-deps! [shared missing]
  (let [contents    (slurp shared-deps-file)
        node        (r/parse-string contents)
        updated     (binding [*print-namespace-maps* false]
                      (reduce (fn [node [dep coord]]
                                (r/assoc-in node [:deps dep] coord))
                              node
                              (sort-by key missing)))
        updated-str (str updated)]
    (when-not (= contents updated-str)
      (spit shared-deps-file updated-str))
    (merge shared missing)))

(defn- sync-file! [shared path]
  (let [contents    (slurp path)
        node        (r/parse-string contents)
        form        (edn/read-string contents)
        paths       (filterv #(contains? shared (peek %)) (dep-paths form))
        updated     (binding [*print-namespace-maps* false]
                      (reduce (fn [node path]
                                (let [current (get-in form path)
                                      shared' (get shared (peek path))]
                                  (r/assoc-in node path
                                              (merge current shared'))))
                              node
                              paths))
        updated-str (str updated)]
    (when-not (= contents updated-str)
      (spit path updated-str))))

(defn sync-deps
  "Syncs versions in deps/deps.edn with the other deps.edn files."
  []
  (let [shared  (shared-deps)
        missing (missing-shared-deps shared)
        shared' (merge-shared-deps! shared missing)]
    (doseq [path (deps-files)]
      (sync-file! shared' path))
    (biff.run/run-task "format")
    nil))
