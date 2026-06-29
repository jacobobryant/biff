(ns com.biffweb.graph
  "TODO"
  (:require [com.biffweb.core :as biff.core]
            [com.biffweb.graph.impl.query :as impl.query]
            [com.biffweb.graph.impl.ctx :as impl.ctx]
            [com.biffweb.graph.impl.ast :as impl.ast]
            [com.biffweb.graph.impl.resolver :as impl.r]))

(biff.core/register
 {:biff.graph/id               'qualified-keyword?
  :biff.graph/query            impl.ast/query-schema
  :biff.graph/input-query      :biff.graph/query
  :biff.graph/output-query     :biff.graph/query
  :biff.graph/query-ast        impl.ast/ast-schema
  :biff.graph/input-ast        :biff.graph/query-ast
  :biff.graph/output-ast       :biff.graph/query-ast
  :biff.graph/input            [:or
                                [:sequential [:maybe 'map?]]
                                [:maybe 'map?]]
  :biff.graph/batch            :boolean
  :biff.graph/resolve-fn       'ifn?
  :biff.graph/resolver         [:map
                                [:biff.graph/id]
                                [:biff.graph/input-ast]
                                [:biff.graph/output-ast]
                                [:biff.graph/resolve-fn]
                                [:biff.graph/batch {:optional true}]]
  :biff.graph/attr             [:and :keyword [:not [:= :*]]]
  :biff.graph/attr->resolvers  [:map-of
                                :biff.graph/attr
                                [:sequential :biff.graph/resolver]]
  :biff.graph/attr-shape       [:map
                                [:kind [:enum :scalar :join]]
                                [:wildcard {:optional true} :boolean]]
  :biff.graph/attr->shape-info [:map-of
                                :biff.graph/attr
                                [:map
                                 [:biff.graph/id {:optional true}]
                                 [:biff.graph/attr]
                                 [:biff.graph/attr-shape]]]
  :biff.graph/cache            [:fn #(or (instance? clojure.lang.IAtom %)
                                         (instance? clojure.lang.Volatile %))]
  :biff.graph/get-ctx          'ifn?
  :biff.graph/middleware       [:sequential 'ifn?]
  :biff.graph/resolvers        [:sequential :biff.graph/resolver]})

(defn query->ast [query]
  (impl.ast/query->ast query))

(defn resolver [opts]
  (impl.r/resolver opts))

(defmacro defresolver [sym opts & args]
  `(impl.r/defresolver ~sym ~opts ~@args))

(defn new-ctx [resolvers & {:as opts}]
  (impl.ctx/new-ctx resolvers opts))

(defn query
  ([ctx query]
   (impl.query/query ctx query))
  ([ctx input query]
   (impl.query/query ctx input query)))

(def fx-handlers
  {:biff.graph.fx/query query})

(defn module []
  {:biff.core/init   (fn [modules-var]
                       {:biff.graph/get-ctx #(impl.ctx/ctx-from-modules
                                              @modules-var)})
   :biff.fx/handlers fx-handlers})
