(ns com.biffweb.graph.impl.ast
  (:require [com.biffweb.core :as biff.core]
            [malli.core :as m]))

(def query-schema
  [:schema
   {:registry
    {"query"       [:vector [:ref "query-item"]]
     "query-item"  [:orn
                    [:required [:ref "query-item*"]]
                    [:optional [:tuple [:= :?] [:ref "query-item*"]]]]
     "query-item*" [:orn
                    [:scalar [:ref "attr"]]
                    [:join [:and
                            [:map-of
                             [:ref "attr"]
                             [:ref "subquery"]]
                            [:fn #(= 1 (count %))]]]]
     "attr"        [:and :keyword [:not [:= :*]]]
     "subquery"    [:orn
                    [:wildcard [:= [:*]]]
                    [:subquery [:ref "query"]]]}}
   [:ref "query"]])

(def ast-schema
  [:schema
   {:registry
    {"ast"      [:map-of [:ref "attr"] [:ref "attr-ast"]]
     "attr"     :keyword
     "attr-ast" [:map {:closed true}
                 [:kind [:enum :scalar :join]]
                 [:optional {:optional true} :boolean]
                 [:wildcard {:optional true} :boolean]
                 [:children {:optional true} [:ref "ast"]]]}}
   [:ref "ast"]])

(def query-parser
  (m/parser query-schema))

(declare parsed-query-item->ast)

(defn- parsed-subquery->ast
  [parsed-subquery]
  (case (:key parsed-subquery)
    :wildcard {:wildcard true}
    :subquery {:children (into {}
                               (map parsed-query-item->ast)
                               (:value parsed-subquery))}))

(defn- parsed-query-item->ast
  [query-item]
  (let [[query-item optional] (case (:key query-item)
                                :required [(:value query-item)]
                                :optional [(second (:value query-item)) true])]
    (case (:key query-item)
      :scalar
      [(:value query-item) (into {:kind :scalar}
                                 (filter val)
                                 {:optional optional})]

      :join
      (let [[attr parsed-subquery]      (first (:value query-item))
            {:keys [wildcard children]} (parsed-subquery->ast parsed-subquery)]
        [attr (into {:kind :join}
                    (filter val)
                    {:children children
                     :optional optional
                     :wildcard wildcard})]))))

(defn query->ast
  [query]
  (biff.core/validate {:biff.graph/query query})
  (into {} (map parsed-query-item->ast) (query-parser query)))

(defn ast-seq [query-ast]
  (->> (tree-seq (constantly true)
                 (comp :children second)
                 [:root {:children query-ast}])
       rest
       (map (fn [[attr info]]
              [attr (select-keys info [:kind :wildcard])]))))
