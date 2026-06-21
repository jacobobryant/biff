(ns com.biffweb.graph.impl
  (:require [com.biffweb.core :as biff.core]
            [com.biffweb.fx :as fx]
            [malli.core :as m]))

;;;; RESOLVERS

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

(defn wrap-input [f]
  (fn [ctx]
    (f ctx (:biff.graph/input ctx))))

(defn- validate-resolver [m]
  (biff.core/validate m {:required   [:biff.graph/id
                                      :biff.graph/input-ast
                                      :biff.graph/output-ast
                                      :biff.graph/resolve-fn]
                         :error-data (select-keys m [:biff.graph/id])}))

(defn resolver [{:keys [id input output batch resolve-fn]}]
  (biff.core/validate {:biff.graph/input-query  (or input [])
                       :biff.graph/output-query (or output [])}
                      {:error-data {:biff.graph/id id}})
  (validate-resolver
   {:biff.graph/id         id
    :biff.graph/input-ast  (query->ast (or input []))
    :biff.graph/output-ast (query->ast (or output []))
    :biff.graph/batch      (boolean batch)
    :biff.graph/resolve-fn (wrap-input resolve-fn)}))

(defmacro defresolver [sym opts & args]
  (let [use-fx (not (vector? (first args)))
        id     (keyword (str *ns*) (str sym))]
    `(def ~sym
       (let [opts# ~opts]
         (biff.core/validate
          {:biff.graph/id         ~id
           :biff.graph/input-ast  (query->ast (get opts# :input []))
           :biff.graph/output-ast (query->ast (get opts# :output []))
           :biff.graph/batch      (get opts# :batch false)

           :biff.graph/resolve-fn
           ~(if-not use-fx
              `(wrap-input (fn ~@args))
              `(let [[& {:as state->fn#}] [~@args]]
                 (fx/machine ~id (update-vals state->fn# wrap-input))))})))))

;;;; ENV

(declare select-output)

(defn- join-value? [value]
  (or (map? value)
      (and (sequential? value)
           (every? (some-fn map? nil?) value))))

(defn- scalar-value? [value]
  (and (not (map? value))
       (or (not (sequential? value))
           (every? (complement map?) value))))

(defn- select-output-value [value
                            attr
                            {:keys [kind children wildcard]}
                            resolver-id]
  (case kind
    :join (assert (join-value? value)
                  (str resolver-id " declared " attr
                       " as a join but value is a scalar"))
    :scalar (assert (scalar-value? value)
                    (str resolver-id " declared " attr
                         " as a scalar but value is a join")))
  (cond
    (or (= kind :scalar) wildcard)
    value

    (sequential? value)
    (mapv #(select-output (or % {}) children resolver-id) value)

    :else
    (select-output value children resolver-id)))

(defn select-output [output output-ast resolver-id]
  (if (sequential? output)
    (mapv #(select-output % output-ast resolver-id) output)
    (into {}
          (keep (fn [[attr attr-ast]]
                  (when (contains? output attr)
                    [attr (select-output-value (get output attr)
                                               attr
                                               attr-ast
                                               resolver-id)])))
          output-ast)))

(defn wrap-select-output [{:biff.graph/keys [id resolve-fn output-ast] :as resolver}]
  (let [resolve-fn (fn [ctx]
                     (-> (resolve-fn ctx)
                         (select-output output-ast id)))]
    (assoc resolver :biff.graph/resolve-fn resolve-fn)))

(defn wrap-validate-output [{:biff.graph/keys [id resolve-fn] :as resolver}]
  (assoc resolver
         :biff.graph/resolve-fn
         (fn [ctx]
           (biff.core/validate (resolve-fn ctx)
                               {:error-data {:biff.graph/id id}}))))

(defn- update-cache! [cache f & args]
  (if (instance? clojure.lang.Volatile cache)
    (vreset! cache (apply f @cache args))
    (apply swap! cache f args)))

(defn wrap-cache [{:biff.graph/keys [batch id resolve-fn] :as resolver}]
  (assoc resolver
         :biff.graph/resolve-fn
         (if batch
           (fn [{:biff.graph/keys [cache input] :as ctx}]
             (let [resolver-cache  (when cache
                                     (get @cache id {}))
                   uncached-inputs (when cache
                                     (into []
                                           (comp (remove #(contains? resolver-cache %))
                                                 (distinct))
                                           input))]
               (cond
                 (not cache)
                 (resolve-fn ctx)

                 (empty? uncached-inputs)
                 (mapv resolver-cache input)

                 :else
                 (let [new-results    (resolve-fn (assoc ctx :biff.graph/input uncached-inputs))
                       _              (update-cache! cache update id merge
                                                     (zipmap uncached-inputs new-results))
                       resolver-cache (get @cache id {})]
                   (mapv resolver-cache input)))))
           (fn [{:biff.graph/keys [cache input] :as ctx}]
             (let [resolver-cache (when cache
                                    (get @cache id))]
               (cond
                 (not cache)
                 (resolve-fn ctx)

                 (contains? resolver-cache input)
                 (get resolver-cache input)

                 :else
                 (get-in (update-cache! cache assoc-in [id input] (resolve-fn ctx))
                         [id input])))))))

(defn- ast-seq [query-ast]
  (->> (tree-seq (constantly true) (comp :children second) [:root {:children query-ast}])
       rest
       (map (fn [[attr info]]
              [attr (select-keys info [:kind :wildcard])]))))

(defn- shape-info [resolvers]
  (for [resolver     resolvers
        query-ast    [(:biff.graph/input-ast resolver)
                      (:biff.graph/output-ast resolver)]
        [attr shape] (ast-seq query-ast)]
    {:biff.graph/attr       attr
     :biff.graph/attr-shape shape
     :biff.graph/id         (:biff.graph/id resolver)}))

(defn validate-query [{:biff.graph/keys [id query-ast attr->shape-info]}]
  (doseq [[attr shape] (ast-seq query-ast)
          :let         [{expected-shape :biff.graph/attr-shape
                         source-id      :biff.graph/id}
                        (get attr->shape-info attr)]]
    (assert (= shape expected-shape)
            (str "Got conflicting attr shapes for " attr ": "
                 (pr-str shape) " (from " (or id "query") "), "
                 (pr-str expected-shape) " (from " source-id ")"))))

(defn new-env [resolvers & {:keys [middleware]}]
  (run! validate-resolver resolvers)
  (let [middleware       (into [wrap-cache
                                wrap-validate-output
                                wrap-select-output]
                               middleware)
        resolvers        (mapv (apply comp middleware) resolvers)
        _                (run! validate-resolver resolvers)
        attr->shape-info (into {}
                               (map (juxt :biff.graph/attr identity))
                               (shape-info resolvers))]
    (doseq [r         resolvers
            query-key [:biff.graph/input-ast :biff.graph/output-ast]]
      (validate-query {:biff.graph/id               (:biff.graph/id r)
                       :biff.graph/query-ast        (get r query-key)
                       :biff.graph/attr->shape-info attr->shape-info}))
    {:biff.graph/attr->shape-info
     attr->shape-info

     :biff.graph/attr->resolvers
     (->> (for [r    resolvers
                attr (keys (:biff.graph/output-ast r))]
            [r attr])
          (reduce (fn [acc [r attr]]
                    (update acc attr (fnil conj []) r))
                  {}))}))

;;;; QUERY ENGINE

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
      (let [unresolved-idxs (into []
                                  (keep-indexed
                                   (fn [i v]
                                     (when (= v ::unresolved)
                                       i)))
                                  values)]
        (if (or (empty? unresolved-idxs) (nil? resolver))
          values
          (let [unresolved-entities (mapv entities unresolved-idxs)
                inputs              (resolve-entities ctx
                                                      unresolved-entities
                                                      (:biff.graph/input-ast resolver)
                                                      (conj resolving-attrs attr))

                valid-pairs         (->> (mapv vector unresolved-idxs inputs)
                                         (filterv (comp not ::unresolved second)))
                valid-idxs          (mapv first valid-pairs)
                valid-inputs        (mapv second valid-pairs)

                {:biff.graph/keys [batch resolve-fn]}
                resolver

                resolve-fn'         (fn [ctx input]
                                      (resolve-fn (assoc ctx :biff.graph/input input)))
                results             (when (not-empty valid-inputs)
                                      (if batch
                                        (resolve-fn' ctx valid-inputs)
                                        (mapv #(resolve-fn' ctx %) valid-inputs)))
                values              (->> (mapv vector valid-idxs results)
                                         (filterv (fn [[_ result]]
                                                    (contains? result attr)))
                                         (reduce (fn [values [idx result]]
                                                   (assoc values idx (get result attr)))
                                                 values))]
            (recur values rest-resolvers)))))))

(defn partition-by-sizes [v sizes]
  (assert (vector? v))
  (assert (vector? sizes))
  (second
   (reduce (fn [[i parts] n]
             (let [j (+ i n)]
               [j (conj parts (subvec v i j))]))
           [0 []]
           sizes)))

(defn resolve-joins [ctx join-values attr children-ast]
  (assert (every? some? join-values)
          "Join values cannot be nil. Use {} or [] instead.")
  (let [all-maps?        (every? map? join-values)
        all-seqs?        (every? sequential? join-values)
        _                (assert (or all-maps? all-seqs?)
                                 (str "Got conflicting cardinalities for " attr))
        value-sizes      (when all-seqs? (mapv count join-values))
        flat-join-values (if all-maps?
                           join-values
                           (into [] (mapcat identity) join-values))
        flat-results     (resolve-entities ctx flat-join-values children-ast #{})]
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

(defn resolve-entities [ctx input query-ast resolving-attrs]
  (reduce (fn [results [attr attr-ast]]
            (let [values (resolve-attr ctx
                                       input
                                       attr
                                       resolving-attrs)
                  values (if (or (= (:kind attr-ast) :scalar)
                                 (:wildcard attr-ast))
                           values
                           (resolve-joins ctx values attr (:children attr-ast)))]
              (mapv (fn [result value]
                      (cond
                        (not= value ::unresolved)
                        (assoc result attr value)

                        (:optional attr-ast)
                        result

                        :else
                        (assoc result ::unresolved true)))
                    results
                    values)))
          (vec (repeat (count input) {}))
          query-ast))

;;;; API

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
                                 {:biff.graph/cache (volatile! {})})
         _                (biff.core/validate ctx {:required [:biff.graph/attr->resolvers
                                                              :biff.graph/attr->shape-info]})
         query-ast        (query->ast query*)
         _                (validate-query
                           {:biff.graph/attr->shape-info (:biff.graph/attr->shape-info ctx)
                            :biff.graph/query-ast        query-ast})
         sequential-input (sequential? input)
         input            (if sequential-input input [input])
         _                (validate-input (:biff.graph/attr->shape-info ctx)
                                          input)
         resolving-attrs  #{}
         results          (resolve-entities ctx input query-ast resolving-attrs)]
     (doseq [entity results
             :when  (::unresolved entity)]
       (throw (ex-info "TODO" entity)))
     (if sequential-input
       results
       (first results)))))

(def fx-handlers
  {:biff.graph.fx/query #'query})

(def ^:private env-from-modules
  (memoize
   (fn [modules]
     (new-env (mapcat :biff.graph/resolvers modules)
              {:middleware (mapcat :biff.graph/middleware modules)}))))

(defn module []
  {:biff.core/init   (fn [modules-var]
                       {:biff.graph/get-env #(env-from-modules @modules-var)})
   :biff.fx/handlers fx-handlers})
