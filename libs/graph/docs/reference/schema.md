# Schema

## :biff.graph/resolver

Map containing the keys:

```clojure
:biff.graph/id          ; qualified keyword
:biff.graph/input-ast   ; return value of query->ast
:biff.graph/output-ast  ; return value of query->ast
:biff.graph/resolve-fn  ; (fn [ctx])
:biff.graph/batch       ; boolean, optional
```

Note that resolve-fn takes a single ctx parameter. Resolver input is passed
under :biff.graph/input (though `resolver` / `defresolver` accept functions
which take input as a second argument)

## :biff.graph/resolvers

`[:sequential :biff.graph/resolver]`

## :biff.graph/middleware

`[:sequential ifn?]`

Each middleware function takes and returns a `:biff.graph/resolver` map.
