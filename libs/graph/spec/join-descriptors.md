# Join descriptor spec

## Motivation

`biff.graph` should make join-vs-scalar contracts explicit at every boundary
where data shape matters.

These guardrails exist so that:

- queries declare which child keys they actually need
- resolver metadata makes map-shaped inputs and outputs explicit
- join/scalar mismatches fail obviously instead of silently depending on
  incidental resolver behavior

## Surfaces covered

- query vectors passed to `biff.graph/query`
- resolver `:input` descriptors
- resolver `:output` descriptors
- `biff.graph/build-index` validation
- runtime validation while resolving query items and resolver outputs
- resolver-output projection/filtering

## Terms

- **scalar descriptor** — a bare key such as `:user/name`
- **join descriptor** — a key with an explicit subquery, e.g.
  `{:user/widgets [:widget/id :widget/created-at]}`
- **single join value** — a map-valued attribute, or `nil` which is normalized
  as described below
- **many join value** — a sequential value whose elements are maps or `nil`

A join descriptor's subquery MUST be either:

- a vector of query items
- `[:*]`

`nil` is never valid inside a descriptor.

This spec treats descriptor shape as a required contract. It does not require
subquery equality between different descriptors unless explicitly stated.

## Assertions

All validation failures described in this spec are assertions.

- When `*assert*` is true, they MUST throw `AssertionError`.
- When `*assert*` is false, the assertion checks MUST compile away and perform
  no validation.
- Resolver-output filtering/projection is not an assertion and MUST still happen
  regardless of `*assert*`.

## Query descriptors

- A query that expects a map-shaped attribute MUST use a join descriptor instead
  of a bare key.
- A bare key in a query MUST be treated as a scalar descriptor.
- A join descriptor in a query MUST be treated as an explicit declaration that
  the caller needs a map-shaped value.

Example:

```clojure
[{:user/widgets [:widget/id :widget/created-at]}]
```

This form is valid.

```clojure
[:user/widgets]
```

This form is invalid when `:user/widgets` is join-shaped.

## Resolver descriptors

- A resolver output that may return a join-shaped value MUST declare that key as
  a join descriptor in `:output`.
- A resolver input that expects a join-shaped value MUST declare that key as a
  join descriptor in `:input`.
- A bare key in resolver metadata MUST be treated as a scalar descriptor.

Example:

```clojure
(defn user-widgets
  {:output [{:user/widgets [:widget/id :widget/created-at]}]}
  ...)
```

Resolver-output subqueries define the projection/filtering contract for that
resolver output. They do not, by themselves, require query planning.

## Optional joins

Existing optional syntax remains available. Optional joins MUST still be
explicit joins.

Examples:

```clojure
{[:? :user/widgets] [:widget/id]}
```

```clojure
[:? {:user/widgets [:widget/id]}]
```

An optional bare key such as `[:? :user/widgets]` is invalid when
`:user/widgets` is join-shaped.

## `[:*]`

`[:*]` MAY be used only as the sole item in a subquery vector:

```clojure
{:some-api/stuff [:*]}
```

`[:*]` is an escape hatch for cases where the consumer genuinely cannot
enumerate the keys in a map-shaped value.

- `[:*]` MUST mark the attribute as a join descriptor.
- `[:*]` SHOULD be used sparingly.
- If `:*` appears in a subquery alongside any other item, descriptor validation
  MUST throw `AssertionError`.
- `[:*]` is valid in a query or resolver `:input` only when at least one
  resolver declares `{:k [:*]}` for the same key in `:output`.

When `[:*]` is used:

- a resolver `:output` descriptor of `{:k [:*]}` MUST preserve the full
  map-shaped value for `:k` at that resolver boundary
- a query item of `{:k [:*]}` MUST preserve the full map-shaped value for `:k`
  in the query result

To preserve a full map-shaped value across both the resolver boundary and the
final query result, both the resolver `:output` descriptor and the consuming
query MUST use `[:*]`.

If either side uses an explicit subquery instead, that side's explicit
projection/filtering rules apply.

## Runtime validation

### Query-time validation

When resolving a query item, `biff.graph` MUST validate the resolved value
against the query descriptor:

- a join descriptor for a single value requires a map or `nil`
- a join descriptor for a many value requires a sequential value whose elements
  are each maps or `nil`
- a scalar descriptor for a many value requires a sequential value whose
  elements are each non-map values
- a scalar descriptor for a single value MUST NOT receive a map

If there is a mismatch, resolution MUST fail immediately with `AssertionError`.

The assertion message MUST identify at least:

- the attribute
- whether the descriptor was scalar or join
- the actual value shape
- that the mismatch happened while checking a query item

### Join nil normalization

A join key is resolved only when the containing map includes that key.

- If a resolver returns `{:user/stuff nil}`, that key is resolved.
- If a resolver returns `nil` or `{}` and the key is absent, that key is
  unresolved.

For resolved join values:

- a single join value of `nil` MUST be treated as an empty map during query
  processing and in the query result
- a many join value element of `nil` MUST be treated as an empty map element
  during query processing and in the query result

Example:

