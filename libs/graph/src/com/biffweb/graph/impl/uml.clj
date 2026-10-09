(ns com.biffweb.graph.impl.uml
  (:require [clojure.set :as set]
            [clojure.string :as str]))

(defn uml [{:keys [biff.graph/resolvers]}]
  (let [asts (mapcat (juxt :biff.graph/input-ast :biff.graph/output-ast)
                     resolvers)
        rules (for [resolver resolvers
                    input (keys (:biff.graph/input-ast resolver))]
                [input (set (keys (:biff.graph/output-ast resolver)))])
        attrs (set (mapcat keys asts))
        primary-key? #(and (= "id" (name %)) (namespace %))
        joins (set (for [ast asts
                         [attr {:keys [kind children]}] ast
                         :when (= :join kind)
                         target (keys children)
                         :when (primary-key? target)]
                     [attr target]))
        ids (sort-by str (set/union (set (filter primary-key? attrs))
                                    (set (map second joins))))
        reachable (fn [id]
                    (loop [found #{id}]
                      (let [next-found (reduce (fn [acc [input outputs]]
                                                 (if (contains? found input)
                                                   (set/union acc outputs)
                                                   acc))
                                               found
                                               rules)]
                        (if (= found next-found)
                          found
                          (recur next-found)))))
        attrs-by-id (into {} (map (fn [id] [id (reachable id)]) ids))
        classes (for [id ids]
                  (str "  class " (namespace id) " {\n"
                       (str/join "\n" (map #(str "    " (subs (str %) 1))
                                             (sort-by str (attrs-by-id id))))
                       "\n  }"))
        relationships (sort (set (for [id ids
                                       [attr target] joins
                                       :when (contains? (attrs-by-id id) attr)]
                                   (str "  " (namespace id) " -- "
                                        (namespace target) " : "
                                        (subs (str attr) 1)))))]
    (str "classDiagram\n" (str/join "\n" classes)
         (when (seq classes) "\n")
         (str/join "\n" relationships)
         (when (seq relationships) "\n"))))
