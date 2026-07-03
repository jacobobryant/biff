(ns com.biffweb.graph
  "[View README](/libs/graph/)

   ## Query format

   A query is a vector of attributes:

   - scalar attributes are described with a keyword: `:foo`
   - join attributes are described with a single-entry map, going from a keyword
   to a subquery: `{:foo [:bar]}`
   - Optional attributes (scalar or join) are described by wrapping them with a
   `[:? ...]`: `[:? :foo]`, `{[:? :foo] [:bar]}`

   The value that a join attribute describes can be either a single map or a
   vector of maps. A join value _must_ be described by a join key: a scalar
   attribute cannot be used to describe a map or vector of maps. If you don't
   want to enumerate the keys in a join value, you can use `[:*]` (wildcard) as
   a join subquery, however this should be done sparingly (e.g. when describing
   data from an external API where you cannot enumerate the keys).

   Scalar values can be anything that isn't a join value.

   Other features from EQL such as union queries and parameters are not
   supported.

   ### AST format

   `query->ast` converts the query to a format that's easier to work with
   programmatically. The query format is only used as input to `resolver` /
   `defresolver`; only the AST is actually stored with the returned resolver.

   ### Grammar

   Queries:

   ```
   query      = [query-item, ...]
   query-item = attribute | join
   attribute  = keyword | [:? keyword]
   join       = {attribute (query | wildcard)}
   wildcard   = [:*]
   ```

   ASTs:

   ```
   ast       = {attribute opts, ...}
   attribute = keyword
   opts      = {:kind     (:scalar | :join),
                :optional boolean,
                :wildcard boolean,
                :children ast}
   ```

   ## Writing resolvers

   Resolvers must have an `:output` query, and they may have an `:input` query.
   biff.graph is strict about the output query matching the data being returned:
   if a join value is returned for a scalar attribute or vice versa, an
   assertion error is thrown. biff.graph also filters out any keys that are not
   included in the output query.

   Attributes in output queries do not need to be marked optional: all
   attributes in an output query are considered optional. When trying to resolve
   a particular attribute, the query engine will try all the resolvers which
   declare that attribute in the top level of their output query.

   Input queries do need to have their attributes marked as optional when
   appropriate. The resolver will only be called if all its non-optional inputs
   can be resolved.

   Attributes with nil values (`{:foo nil}`) are considered unresolved. If
   you're writing a resolver that you want to be called even if a particular
   attribute is nil, that attribute must be marked optional in the input query.

   `defresolver` does not auto-infer input or output queries as Pathom's
   `defresolver` does.

   ### Batch resolvers

   Resolvers that are defined with `:batch true` receive their input and return
   their output as a vector of maps instead of a single map. You must ensure
   that the output vector has the same order as the input vector.

   ### Validation

   When `*assert*` is true, biff.graph will pass the resolver output to
   `com.biffweb.core/validate`. Thus if you register your application's schema
   with `com.biffweb.core/register`, biff.graph will enforce that schema.

   ## Schema

   ### :biff.graph/resolver

   Map containing the keys:

   ```
   :biff.graph/id          ; qualified keyword
   :biff.graph/input-ast   ; return value of query->ast
   :biff.graph/output-ast  ; return value of query->ast
   :biff.graph/resolve-fn  ; (fn [ctx])
   :biff.graph/batch       ; boolean, optional
   ```

   Note that resolve-fn takes a single ctx parameter. Resolver input is passed
   under :biff.graph/input (though `resolver` / `defresolver` accept functions
   which take input as a second argument)

   ### :biff.graph/resolvers

   `[:sequential :biff.graph/resolver]`

   ### :biff.graph/middleware

   `[:sequential ifn?]`

   Each middleware function takes and returns a `:biff.graph/resolver` map."
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

(defn query->ast
  "Returns the AST for the given query."
  [query]
  (impl.ast/query->ast query))

(defn resolver
  "Returns a resolver map conforming to the :biff.graph/resolver schema.

   input and output are passed through query->ast. resolve-fn is a function of
   two arguments, (fn [ctx input] ...), that gets wrapped so that
   (:biff.graph/input ctx) is passed as the second argument (since
   :biff.graph/resolve-fn needs to be a function of one argument)."
  {:arglists '([{:keys [id input output batch resolve-fn]}])}
  [opts]
  (impl.r/resolver opts))

(defmacro defresolver
  "Wrapper for `resolver` with similar syntax to a defn with metadata.

   The :id for `resolver` is the defined var's fully-qualified name as a
   keyword. :input, :output, and :batch are specified in an options map that
   precedes the argument vector. :resolve-fn is taken from the body of the
   defresolver form.

     (defresolver my-resolver
       {:input [:x]
        :output [:y]}
       [ctx {:keys [x]}]
       {:y (inc x)})

   If the first form after the options map isn't a vector, it and the remaining
   forms will be passed to com.biffweb.fx/machine to generate a resolve
   function, with the state functions wrapped so that (:biff.graph/input ctx)
   is passed as a second argument:

     (defresolver my-resolver
       {:input [:foo]
        :output [:bar]}

       :start
       (fn [ctx input] ...)

       :next
       (fn [ctx input] ...))"
  [sym opts & args]
  `(impl.r/defresolver ~sym ~opts ~@args))

(defn new-ctx
  "Returns a `ctx` map that can be passed to `query`.

   Applies `middleware` to `resolvers`, then validates and indexes them for use
   by the query engine. Also applies some default middleware for caching,
   runtime validation, and exception handling.

   See schema for :biff.graph/resolvers and :biff.graph/middleware."
  {:arglists '([resolvers & {:keys [middleware]}])}
  [resolvers & {:as opts}]
  (impl.ctx/new-ctx resolvers opts))

(defn query
  "Executes the given query.

   ctx
     A map returned by `new-ctx`. ctx may also include whatever additional keys
     you'd like to make available to your resolvers.

   query
     A biff.graph query.

   input
     A map of starting data for the query. Can also be a vector of maps, in
     which case `query` will return a vector of maps.

   Throws an exception if any required attributes couldn't be resolved.

     (query ctx {:user/id 1} [:user/email :user/joined-at])
     => {:user/email \"...\", :user/joined-at #inst \"...\"}"
  ([ctx query]
   (impl.query/query ctx query))
  ([ctx input query]
   (impl.query/query ctx input query)))

(def ^{:doc "A biff.fx handlers map. Contains `:biff.graph.fx/query query`."}
  fx-handlers
  {:biff.graph.fx/query query})

(defn module
  "Returns a biff.core module.

   Includes :biff.fx/handlers. :biff.core/init collects :biff.graph/resolvers
   and :biff.graph/middleware from other modules and passes them to new-ctx, so
   you can pass the system map as ctx to `query`."
  []
  {:biff.core/init   (fn [modules-var]
                       {:biff.graph/get-ctx #(impl.ctx/ctx-from-modules
                                              @modules-var)})
   :biff.fx/handlers fx-handlers})
