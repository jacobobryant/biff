(ns com.biffweb.graph.impl.ast
  (:require [com.biffweb.core :as biff.core]
            [malli.core :as m]))

(def query-schema
  [:schema
   {:registry
    {"query"       [:vector [:ref "query-item"]]
     "query-item"  [:orn
                    [:required-or-join [:ref "query-item*"]]
                    [:optional-scalar [:tuple [:= :?] [:ref "attr"]]]]
     "query-item*" [:orn
                    [:scalar [:ref "attr"]]
                    [:join [:and
                            [:map-of
                             [:ref "join-key"]
                             [:ref "subquery"]]
                            [:fn #(= 1 (count %))]]]]
     "join-key"    [:orn
                    [:required [:ref "attr"]]
                    [:optional [:tuple [:= :?] [:ref "attr"]]]]
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
                                :required-or-join [(:value query-item) nil]

                                :optional-scalar [{:key :scalar

                                                   :value
                                                   (second (:value query-item))}
                                                  true])]
    (case (:key query-item)
      :scalar
      [(:value query-item) (into {:kind :scalar}
                                 (filter val)
                                 {:optional optional})]

      :join
      (let [[attr-node subquery]        (first (:value query-item))
            attr-value                  (:value attr-node)
            [attr optional]             (case (:key attr-node)
                                          :required [attr-value optional]
                                          :optional
                                          [(second attr-value) true])
            {:keys [wildcard children]} (parsed-subquery->ast subquery)]
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
