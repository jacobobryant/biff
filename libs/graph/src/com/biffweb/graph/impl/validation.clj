(ns com.biffweb.graph.impl.validation
  (:require
   [com.biffweb.core :as biff.core]
   [com.biffweb.graph.impl.ast :as impl.ast]))

(defn validate-query [{:biff.graph/keys [id query-ast attr->shape-info]}]
  (doseq [[attr shape] (impl.ast/ast-seq query-ast)
          :let         [{expected-shape :biff.graph/attr-shape
                         source-id      :biff.graph/id}
                        (get attr->shape-info attr)]]
    (assert expected-shape
            (str "No resolver declares output for `" attr "` requested by "
                 (or id "query")))
    (assert (= shape expected-shape)
            (str "Got conflicting attr shapes for `" attr "`: "
                 (pr-str shape) " (from " (or id "query") "), "
                 (pr-str expected-shape) " (from " source-id ")"))))

(defn validate-resolver [m]
  (biff.core/validate m {:required   [:biff.graph/id
                                      :biff.graph/input-ast
                                      :biff.graph/output-ast
                                      :biff.graph/resolve-fn]
                         :error-data (select-keys m [:biff.graph/id])}))

(defn join-value? [value]
  (or (map? value)
      (and (sequential? value)
           (every? (some-fn map? nil?) value))))

(defn scalar-value? [value]
  (not (join-value? value)))

(defn- validate-input-value [attr->shape-info attr value]
  (when-some [{:biff.graph/keys [attr-shape]} (get attr->shape-info attr)]
    (case (:kind attr-shape)
      :join (assert (join-value? value)
                    (str "Input attr " attr " is a join but value is a scalar"))
      :scalar (assert (scalar-value? value)
                      (str "Input attr " attr " is a scalar but value is a join")))))

(defn validate-input [attr->shape-info input]
  (letfn [(visit [entity]
            (when (map? entity)
              (doseq [[attr value] entity]
                (validate-input-value attr->shape-info attr value)
                (cond
                  (map? value)
                  (visit value)

                  (sequential? value)
                  (run! visit value)))))]
    (run! visit input)))
