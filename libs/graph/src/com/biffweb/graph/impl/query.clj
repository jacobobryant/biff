(ns com.biffweb.graph.impl.query
  (:require
   [com.biffweb.core :as biff.core]
   [com.biffweb.graph.impl.ast :as impl.ast]
   [com.biffweb.graph.impl.validation :as impl.v]))

(defn apply-indexed [f indexed]
  (let [indexes (mapv first indexed)
        xs      (mapv second indexed)
        xs      (f xs)]
    (mapv vector indexes xs)))

(defn partition-by-sizes [v sizes]
  (assert (vector? v))
  (assert (vector? sizes))
  (second
   (reduce (fn [[i parts] n]
             (let [j (+ i n)]
               [j (conj parts (subvec v i j))]))
           [0 []]
           sizes)))

(declare resolve-entities)

(defn resolve-attr [{:biff.graph/keys [attr->resolvers] :as ctx}
                    entities attr resolving-attrs]
  (assert (vector? entities))
  (if (contains? resolving-attrs attr)
    (vec (repeat (count entities) ::unresolved))
    (loop [values                      (mapv #(if (contains? % attr)
                                                (get % attr)
                                                ::unresolved)
                                             entities)
           [resolver & rest-resolvers] (get attr->resolvers attr)]
      (let [indexed-entities
            (into []
                  (comp (map-indexed vector)
                        (filter (comp #{::unresolved} values first)))
                  entities)]
        (if (or (empty? indexed-entities) (nil? resolver))
          values
          (let [{:biff.graph/keys [batch resolve-fn]} resolver

                ctx
                (update ctx
                        :biff.graph/trace
                        conj
                        {:resolving (:biff.graph/id resolver)})

                resolve-fn
                (fn [ctx input]
                  (resolve-fn (assoc ctx :biff.graph/input input)))

                batch-resolve-fn
                (if batch
                  resolve-fn
                  (fn [ctx inputs]
                    (mapv #(resolve-fn ctx %) inputs)))

                indexed-inputs
                (->> indexed-entities
                     (apply-indexed #(resolve-entities
                                      ctx
                                      %
                                      (:biff.graph/input-ast resolver)
                                      (conj resolving-attrs attr)))
                     (filterv (comp not ::unresolved second)))

                indexed-results
                (some->> indexed-inputs
                         not-empty
                         (apply-indexed #(batch-resolve-fn ctx %))
                         (filterv (fn [[_ result]]
                                    (contains? result attr))))

                values
                (reduce (fn [values [idx result]]
                          (assoc values idx (get result attr)))
                        values
                        indexed-results)]
            (recur values rest-resolvers)))))))

(defn resolve-joins [ctx join-values attr children-ast]
  (assert (every? some? join-values)
          "Join values cannot be nil. Use {} or [] instead.")
  (let [all-maps?        (every? map? join-values)
        all-seqs?        (every? sequential? join-values)

        _                (assert (or all-maps? all-seqs?)
                                 (str "Got conflicting cardinalities for " attr
                                      ". The value should either always be a "
                                      "map or always be a sequence of maps."))
        value-sizes      (when all-seqs? (mapv count join-values))
        flat-join-values (if all-maps?
                           join-values
                           (into [] (mapcat identity) join-values))
        flat-results     (if (empty? flat-join-values)
                           []
                           (resolve-entities ctx flat-join-values children-ast #{}))]
    (if all-maps?
      (mapv (fn [result]
              (if (::unresolved result)
                ::unresolved
                result))
            flat-results)
      (mapv (fn [results]
              (if (some ::unresolved results)
                ::unresolved
                results))
            (partition-by-sizes flat-results value-sizes)))))

(defn resolve-entities [ctx input query-ast resolving-attrs]
  (reduce (fn [results [attr attr-ast]]
            (let [trace (:biff.graph/trace ctx)

                  ctx
                  (update-in ctx
                             [:biff.graph/trace (dec (count trace)) :path]
                             (fnil conj [])
                             attr)

                  indexed-input
                  (mapv (fn [i input result]
                          [i (merge input result)])
                        (range)
                        input
                        results)

                  indexed-values
                  (->> indexed-input
                       (filterv (comp not ::unresolved second))
                       (apply-indexed #(resolve-attr ctx
                                                     %
                                                     attr
                                                     resolving-attrs)))

                  indexed-values
                  (if (or (= (:kind attr-ast) :scalar)
                          (:wildcard attr-ast))
                    indexed-values
                    (->> indexed-values
                         (filterv (comp not #{::unresolved} second))
                         (apply-indexed #(resolve-joins ctx
                                                        %
                                                        attr
                                                        (:children attr-ast)))))

                  idx->value
                  (into {} indexed-values)]
              (mapv (fn [i result]
                      (let [value (get idx->value i ::unresolved)]
                        (cond
                          (::unresolved result)
                          result

                          (not= value ::unresolved)
                          (assoc result attr value)

                          (:optional attr-ast)
                          result

                          :else
                          (-> result
                              (assoc ::unresolved true)
                              (update ::missing (fnil conj []) attr)))))
                    (range)
                    results)))
          (vec (repeat (count input) {}))
          query-ast))

(defn query
  ([ctx query*]
   (query ctx {} query*))
  ([{:biff.graph/keys [get-env] :as ctx} input query*]
   (biff.core/validate {:biff.graph/query query*
                        :biff.graph/input input})
   (let [;; (get-env) intentionally overrides ctx; if you want to set the env in
         ;; ctx, don't set get-env.
         ctx              (merge ctx
                                 (when get-env (get-env))
                                 {:biff.graph/cache (volatile! {})
                                  :biff.graph/trace [{:resolving :query}]})
         _                (biff.core/validate ctx {:required [:biff.graph/attr->resolvers
                                                              :biff.graph/attr->shape-info]})
         query-ast        (impl.ast/query->ast query*)
         _                (impl.v/validate-query
                           {:biff.graph/attr->shape-info (:biff.graph/attr->shape-info ctx)
                            :biff.graph/query-ast        query-ast})
         sequential-input (sequential? input)
         input            (if sequential-input (vec input) [input])
         _                (impl.v/validate-input (:biff.graph/attr->shape-info ctx)
                                                 input)
         resolving-attrs  #{}
         results          (resolve-entities ctx input query-ast resolving-attrs)]
     (doseq [entity results
             :when  (::unresolved entity)]
       (throw (ex-info "Entity could not be fully resolved"
                       {:biff.graph/missing (::missing entity)})))
     (if sequential-input
       results
       (first results)))))
