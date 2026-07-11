# com.biffweb.graph reference

### query->ast

[view source](../../src/com/biffweb/graph.clj#L46)

```
(query->ast query)

Returns the AST for the given query.
```

### resolver

[view source](../../src/com/biffweb/graph.clj#L51)

```
(resolver {:keys [id input output batch resolve-fn]})

Returns a resolver map conforming to the :biff.graph/resolver schema.

input and output are passed through query->ast. resolve-fn is a function of
two arguments, (fn [ctx input] ...), that gets wrapped so that
(:biff.graph/input ctx) is passed as the second argument (since
:biff.graph/resolve-fn needs to be a function of one argument).
```

### defresolver

[view source](../../src/com/biffweb/graph.clj#L62)

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
function, with the state functions wrapped so that (:biff.graph/input ctx)
is passed as a second argument:

  (defresolver my-resolver
    {:input [:foo]
     :output [:bar]}

    :start
    (fn [ctx input] ...)

    :next
    (fn [ctx input] ...))
```

### new-ctx

[view source](../../src/com/biffweb/graph.clj#L93)

```
(new-ctx resolvers & {:keys [middleware]})

Returns a `ctx` map that can be passed to `query`.

Applies `middleware` to `resolvers`, then validates and indexes them for use
by the query engine. Also applies some default middleware for caching,
runtime validation, and exception handling.

See schema for :biff.graph/resolvers and :biff.graph/middleware.
```

### query

[view source](../../src/com/biffweb/graph.clj#L105)

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

[view source](../../src/com/biffweb/graph.clj#L128)

```
A biff.fx handlers map. Contains `:biff.graph.fx/query query`.
```

### module

[view source](../../src/com/biffweb/graph.clj#L132)

```
(module)

Returns a biff.core module.

Includes :biff.fx/handlers. :biff.core/init collects :biff.graph/resolvers
and :biff.graph/middleware from other modules and passes them to new-ctx, so
you can pass the system map as ctx to `query`.
```
