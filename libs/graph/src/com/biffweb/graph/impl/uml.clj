(ns com.biffweb.graph.impl.uml
  (:require [clojure.set :as set]
            [clojure.string :as str]))

(defn- ctx-resolvers [{:biff.graph/keys [get-ctx resolvers] :as ctx}]
  (let [{:biff.graph/keys [attr->resolvers]}
        (merge ctx (when get-ctx (get-ctx)))]
    (or (when-not get-ctx resolvers)
        (distinct (apply concat (vals attr->resolvers))))))

(defn uml [ctx]
  (let [resolvers     (ctx-resolvers ctx)
        asts          (mapcat (fn [resolver]
                                [(:biff.graph/input-ast resolver)
                                 (:biff.graph/output-ast resolver)])
                              resolvers)
        rules         (for [resolver resolvers
                            input    (keys (:biff.graph/input-ast resolver))]
                        [input (set (keys (:biff.graph/output-ast resolver)))])
        attrs         (set (mapcat keys asts))
        primary-key?  #(and (= "id" (name %)) (namespace %))
        joins         (set (for [ast         asts
                                 [attr opts] ast
                                 :when       (= :join (:kind opts))
                                 target      (keys (:children opts))
                                 :when       (primary-key? target)]
                             [attr target]))
        ids           (sort-by str (set/union (set (filter primary-key? attrs))
                                              (set (map second joins))))
        reachable     (fn [id]
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
        attrs-by-id   (into {} (map (fn [id] [id (reachable id)]) ids))
        classes       (for [id ids]
                        (str "  class " (namespace id) " {\n"
                             (str/join "\n"
                                       (map #(str "    " (subs (str %) 1))
                                            (sort-by str (attrs-by-id id))))
                             "\n  }"))
        relationships (sort (set (for [id            ids
                                       [attr target] joins
                                       :when         ((attrs-by-id id) attr)]
                                   (str "  " (namespace id) " -- "
                                        (namespace target) " : "
                                        (subs (str attr) 1)))))]
    (str "classDiagram\n" (str/join "\n" classes)
         (when (seq classes) "\n")
         (str/join "\n" relationships)
         (when (seq relationships) "\n"))))
