# Writing resolvers

Resolvers must have an `:output` query, and they may have an `:input` query.
biff.graph is strict about the output query matching the data being returned: if
a join value is returned for a scalar attribute or vice versa, an assertion
error is thrown. biff.graph also filters out any keys that are not included in
the output query.

Attributes in output queries do not need to be marked optional: all attributes
in an output query are considered optional. When trying to resolve a particular
attribute, the query engine will try all the resolvers which declare that
attribute in the top level of their output query.

Input queries do need to have their attributes marked as optional when
appropriate. The resolver will only be called if all its non-optional inputs can
be resolved.

Attributes with nil values (`{:foo nil}`) are considered unresolved. If you're
writing a resolver that you want to be called even if a particular attribute is
nil, that attribute must be marked optional in the input query.

`defresolver` does not auto-infer input or output queries as Pathom's
`defresolver` does.

## Batch resolvers

Resolvers that are defined with `:batch true` receive their input and return
their output as a vector of maps instead of a single map. You must ensure that
the output vector has the same order as the input vector.

## Validation

When `*assert*` is true, biff.graph will pass the resolver output to
`com.biffweb.core/validate`. Thus if you register your application's schema with
`com.biffweb.core/register`, biff.graph will enforce that schema.
