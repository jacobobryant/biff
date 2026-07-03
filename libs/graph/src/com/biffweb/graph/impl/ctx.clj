(ns com.biffweb.graph.impl.ctx
  (:require
   [com.biffweb.core :as biff.core]
   [com.biffweb.graph.impl.ast :as impl.ast]
   [com.biffweb.graph.impl.validation :as impl.v]))

(defn wrap-exception [{:biff.graph/keys [id resolve-fn] :as resolver}]
  (assoc resolver
         :biff.graph/resolve-fn
         (fn [ctx]
           (try
             (resolve-fn ctx)
             (catch Exception e
               (throw
                (ex-info (str "Resolver " id " threw an exception")
                         (select-keys ctx [:biff.graph/trace
                                           :biff.graph/input])
                         e)))))))

(declare select-output)

(defn- select-output-value [value
                            attr
                            {:keys [kind children wildcard]}
                            resolver-id]
  (case kind
    :join (assert (impl.v/join-value? value)
                  (str resolver-id " declared " attr
                       " as a join but value is a scalar"))
    :scalar (assert (impl.v/scalar-value? value)
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
                  (when-some [value (get output attr)]
                    [attr (select-output-value value
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

(defn- shape-info [resolvers]
  (for [resolver     resolvers
        query-ast    [(:biff.graph/input-ast resolver)
                      (:biff.graph/output-ast resolver)]
        [attr shape] (impl.ast/ast-seq query-ast)]
    {:biff.graph/attr       attr
     :biff.graph/attr-shape shape
     :biff.graph/id         (:biff.graph/id resolver)}))

(defn new-ctx [resolvers & {:keys [middleware]}]
  (run! impl.v/validate-resolver resolvers)
  (let [middleware       (into [wrap-cache
                                wrap-validate-output
                                wrap-select-output
                                wrap-exception]
                               middleware)
        resolvers        (mapv (apply comp middleware) resolvers)
        _                (run! impl.v/validate-resolver resolvers)
        attr->shape-info (into {}
                               (map (juxt :biff.graph/attr identity))
                               (shape-info resolvers))]
    (doseq [r         resolvers
            query-key [:biff.graph/input-ast :biff.graph/output-ast]]
      (impl.v/validate-query
       {:biff.graph/id               (:biff.graph/id r)
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

(def ctx-from-modules
  (memoize
   (fn [modules]
     (new-ctx (mapcat :biff.graph/resolvers modules)
              {:middleware (mapcat :biff.graph/middleware modules)}))))
