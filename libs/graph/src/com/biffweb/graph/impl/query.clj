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

(defn resolve-attr [{:biff.graph/keys [trace attr->resolvers] :as ctx}
                    entities attr resolving-attrs]
  (assert (vector? entities))
  (if (contains? resolving-attrs attr)
    (vec (repeat (count entities) {::fail-trace trace}))
    (loop [values                      (mapv #(if-some [value (get % attr)]
                                                value
                                                {::fail-trace trace})
                                             entities)
           [resolver & rest-resolvers] (get attr->resolvers attr)]
      (let [indexed-entities
            (into []
                  (comp (map-indexed vector)
                        (filter (comp ::fail-trace values first)))
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

                {resolved-inputs true unresolved-inputs false}
                (->> indexed-entities
                     (apply-indexed #(resolve-entities
                                      ctx
                                      %
                                      (:biff.graph/input-ast resolver)
                                      (conj resolving-attrs attr)))
                     (group-by (comp not ::fail-trace second)))

                indexed-values
                (some->> resolved-inputs
                         not-empty
                         (apply-indexed #(batch-resolve-fn ctx %))
                         (keep (fn [[i result]]
                                 (when (contains? result attr)
                                   [i (get result attr)]))))

                values
                (reduce (fn [values [idx value]]
                          (assoc values idx value))
                        values
                        (into unresolved-inputs indexed-values))]
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
      flat-results
      (mapv (fn [results]
              (if-some [fail-trace (some ::fail-trace results)]
                {::fail-trace fail-trace}
                results))
            (partition-by-sizes flat-results value-sizes)))))

(defn resolve-entities [ctx input query-ast resolving-attrs]
  (reduce (fn [results [attr attr-ast]]
            (let [trace (:biff.graph/trace ctx)
                  trace (update-in trace
                                   [(dec (count trace)) :path]
                                   (fnil conj [])
                                   attr)
                  ctx   (assoc ctx :biff.graph/trace trace)

                  indexed-input
                  (mapv (fn [i input result]
                          [i (merge input result)])
                        (range)
                        input
                        results)

                  indexed-values
                  (->> indexed-input
                       (filterv (comp not ::fail-trace second))
                       (apply-indexed #(resolve-attr ctx
                                                     %
                                                     attr
                                                     resolving-attrs)))

                  indexed-values
                  (if (or (= (:kind attr-ast) :scalar)
                          (:wildcard attr-ast))
                    indexed-values
                    (let [{indexed-resolved true indexed-unresolved false}
                          (group-by (comp not ::fail-trace second)
                                    indexed-values)]
                      (->> indexed-resolved
                           (apply-indexed #(resolve-joins ctx
                                                          %
                                                          attr
                                                          (:children attr-ast)))
                           (into (vec indexed-unresolved)))))

                  idx->value
                  (into {} indexed-values)]
              (mapv (fn [i result]
                      (let [value (get idx->value i)]
                        (cond
                          (::fail-trace result)
                          result

                          (not (::fail-trace value))
                          (assoc result attr value)

                          (:optional attr-ast)
                          result

                          :else
                          (merge result value))))
                    (range)
                    results)))
          (vec (repeat (count input) {}))
          query-ast))

(defn query
  ([ctx query*]
   (query ctx {} query*))
  ([{:biff.graph/keys [get-ctx] :as ctx} input query*]
   (biff.core/validate {:biff.graph/query query*
                        :biff.graph/input input})
   (let [;; (get-ctx) intentionally overrides the incoming ctx; if you want to
         ;; provide graph indexes directly, don't set get-ctx.
         ctx
         (-> (merge ctx
                    (when get-ctx (get-ctx))
                    {:biff.graph/cache (volatile! {})
                     :biff.graph/trace [{:resolving :query}]})
             (biff.core/validate {:required [:biff.graph/attr->resolvers
                                             :biff.graph/attr->shape-info]}))

         query-ast (impl.ast/query->ast query*)

         _
         (impl.v/validate-query
          {:biff.graph/attr->shape-info (:biff.graph/attr->shape-info ctx)
           :biff.graph/query-ast        query-ast})

         sequential-input (sequential? input)
         input            (if sequential-input (vec input) [input])
         _                (impl.v/validate-input (:biff.graph/attr->shape-info ctx)
                                                 input)
         resolving-attrs  #{}
         execute          (fn [ctx]
                            (resolve-entities ctx input query-ast resolving-attrs))
         execute          (if-some [wrap-db-snapshot (:biff.core/wrap-db-snapshot ctx)]
                            (wrap-db-snapshot execute)
                            execute)
         results          (execute ctx)]
     (doseq [{::keys [fail-trace]} results
             :when                 fail-trace
             :let                  [attr (-> fail-trace
                                             peek
                                             :path
                                             peek)]]
       (throw (ex-info (str "Could not resolve " attr)
                       {:biff.graph/trace fail-trace})))
     (if sequential-input
       results
       (first results)))))
