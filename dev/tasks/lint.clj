(ns tasks.lint
  (:require [com.biffweb.tasks.impl.util :as util]
            [rewrite-clj.zip :as z]))

(def ^:private binding-forms
  '#{binding doseq dotimes for if-let if-some let letfn loop
     when-first when-let when-some with-open with-redefs})

(defn- child-locs [loc]
  (when-let [child (z/down loc)]
    (take-while some? (iterate z/right child))))

(defn- pair-locs [loc]
  (let [children (vec (child-locs loc))]
    (when (even? (count children))
      (partition 2 children))))

(defn- expanded? [[left right]]
  (< (:end-row (meta (z/node left)))
     (:row (meta (z/node right)))))

(defn- separated? [[_ left-right] [right-left _]]
  (< (inc (:end-row (meta (z/node left-right))))
     (:row (meta (z/node right-left)))))

(defn- pair-findings [path pairs]
  (for [[left right] (partition 2 1 pairs)
        :when        (and (or (expanded? left) (expanded? right))
                          (not (separated? left right)))
        :let         [[loc _] right
                      {:keys [row col]} (meta (z/node loc))]]
    {:path path :row row :col col}))

(defn- sibling-index [loc]
  (loop [loc   loc
         index 0]
    (if-let [left (z/left loc)]
      (recur left (inc index))
      index)))

(defn- binding-vector? [loc]
  (let [parent (z/up loc)]
    (and (= :list (some-> parent z/tag))
         (= 1 (sibling-index loc))
         (contains? binding-forms (some-> parent z/down z/sexpr)))))

(defn- cond-form? [loc]
  (and (= :list (z/tag loc))
       (= 'cond (some-> loc z/down z/sexpr))))

(defn- findings-at [path loc]
  (let [pairs (cond
                (= :map (z/tag loc))
                (pair-locs loc)

                (binding-vector? loc)
                (pair-locs loc)

                (cond-form? loc)
                (let [children (rest (child-locs loc))]
                  (when (even? (count children))
                    (partition 2 children))))]
    (when-some [pairs pairs]
      (pair-findings path pairs))))

(defn- source-findings [path source]
  (loop [loc      (z/of-string source {:track-position? true})
         findings []]
    (if (z/end? loc)
      findings
      (recur (z/next loc)
             (into findings (findings-at path loc))))))

(defn- file-findings [root file]
  (source-findings (util/relative-path root file) (slurp file)))

(defn- lint-pair-spacing []
  (let [root     (util/project-root)
        findings (mapcat #(file-findings root %)
                         (util/clojure-files))]
    (doseq [{:keys [path row col]} findings]
      (println (str path ":" row ":" col
                    ": pairs spanning multiple lines must be separated "
                    "by a blank line")))
    (when (seq findings)
      (throw (ex-info "Pair spacing check failed"
                      {:findings (vec findings)})))))

(defn lint [& args]
  (apply (requiring-resolve 'com.biffweb.tasks/lint) args)
  (lint-pair-spacing))
