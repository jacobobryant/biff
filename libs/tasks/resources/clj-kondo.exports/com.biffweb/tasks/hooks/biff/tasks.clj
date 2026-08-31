(ns hooks.biff.tasks
  (:require [clj-kondo.hooks-api :as api]
            [clojure.string :as str]))

(defn- namespace-parts [ns-sym]
  (str/split (str ns-sym) #"\."))

(defn- impl-prefix [required-ns]
  (let [parts (namespace-parts required-ns)
        index (first (keep-indexed #(when (= %2 "impl") %1) parts))]
    (when (some? index)
      (subvec (vec parts) 0 index))))

(defn- allowed? [current-ns required-ns]
  (when-let [prefix (impl-prefix required-ns)]
    (let [current-parts (vec (namespace-parts current-ns))
          impl-prefix   (conj prefix "impl")]
      (or (= current-parts prefix)
          (and (<= (count impl-prefix) (count current-parts))
               (= impl-prefix
                  (subvec current-parts 0 (count impl-prefix))))))))

(defn- required-namespaces [libspec]
  (cond
    (symbol? (api/sexpr libspec))
    [[libspec (api/sexpr libspec)]]

    (api/vector-node? libspec)
    (let [[prefix & children] (:children libspec)]
      (if (some api/vector-node? children)
        (mapcat (fn [child]
                  (if (api/vector-node? child)
                    (let [name (first (:children child))]
                      [[name (symbol (str (api/sexpr prefix) "."
                                          (api/sexpr name)))]])
                    []))
                children)
        [[prefix (api/sexpr prefix)]]))

    :else
    []))

(defn- require-clauses [node]
  (filter (fn [clause]
            (and (api/list-node? clause)
                 (= :require (some-> clause :children first api/sexpr))))
          (:children node)))

(defn impl-visibility [{:keys [node]}]
  (let [[_ current-ns-node & _] (:children node)
        current-ns              (api/sexpr current-ns-node)]
    (doseq [clause                      (require-clauses node)
            libspec                     (rest (:children clause))
            [required-node required-ns] (required-namespaces libspec)]
      (when (and (symbol? required-ns)
                 (impl-prefix required-ns)
                 (not (allowed? current-ns required-ns)))
        (let [message (str "Namespace " current-ns
                           " may not require private namespace " required-ns)]
          (api/reg-finding!
           (merge (meta required-node)
                  {:message message
                   :type    :biff/impl-visibility})))))))
