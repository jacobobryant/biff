(ns com.biffweb.graph
  "A lightweight implementation of pathom-style resolvers.

  Supports:
  - Simple resolvers with declared input/output
  - Nested queries (joins)
  - Nested inputs (resolvers that require sub-attributes of their inputs)
  - Optional inputs ([:? :key] syntax)
  - Optional query items ([:? :key] in query vectors)
  - Global resolvers (no input)
  - Var-based resolvers (metadata-driven)
  - Batch resolvers (process multiple entities at once, breadth-first)
  - Per-query resolver caching (avoids redundant resolver calls)
  - Strict mode only (throws on missing data)

  Omits (compared to pathom3):
  - Plugin system
  - Lenient mode
  - Query planning (uses query directly)
  - EQL AST manipulation"
  (:require [com.biffweb.core :as biff.core]
            [com.biffweb.fx :as fx]))

(biff.core/register
 {:biff.graph/cache      [:fn #(instance? clojure.lang.IAtom %)]
  :biff.graph/get-index  'ifn?
  :biff.graph/index      'map?
  :biff.graph/middleware [:sequential 'ifn?]
  :biff.graph/resolvers  [:sequential 'any?]})

;; ---------------------------------------------------------------------------
;; Input helpers
;; ---------------------------------------------------------------------------

(defn- optional-input?
  "Returns true if input-item is an optional input marker [:? ...]."
  [input-item]
  (and (vector? input-item)
       (= :? (first input-item))))

(defn- unwrap-optional
  "Given an optional input marker [:? x], returns x."
  [input-item]
  (second input-item))

;; ---------------------------------------------------------------------------
;; Registry helpers
;; ---------------------------------------------------------------------------

(defn resolver
  "Define a resolver. Accepts either a map or a var.

  When given a var:
  - Uses var metadata for :input, :output, and :batch
  - Derives :id from the var's namespace and name
  - Stores the var itself (not the deref'd fn) as :resolve

  When given a map, expects:
    :id      - keyword, unique resolver id
    :input   - vector of input specs (keywords, join maps, or optional wrappers)
    :output  - vector of output descriptors (keywords or join maps)
    :resolve - (fn [ctx input-map] output-map), or if :batch is true,
               (fn [ctx [input-map ...]] [output-map ...])
    :batch   - (optional) if true, the resolver takes a vector of input maps
               and returns a vector of output maps in the same order

  Returns a resolver map with keys :id, :input, :output, :resolve, :batch."
  [resolver-or-map]
  (if (var? resolver-or-map)
    (let [var-meta (meta resolver-or-map)
          id       (keyword (str (:ns var-meta)) (str (:name var-meta)))
          input    (or (:input var-meta) [])
          output   (or (:output var-meta) [])
          batch    (boolean (:batch var-meta))]
      {:id      id
       :input   input
       :output  output
       :resolve resolver-or-map
       :batch   batch})
    (let [{:keys [id input output resolve batch]} resolver-or-map]
      (when-not id
        (throw (ex-info "Resolver must have an :id" {:resolver resolver-or-map})))
      (when-not resolve
        (throw (ex-info "Resolver must have a :resolve function" {:resolver resolver-or-map})))
      {:id      id
       :input   (or input [])
       :output  (or output [])
       :resolve resolve
       :batch   (boolean batch)})))

(declare parse-descriptor)

(defn- descriptor-surface-name
  [surface]
  (case surface
    :query "query"
    :input "resolver input"
    :output "resolver output"
    "descriptor"))

(defn- parse-subquery
  [surface attr subquery]
  (assert (vector? subquery)
          (str "Join descriptor for " attr " in " (descriptor-surface-name surface)
               " must use a vector subquery, got: " (pr-str subquery)))
  (if (= subquery [:*])
    {:wildcard? true
     :children  nil}
    (do
      (assert (not-any? #(= :* %) subquery)
              (str "Join descriptor for " attr " in " (descriptor-surface-name surface)
                   " must use [:*] by itself"))
      {:wildcard? false
       :children  (mapv #(parse-descriptor surface %) subquery)})))

(defn- parse-descriptor
  [surface item]
  (let [allow-optional? (not= surface :output)]
    (letfn [(parse* [item optional?]
              (cond
                (optional-input? item)
                (do
                  (assert allow-optional?
                          (str "Optional descriptors are not allowed in "
                               (descriptor-surface-name surface) ": " (pr-str item)))
                  (parse* (unwrap-optional item) true))

                (keyword? item)
                (do
                  (assert (not= :* item)
                          (str "[:*] may only appear as the sole item in a join descriptor, got "
                               (pr-str item) " in " (descriptor-surface-name surface)))
                  {:raw       item
                   :attr      item
                   :optional? optional?
                   :kind      :scalar
                   :wildcard? false
                   :children  nil})

                (map? item)
                (do
                  (assert (= 1 (count item))
                          (str "Descriptor maps in " (descriptor-surface-name surface)
                               " must contain exactly one entry, got: " (pr-str item)))
                  (let [[raw-attr subquery] (first item)
                        [attr optional?]    (if (optional-input? raw-attr)
                                              (do
                                                (assert allow-optional?
                                                        (str "Optional descriptors are not allowed in "
                                                             (descriptor-surface-name surface) ": "
                                                             (pr-str item)))
                                                [(unwrap-optional raw-attr) true])
                                              [raw-attr optional?])]
                    (assert (keyword? attr)
                            (str "Descriptor key in " (descriptor-surface-name surface)
                                 " must be a keyword, got: " (pr-str raw-attr)))
                    (let [{:keys [wildcard? children]} (parse-subquery surface attr subquery)]
                      {:raw       item
                       :attr      attr
                       :optional? optional?
                       :kind      :join
                       :wildcard? wildcard?
                       :children  children})))

                :else
                (assert false
                        (str "Invalid " (descriptor-surface-name surface)
                             " descriptor: " (pr-str item)))))]
      (parse* item false))))

(defn- parse-descriptors
  [surface items]
  (mapv #(parse-descriptor surface %) items))

(defn- flatten-descriptors
  [descriptors]
  (mapcat (fn [descriptor]
            (cons descriptor (flatten-descriptors (:children descriptor))))
          descriptors))

(defn- value-shape
  [v]
  (cond
    (nil? v) "nil"
    (map? v) "map"
    (sequential? v)
    (cond
      (every? #(or (map? %) (nil? %)) v) "sequential of maps"
      (every? #(not (map? %)) v) "sequential of non-maps"
      :else "sequential with mixed map/non-map values")
    :else "non-map scalar"))

(defn- valid-descriptor-value?
  [{:keys [kind]} v]
  (case kind
    :join   (or (nil? v)
                (map? v)
                (and (sequential? v)
                     (every? #(or (map? %) (nil? %)) v)))
    :scalar (and (not (map? v))
                 (or (not (sequential? v))
                     (every? #(not (map? %)) v)))))

(defn- assert-descriptor-value!
  [{:keys [attr kind]} v {:keys [context resolver-id]}]
  (assert (valid-descriptor-value? {:kind kind} v)
          (str "Attribute " attr " uses a " (name kind) " descriptor but got "
               (value-shape v) " while checking "
               (case context
                 :query-item "a query item"
                 :resolver-output (str "resolver output for " resolver-id)
                 "a descriptor"))))

(defn- normalize-join-value
  [v]
  (if (sequential? v)
    (mapv #(if (nil? %)
             (with-meta {} {::normalized-join-nil true})
             %)
          v)
    (if (nil? v)
      (with-meta {} {::normalized-join-nil true})
      v)))

(defn- normalized-join-nil?
  [v]
  (boolean (::normalized-join-nil (meta v))))

(declare project-entity)

(defn- project-value
  [{:keys [kind wildcard? children]} v]
  (if (= kind :scalar)
    v
    (let [normalized (normalize-join-value v)]
      (cond
        wildcard?
        normalized

        (sequential? normalized)
        (mapv (fn [child]
                (let [projected (project-entity children child)]
                  (if (normalized-join-nil? child)
                    (with-meta projected {::normalized-join-nil true})
                    projected)))
              normalized)

        :else
        (let [projected (project-entity children normalized)]
          (if (normalized-join-nil? normalized)
            (with-meta projected {::normalized-join-nil true})
            projected))))))

(defn- project-entity
  [descriptors entity]
  (reduce (fn [m descriptor]
            (let [attr (:attr descriptor)]
              (if (contains? entity attr)
                (assoc m attr (project-value descriptor (get entity attr)))
                m)))
          {}
          descriptors))

(defn- prepare-resolver-result
  [{:keys [id output-descriptors]} result]
  (reduce (fn [m descriptor]
            (let [attr (:attr descriptor)]
              (if (contains? result attr)
                (let [value (get result attr)]
                  (assert-descriptor-value! descriptor value
                                            {:context     :resolver-output
                                             :resolver-id id})
                  (assoc m attr (project-value descriptor value)))
                m)))
          {}
          output-descriptors))

(defn- validate-resolvers!
  [resolvers]
  (let [all-occurrences    (mapcat (fn [{:keys [id input-descriptors output-descriptors]}]
                                     (concat
                                      (map #(assoc % :surface :input :resolver-id id)
                                           (flatten-descriptors input-descriptors))
                                      (map #(assoc % :surface :output :resolver-id id)
                                           (flatten-descriptors output-descriptors))))
                                   resolvers)
        shape-by-attr      (reduce (fn [shapes {:keys [attr kind]}]
                                     (if-let [existing (get shapes attr)]
                                       (do
                                         (assert (= existing kind)
                                                 (str "Resolvers disagree on whether " attr
                                                      " is scalar or join-shaped"))
                                         shapes)
                                       (assoc shapes attr kind)))
                                   {}
                                   all-occurrences)
        output-join-shapes (reduce (fn [wildcards {:keys [attr kind wildcard?]}]
                                     (if (= kind :join)
                                       (if-let [existing (get wildcards attr)]
                                         (do
                                           (assert (= existing wildcard?)
                                                   (str "Resolvers disagree on whether output key " attr
                                                        " uses [:*]"))
                                           wildcards)
                                         (assoc wildcards attr wildcard?))
                                       wildcards))
                                   {}
                                   (mapcat #(flatten-descriptors (:output-descriptors %)) resolvers))]
    (doseq [{:keys [attr wildcard?]} (mapcat #(flatten-descriptors (:input-descriptors %)) resolvers)
            :when                    wildcard?]
      (assert (true? (get output-join-shapes attr))
              (str "Resolver input uses {:"
                   (namespace attr) "/" (name attr)
                   " [:*]} but no resolver output declares [:*] for " attr)))
    {:shape-by-attr         shape-by-attr
     :wildcard-output-attrs (into #{}
                                  (keep (fn [[attr wildcard?]]
                                          (when wildcard? attr)))
                                  output-join-shapes)}))

;; ---------------------------------------------------------------------------
;; Caching wrappers
;; ---------------------------------------------------------------------------

(defn- wrap-caching
  "Wrap a resolver's :resolve function with per-query caching.
  The cache is an atom stored in ctx under :biff.graph/cache.
  For non-batch resolvers, acts like memoize keyed on the input map.
  For batch resolvers, checks each input element individually and only
  sends unique uncached inputs to the underlying resolver."
  [{:keys [id batch resolve] :as resolver}]
  (if batch
    (fn [ctx inputs]
      (if-let [cache (:biff.graph/cache ctx)]
        (let [resolver-cache  (get @cache id {})
              uncached-idxs   (vec (keep (fn [i] (when-not (contains? resolver-cache (nth inputs i)) i))
                                         (range (count inputs))))
              ;; Deduplicate uncached inputs while preserving order
              unique-uncached (vec (distinct (map #(nth inputs %) uncached-idxs)))]
          (if (empty? unique-uncached)
            (mapv #(get resolver-cache %) inputs)
            (let [new-results   (resolve ctx unique-uncached)
                  _             (biff.core/validate new-results)
                  new-results   (mapv #(prepare-resolver-result resolver %) new-results)
                  _             (swap! cache update id
                                       (fn [m]
                                         (reduce (fn [m [input result]]
                                                   (assoc m input result))
                                                 (or m {})
                                                 (map vector unique-uncached new-results))))
                  updated-cache (get @cache id)]
              (mapv #(get updated-cache %) inputs))))
        (let [results (resolve ctx inputs)]
          (biff.core/validate results)
          (mapv #(prepare-resolver-result resolver %) results))))
    (fn [ctx input]
      (if-let [cache (:biff.graph/cache ctx)]
        (let [resolver-cache (get @cache id)]
          (if (and resolver-cache (contains? resolver-cache input))
            (get resolver-cache input)
            (let [result (resolve ctx input)]
              (biff.core/validate result)
              (let [result (prepare-resolver-result resolver result)]
                (swap! cache assoc-in [id input] result)
                result))))
        (let [result (resolve ctx input)]
          (biff.core/validate result)
          (prepare-resolver-result resolver result))))))

(defn build-index
  "Build an index from a collection of resolvers (maps or vars).
  Calls `resolver` on each item and wraps each resolver's :resolve function
  with caching logic. When a cache atom is present in the query context
  (under :biff.graph/cache), resolved results are memoized per input.

  Accepts optional keyword arguments:
    :middleware - a vector of functions (fn [resolver-map] -> resolver-map)
                  applied after resolvers are converted to maps but before
                  building the :resolvers-by-output index.

  Returns a map with:
    :resolvers-by-output  {attr-key [resolver ...]}
    :all-resolvers        [resolver ...]"
  [resolvers & {:keys [middleware]}]
  (let [resolvers                                     (mapv resolver resolvers)
        resolvers                                     (reduce (fn [rs mw] (mapv mw rs))
                                                              resolvers
                                                              middleware)
        resolvers                                     (mapv (fn [r]
                                                              (assoc r
                                                                     :input-descriptors (parse-descriptors :input (:input r))
                                                                     :output-descriptors (parse-descriptors :output (:output r))))
                                                            resolvers)
        {:keys [shape-by-attr wildcard-output-attrs]} (validate-resolvers! resolvers)
        resolvers                                     (mapv (fn [r] (assoc r :resolve (wrap-caching r)))
                                                            resolvers)]
    {:resolvers-by-output
     (reduce (fn [idx r]
               (reduce (fn [idx descriptor]
                         (update idx (:attr descriptor) (fnil conj []) r))
                       idx
                       (:output-descriptors r)))
             {}
             resolvers)
     :shape-by-attr         shape-by-attr
     :wildcard-output-attrs wildcard-output-attrs
     :all-resolvers         resolvers}))

(def ^:private index-for-modules
  (memoize
   (fn [modules]
     (let [middleware (not-empty (vec (mapcat :biff.graph/middleware modules)))]
       (apply build-index
              (mapcat :biff.graph/resolvers modules)
              (cond-> []
                middleware (conj :middleware middleware)))))))

(declare query)

(defmacro defresolver
  "Defines a resolver backed by a biff.fx machine.

   The first form after the parameter vector is the main resolver body.
   Remaining keyword/fn pairs (if any) define additional machine states."
  {:arglists '([sym opts-map [ctx input] body & states])}
  [sym opts & args]
  (let [{:keys [input output]}    opts
        [params body & state-kvs] args
        machine-name              (keyword (str *ns*) (str sym))
        ctx-sym                   (first params)
        input-sym                 (second params)]
    `(let [start-fn# (fn [~ctx-sym ~input-sym] ~body)
           machine#  (fx/machine ~machine-name
                                 :start (fn [{resolver-input# :biff.fx/resolver-input :as ctx#}]
                                          (start-fn# ctx# resolver-input#))
                                 ~@state-kvs)]
       (defn ~sym
         ~(merge (when (seq input) {:input input})
                 {:output output})
         [ctx# input#]
         (machine# (assoc ctx# :biff.fx/resolver-input input#))))))
;; ---------------------------------------------------------------------------
;; Query engine
;; ---------------------------------------------------------------------------

(declare ^:private process-entities)

(defn- find-resolver-candidates
  "Find all resolvers that can provide `attr`."
  [ctx attr]
  (get-in (or (:biff.graph/index ctx)
              (when-some [get-index (:biff.graph/get-index ctx)]
                (get-index)))
          [:resolvers-by-output attr]))

(defn- validate-query-descriptor!
  [index descriptor]
  (let [{:keys [shape-by-attr wildcard-output-attrs]} index
        {:keys [attr kind wildcard? children]}        descriptor]
    (when-let [known-shape (get shape-by-attr attr)]
      (assert (= known-shape kind)
              (str "Query uses " attr " as a " (name kind)
                   " descriptor, but resolver metadata classifies it as "
                   (name known-shape))))
    (when wildcard?
      (assert (contains? wildcard-output-attrs attr)
              (str "Query uses {:"
                   (namespace attr) "/" (name attr)
                   " [:*]} but no resolver output declares [:*] for " attr)))
    (doseq [child children]
      (validate-query-descriptor! index child))))

;; ---------------------------------------------------------------------------
;; Sentinel helpers
;; ---------------------------------------------------------------------------

(defn- unresolved-result
  "Create a sentinel indicating an entity couldn't satisfy a required attribute."
  [attr entity]
  {::unresolved-entity true
   ::failed-attr       attr
   ::available-keys    (vec (keys entity))})

(defn- unresolved-result?
  "Check if a result is an unresolved-entity sentinel."
  [v]
  (and (map? v) (::unresolved-entity v)))

;; ---------------------------------------------------------------------------
;; Breadth-first batch processing
;; ---------------------------------------------------------------------------

(defn- resolve-attrs-batch
  "Resolve a single attr for multiple entities, trying resolver candidates in order.
  Never throws for resolution failures — returns ::unresolved for values that
  cannot be resolved. The resolving set tracks attrs for cycle detection."
  [ctx entities attr resolving]
  (if (contains? resolving attr)
    (vec (repeat (count entities) ::unresolved))
    (let [resolving'  (conj resolving attr)
          candidates  (find-resolver-candidates ctx attr)
          init-values (mapv (fn [e] (if (contains? e attr) (get e attr) ::unresolved)) entities)]
      (loop [values     init-values
             candidates (seq candidates)]
        (let [unresolved-idxs (vec (keep-indexed (fn [i v] (when (= v ::unresolved) i)) values))]
          (if (or (empty? unresolved-idxs) (nil? candidates))
            values
            (let [r                   (first candidates)
                  unresolved-entities (mapv #(nth entities %) unresolved-idxs)
                  ;; Resolve inputs via process-entities with the original input query.
                  ;; Entities that can't satisfy required inputs come back as sentinels.
                  resolved-inputs     (process-entities ctx unresolved-entities
                                                        (:input-descriptors r)
                                                        resolving')
                  valid-mask          (mapv #(not (unresolved-result? %)) resolved-inputs)
                  valid-inputs        (vec (keep-indexed (fn [i m] (when (nth valid-mask i) m))
                                                         resolved-inputs))
                  valid-global-idxs   (vec (keep-indexed
                                            (fn [i valid?]
                                              (when valid? (nth unresolved-idxs i)))
                                            valid-mask))]
              (if (empty? valid-inputs)
                (recur values (next candidates))
                (let [results    (if (:batch r)
                                   ((:resolve r) ctx valid-inputs)
                                   (mapv #((:resolve r) ctx %) valid-inputs))
                      new-values (reduce
                                  (fn [vals [global-idx result]]
                                    (if (contains? result attr)
                                      (assoc vals global-idx (get result attr))
                                      vals))
                                  values
                                  (map vector valid-global-idxs results))]
                  (recur new-values (next candidates)))))))))))

(defn- join-child-info
  [descriptor v result]
  (cond
    (unresolved-result? result)
    {:type :already-failed}

    (= v ::unresolved)
    {:type :unresolved}

    :else
    (do
      (assert-descriptor-value! descriptor v {:context :query-item})
      (let [normalized (normalize-join-value v)]
        {:type  (if (sequential? normalized) :seq :map)
         :value normalized}))))

(defn- join-children-for-processing
  [child-info]
  (into []
        (mapcat (fn [{:keys [type value]}]
                  (case type
                    :map (if (normalized-join-nil? value)
                           []
                           [value])
                    :seq (remove normalized-join-nil? value)
                    [])))
        child-info))

(defn- assemble-seq-children
  [items processed offset]
  (let [processed       (or processed [])
        active-count    (count (remove normalized-join-nil? items))
        active-children (subvec processed offset (+ offset active-count))]
    (loop [remaining-items    items
           remaining-children active-children
           acc                []]
      (if (empty? remaining-items)
        {:children acc
         :offset   (+ offset active-count)}
        (let [item (first remaining-items)]
          (if (normalized-join-nil? item)
            (recur (rest remaining-items) remaining-children (conj acc {}))
            (let [child (first remaining-children)]
              (if (unresolved-result? child)
                {:sentinel child
                 :offset   (+ offset active-count)}
                (recur (rest remaining-items)
                       (subvec remaining-children 1)
                       (conj acc child))))))))))

(defn- process-entities
  "Process descriptors against multiple entities using breadth-first traversal.
  Always returns a vector of results, never throws for resolution failures.
  Each result is either a map of resolved attributes or an unresolved-result
  sentinel indicating the entity couldn't satisfy a required attribute.
  For optional query items, unresolved values are silently omitted.
  For required query items, unresolved values cause the entity result to become
  a sentinel. The resolving set is passed through for input resolution (cycle
  detection) and reset to #{} for sub-queries (new resolution context)."
  [ctx entities descriptors resolving]
  (if (empty? entities)
    []
    (reduce
     (fn [results {:keys [attr kind optional? wildcard? children] :as descriptor}]
       (let [enriched (mapv (fn [e r]
                              (if (unresolved-result? r) e (merge e r)))
                            entities
                            results)
             values   (resolve-attrs-batch ctx enriched attr resolving)]
         (if (= kind :join)
           (let [child-info   (mapv #(join-child-info descriptor %1 %2) values results)
                 all-children (when-not wildcard?
                                (join-children-for-processing child-info))
                 processed    (when (and (not wildcard?) (seq all-children))
                                (vec (process-entities ctx all-children children #{})))]
             (loop [rs     results
                    idx    0
                    offset 0]
               (if (>= idx (count results))
                 rs
                 (let [{:keys [type value]} (nth child-info idx)
                       r                    (nth rs idx)]
                   (case type
                     :already-failed
                     (recur rs (inc idx) offset)

                     :unresolved
                     (if optional?
                       (recur rs (inc idx) offset)
                       (recur (assoc rs idx (unresolved-result attr (nth enriched idx)))
                              (inc idx)
                              offset))

                     :map
                     (cond
                       wildcard?
                       (recur (assoc rs idx (assoc r attr value))
                              (inc idx)
                              offset)

                       (normalized-join-nil? value)
                       (recur (assoc rs idx (assoc r attr {}))
                              (inc idx)
                              offset)

                       :else
                       (let [child (nth processed offset)]
                         (if (unresolved-result? child)
                           (recur (assoc rs idx child) (inc idx) (inc offset))
                           (recur (assoc rs idx (assoc r attr child))
                                  (inc idx)
                                  (inc offset)))))

                     :seq
                     (if wildcard?
                       (recur (assoc rs idx (assoc r attr value))
                              (inc idx)
                              offset)
                       (let [{:keys [sentinel children offset]} (assemble-seq-children
                                                                 value
                                                                 processed
                                                                 offset)]
                         (if sentinel
                           (recur (assoc rs idx sentinel) (inc idx) offset)
                           (recur (assoc rs idx (assoc r attr children))
                                  (inc idx)
                                  offset)))))))))
           (mapv (fn [result v enriched-ent]
                   (cond
                     (unresolved-result? result)
                     result

                     (= v ::unresolved)
                     (if optional? result (unresolved-result attr enriched-ent))

                     :else
                     (do
                       (assert-descriptor-value! descriptor v {:context :query-item})
                       (assoc result attr v))))
                 results
                 values
                 enriched))))
     (vec (repeat (count entities) {}))
     descriptors)))

;; ---------------------------------------------------------------------------
;; Public API
;; ---------------------------------------------------------------------------

(defn query
  "Run an EQL query using the provided resolver index.

  Arguments:
    ctx    - context map; must include :biff.graph/index (from build-index).
             Any other keys are passed through to resolver functions.
    entity - (optional) initial entity map with seed data, or a vector of entity
             maps for batch querying. Defaults to {} if omitted.
    query  - EQL query vector, e.g. [:user/name {:user/friends [:user/name]}]
             Supports optional items via [:? :attr] syntax.

  Returns a map satisfying the query when given a single entity,
  or a vector of maps when given a vector of entities.
  Throws ExceptionInfo if any required attribute cannot be resolved."
  ([ctx query-vec]
   (query ctx {} query-vec))
  ([{:keys [biff.graph/index biff.graph/get-index] :as ctx} entity-or-entities query-vec]
   (let [index             (or index
                               (when get-index
                                 (get-index)))
         query-descriptors (parse-descriptors :query query-vec)
         _                 (doseq [descriptor query-descriptors]
                             (validate-query-descriptor! index descriptor))
         ctx               (assoc ctx
                                  :biff.graph/index index
                                  :biff.graph/cache (atom {}))
         is-vec?           (sequential? entity-or-entities)
         entities          (if is-vec? (vec entity-or-entities) [(or entity-or-entities {})])
         results           (process-entities ctx entities query-descriptors #{})]
     (doseq [r results]
       (when (unresolved-result? r)
         (throw (ex-info (str "No resolver found for attribute " (::failed-attr r)
                              " with available inputs " (::available-keys r))
                         {::resolve-error true
                          :attr           (::failed-attr r)
                          :available-keys (::available-keys r)}))))
     (if is-vec? results (first results)))))

(def fx-handlers
  {:biff.graph.fx/query #'query})

(defn module
  []
  {:biff.core/init
   (fn [modules-var]
     {:biff.graph/get-index
      (fn []
        (index-for-modules @modules-var))})
   :biff.fx/handlers fx-handlers})