```clojure
[{:user/stuff [:stuff/a :stuff/b]}]
```

If the resolved value is:

```clojure
{:user/stuff nil}
```

then the query result MUST include:

```clojure
{:user/stuff {}}
```

If `:user/stuff` is absent entirely, then `:user/stuff` is unresolved rather
than normalized.

### Resolver-output validation

Whenever a resolver returns, `biff.graph` MUST validate each declared output key
against the resolver's `:output` descriptor:

- a join descriptor for a single value requires a map or `nil`
- a join descriptor for a many value requires a sequential value whose elements
  are each maps or `nil`
- a scalar descriptor for a many value requires a sequential value whose
  elements are each non-map values
- a scalar descriptor for a single value MUST NOT receive a map

If there is a mismatch, resolution MUST fail immediately with `AssertionError`.

The assertion message MUST identify at least:

- the resolver id
- the attribute
- whether the descriptor was scalar or join
- the actual value shape
- that the mismatch happened while checking resolver output

## Resolver-output projection

After validation, `biff.graph` MUST filter each resolver result so that only the
keys declared by the resolver's `:output` descriptor remain.

Example:

```clojure
{:output [:foo/a :foo/b]}
```

If the resolver returns:

```clojure
{:foo/a 1 :foo/b 2 :foo/c 3}
```

then `biff.graph` MUST treat the resolver output as:

```clojure
{:foo/a 1 :foo/b 2}
```

For join descriptors, this filtering MUST apply recursively using the declared
subquery.

Example:

```clojure
{:output [{:user/widgets [:widget/id]}]}
```

If the resolver returns:

```clojure
{:user/widgets [{:widget/id 1 :widget/name "a"}]}
```

then `biff.graph` MUST treat the resolver output as:

```clojure
{:user/widgets [{:widget/id 1}]}
```

If a resolved join value is `nil`, projection MUST apply after nil
normalization. For a single join value that means projecting from `{}`. For a
many join value that means projecting from `{}` elementwise.

If the declared subquery is `[:*]`, `biff.graph` MUST keep the full map-shaped
value for that attribute instead of recursively filtering child keys.

## Static validation

### `build-index`

`biff.graph/build-index` MUST reject resolver sets that disagree on whether a
key is scalar or join-shaped.

For this validation, every occurrence of a key in resolver `:input` or `:output`
counts. That includes input-vs-input, output-vs-output, and input-vs-output
disagreements. `[:*]` counts as join-shaped.

For a given output key, resolvers MUST agree on whether the output subquery uses
`[:*]` or an explicit subquery. If multiple resolvers output the same key and
one declares `{:k [:*]}` while another declares `{:k [...]}`, `build-index`
MUST throw `AssertionError`.

`build-index` MUST also reject resolver `:input` descriptors that use `{:k [:*]}`
unless at least one resolver `:output` descriptor uses `{:k [:*]}` for that same
key.

Examples of mismatches that MUST fail:

- one resolver declares `:input [:user/widgets]`
- another resolver declares `:output [{:user/widgets [:widget/id]}]`

and:

- one resolver declares `:output [:session/user]`
- another resolver declares `:input [{:session/user [:user/id]}]`

and:

- one resolver declares `:output [{:some-api/stuff [:*]}]`
- another resolver declares `:output [{:some-api/stuff [:stuff/id]}]`

and:

- one resolver declares `:input [{:some-api/stuff [:*]}]`
- no resolver declares `:output [{:some-api/stuff [:*]}]`

This validation is only about join-vs-scalar shape. It MUST NOT require
subquery equality.

When building the resolver index, join descriptors in `:output` MUST still be
indexed by their attribute key so that resolver lookup remains keyed by
attribute, not by the full descriptor form.

### `query`

Before resolution starts, `biff.graph/query` MUST reject query vectors that use
a key with the wrong join/scalar shape relative to the resolver index.

`biff.graph/query` MUST also reject use of `{:k [:*]}` unless the resolver
index includes at least one resolver output descriptor of `{:k [:*]}` for that
same key.

Examples of mismatches that MUST fail:

- the query asks for `:user/widgets`
- the resolver index classifies `:user/widgets` as a join

and:

- the query asks for `{:user/name [:name/parts]}`
- the resolver index classifies `:user/name` as a scalar

and:

- the query asks for `{:some-api/stuff [:*]}`
- no resolver output descriptor uses `{:some-api/stuff [:*]}`

This static query-time validation only applies to keys whose join/scalar shape
is known from the resolver index.

If a join-shaped or scalar-shaped value only appears in the seed entity and no
resolver metadata classifies that key, `query` MAY defer the shape check until
runtime. In that case, once resolution reaches the query item, the runtime
query-time validation rules above MUST still enforce the descriptor shape and
MUST throw `AssertionError` on mismatch.

## Non-goals

- This spec does not add query planning.
- This spec does not add automatic inference of subqueries.
- This spec does not add an additional wildcard or blob descriptor beyond
  `[:*]`.
- This spec does not add extra public helpers for validating or normalizing
  query descriptors.
- This spec does not define alternative output-descriptor syntax; explicit
  subqueries remain the required format for joins.
