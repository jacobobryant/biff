(ns hooks.biff.graph
  (:require [clj-kondo.hooks-api :as api]))

(defn defresolver [{:keys [node]}]
  (let [[_ sym _opts & args] (:children node)
        fns                  (if (api/vector-node? (first args))
                               [(api/list-node
                                 (list* (api/token-node 'fn)
                                        args))]
                               (keep-indexed
                                (fn [i arg]
                                  (when (odd? i)
                                    arg))
                                args))]
    {:node (api/list-node
            [(api/token-node 'def)
             sym
             (api/list-node
              (list* (api/token-node 'do)
                     fns))])}))
