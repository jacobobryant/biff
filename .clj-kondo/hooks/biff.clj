(ns hooks.biff
  (:require [clj-kondo.hooks-api :as api]))

(defn with-temp-dir [{:keys [node]}]
  (let [[_ binding & body] (:children node)
        sym                (first (:children binding))]
    {:node (api/list-node
            (list* (api/token-node 'let)
                   (api/vector-node [sym (api/token-node nil)])
                   body))}))
