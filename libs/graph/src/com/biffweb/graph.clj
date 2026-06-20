(ns com.biffweb.graph
  (:require [com.biffweb.core :as biff.core]
            [com.biffweb.graph.impl :as impl]))

(biff.core/register
 {:biff.graph/id              'qualified-keyword?
  :biff.graph/query           impl/query-schema
  :biff.graph/ast             impl/ast-schema
  :biff.graph/input-query     :biff.graph/query
  :biff.graph/output-query    :biff.graph/query
  :biff.graph/input-ast       :biff.graph/ast
  :biff.graph/output-ast      :biff.graph/ast
  :biff.graph/input           [:or
                               [:sequential [:maybe 'map?]]
                               [:maybe 'map?]]
  :biff.graph/batch           :boolean
  :biff.graph/resolve-fn      'ifn?
  :biff.graph/resolver        [:map
                               [:biff.graph/id]
                               [:biff.graph/input-ast]
                               [:biff.graph/output-ast]
                               [:biff.graph/resolve-fn]
                               [:biff.graph/batch {:optional true}]]
  :biff.graph/attr->resolvers [:map-of :keyword [:sequential :biff.graph/resolver]]
  :biff.graph/attr->shape     [:map-of :keyword [:map
                                                 [:kind [:enum :scalar :join]]
                                                 [:wildcard {:optional true} :boolean]]]
  :biff.graph/cache           [:fn #(or (instance? clojure.lang.IAtom %)
                                        (instance? clojure.lang.Volatile %))]
  :biff.graph/get-env         'ifn?
  :biff.graph/middleware      [:sequential 'ifn?]
  :biff.graph/resolvers       [:sequential :biff.graph/resolver]})

(defn query->ast [query]
  (impl/query->ast query))

(defn resolver [opts]
  (impl/resolver opts))

(defmacro defresolver [sym opts & args]
  `(impl/defresolver ~sym ~opts ~@args))

(defn new-env [resolvers & {:as opts}]
  (impl/new-env resolvers opts))

(defn query
  ([ctx query]
   (impl/query ctx query))
  ([ctx input query]
   (impl/query ctx input query)))

(def fx-handlers impl/fx-handlers)

(defn module []
  (impl/module))
