# com.biffweb.graph reference

- [Query format](#queryformat)
  - [AST format](#astformat)
  - [Grammar](#grammar)
- [Writing resolvers](#writingresolvers)
  - [Batch resolvers](#batchresolvers)
- [Schema](#schema)
  - [:biff.graph/resolver](#biffgraphresolver)
  - [:biff.graph/resolvers](#biffgraphresolvers)
  - [:biff.graph/middleware](#biffgraphmiddleware)
- [API](#api)
  - [query->ast](#query-ast)
  - [resolver](#resolver)
  - [defresolver](#defresolver)
  - [new-ctx](#new-ctx)
  - [query](#query)
  - [fx-handlers](#fx-handlers)
  - [module](#module)

[View README](/libs/graph/)

## Query format

A query is a vector of attributes:

- scalar attributes are described with a keyword: `:foo`
- join attributes are described with a single-entry map, going from a keyword
to a subquery: `{:foo [:bar]}`
- Optional attributes (scalar or join) are described by wrapping them with a
`[:? ...]`: `[:foo]`, `{[:? :foo] [:bar]}`

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

`query`       | `[query-item]`
`query-item`  | `attribute | join`
`attribute`   | `keyword | [:? keyword]` | `:*` is invalid as an attribute keyword
`join`        | `{attribute (query | wildcard)}`
`wildcard`    | `[:*]`

ASTs:

`ast`       | `{attribute opts}`
`attribute` | `keyword`
`opts`      | `{:kind (:scalar | :join), :optional boolean, :wildcard boolean, :children ast}`

## Writing resolvers

Resolvers must have an `:output` query, and they may have an `:input` query.
biff.graph is strict about the output query matching the data being returned:
if a join value is returned for a scalar attribute or vice versa, an
assertion error is thrown. biff.graph also filters out any keys that are not
included in the output query.

Attributes in output queries do not need to be marked optional: all
attributes in an output query are considered optional. When trying to resolve
a particular attribute, the query engine will try all the resolvers which
declare that attribute in the top level of its output query.

Input queries do need to have their attributes marked as optional when
appropriate. The resolver will only be called if all its non-optional inputs
can be resolved.

In resolver output, attributes with nil values (`{:foo nil}`) are still
considered resolved. Missing attributes should be omitted from the resolver
output entirely (e.g. `(when-some [foo (maybe-get-foo)] {:foo foo})`).

`defresolver` does not auto-infer input or output queries as Pathom's
`defresolver` does.

### Batch resolvers

Resolvers that are defined with `:batch true` receive their input and return
their output as a vector of maps instead of a single map. You must ensure
that the output vector has the same order as the input vector.

## Schema

### :biff.graph/resolver

Map containing the keys:

`:biff.graph/id`         | qualified keyword
`:biff.graph/input-ast`  | return value of `query->ast`
`:biff.graph/output-ast` | return value of `query->ast`
`:biff.graph/resolve-fn` | function or function var
`:biff.graph/batch`      | boolean, optional

### :biff.graph/resolvers

`[:sequential :biff.graph/resolver]`

### :biff.graph/middleware

`[:sequential ifn?]`

Each middleware function takes and returns a `:biff.graph/resolver` map.

## API

### query->ast

[view source](../../libs/graph/src/com/biffweb/graph.clj#L143)

```
(query->ast query)

Returns the AST for the given query.
```

### resolver

[view source](../../libs/graph/src/com/biffweb/graph.clj#L148)

```
(resolver {:keys [id input output batch resolve-fn]})

Returns a resolver map conforming to the :biff.graph/resolver schema
```

### defresolver

[view source](../../libs/graph/src/com/biffweb/graph.clj#L154)

```
(defresolver sym opts & args)

Wrapper for `resolver` with similar syntax to a defn with metadata.

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
function:

  (defresolver my-resolver
    {:input [:foo]
     :output [:bar]}

    :start
    (fn [ctx input] ...)

    :next
    (fn [ctx input] ...))
```

### new-ctx

[view source](../../libs/graph/src/com/biffweb/graph.clj#L184)

```
(new-ctx resolvers & {:keys [middleware]})

Returns a `ctx` map that can be passed to `query`.

Applies `middleware` to `resolvers`, then validates and indexes them for use
by the query engine. Also applies some default middleware for caching,
runtime validation, and exception handling.

See schema for :biff.graph/resolvers and :biff.graph/middleware.
```

### query

[view source](../../libs/graph/src/com/biffweb/graph.clj#L196)

```
(query ctx query)
(query ctx input query)

Executes the given query.

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
  => {:user/email "...", :user/joined-at #inst "..."}
```

### fx-handlers

[view source](../../libs/graph/src/com/biffweb/graph.clj#L219)

```
A biff.fx handlers map. Contains `:biff.graph.fx/query query`.
```

### module

[view source](../../libs/graph/src/com/biffweb/graph.clj#L223)

```
(module)

Returns a biff.core module.

Includes :biff.fx/handlers. :biff.core/init collects :biff.graph/resolvers
and :biff.graph/middleware from other modules and returns keys needed for
`query`'s `ctx` map.
```
