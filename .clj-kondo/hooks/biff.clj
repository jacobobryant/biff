(ns hooks.biff
  (:require [clj-kondo.hooks-api :as api]))

(defn with-temp-dir [{:keys [node]}]
  (let [[_ binding & body] (:children node)
        sym                (first (:children binding))]
    {:node (api/list-node
            (list* (api/token-node 'let)
                   (api/vector-node [sym (api/token-node nil)])
                   body))}))

(defn defresolver [{:keys [node]}]
  (let [[_ sym _opts & args] (:children node)
        fns                 (cond
                              (api/vector-node? (first args))
                              [(api/list-node
                                (list* (api/token-node 'fn)
                                       args))]

                              (api/keyword-node? (first args))
                              (keep-indexed
                               (fn [i arg]
                                 (when (odd? i)
                                   arg))
                               args)

                              :else args)]
    {:node (api/list-node
            [(api/token-node 'def)
             sym
             (api/list-node
              (list* (api/token-node 'do)
                     fns))])}))
