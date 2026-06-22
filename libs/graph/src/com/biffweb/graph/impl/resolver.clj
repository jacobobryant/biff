(ns com.biffweb.graph.impl.resolver
  (:require
   [com.biffweb.core :as biff.core]
   [com.biffweb.fx :as fx]
   [com.biffweb.graph.impl.ast :as impl.ast]
   [com.biffweb.graph.impl.validation :as impl.v]))

(defn wrap-input [f]
  (fn [ctx]
    (f ctx (:biff.graph/input ctx))))

(defn resolver [{:keys [id input output batch resolve-fn]}]
  (biff.core/validate {:biff.graph/input-query  (or input [])
                       :biff.graph/output-query (or output [])}
                      {:error-data {:biff.graph/id id}})
  (impl.v/validate-resolver
   {:biff.graph/id         id
    :biff.graph/input-ast  (impl.ast/query->ast (or input []))
    :biff.graph/output-ast (impl.ast/query->ast (or output []))
    :biff.graph/batch      (boolean batch)
    :biff.graph/resolve-fn (wrap-input resolve-fn)}))

(defmacro defresolver [sym opts & args]
  (let [use-fx (not (vector? (first args)))
        id     (keyword (str *ns*) (str sym))]
    `(def ~sym
       (let [opts# ~opts]
         (biff.core/validate
          {:biff.graph/id         ~id
           :biff.graph/input-ast  (impl.ast/query->ast (get opts# :input []))
           :biff.graph/output-ast (impl.ast/query->ast (get opts# :output []))
           :biff.graph/batch      (get opts# :batch false)

           :biff.graph/resolve-fn
           ~(if-not use-fx
              `(wrap-input (fn ~@args))
              `(let [[& {:as state->fn#}] [~@args]]
                 (fx/machine ~id (update-vals state->fn# wrap-input))))})))))
