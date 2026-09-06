# Query format

A query is a vector of attributes:

- scalar attributes are described with a keyword: `:foo`
- join attributes are described with a single-entry map, going from a keyword to
  a subquery: `{:foo [:bar]}`
- Optional attributes (scalar or join) are described by wrapping them with a
  `[:? ...]`: `[:? :foo]`, `{[:? :foo] [:bar]}`

The value that a join attribute describes can be either a single map or a vector
of maps. A join value _must_ be described by a join key: a scalar attribute
cannot be used to describe a map or vector of maps. If you don't want to
enumerate the keys in a join value, you can use `[:*]` (wildcard) as a join
subquery, however this should be done sparingly (e.g. when describing data from
an external API where you cannot enumerate the keys).

Scalar values can be anything that isn't a join value.

Other features from EQL such as union queries and parameters are not supported.

## AST format

`query->ast` converts the query to a format that's easier to work with
programmatically. The query format is only used as input to `resolver` /
`defresolver`; only the AST is actually stored with the returned resolver.

## Grammar

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
