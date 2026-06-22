# Graph Error Examples

## Invalid Query

```clojure
(require '[com.biffweb.graph :as graph])

(graph/query->ast [:*])
```

```
ERROR LOG com.biffweb.graph :biff.graph/error-example Invalid Query

<<< error <<<
Root: java.lang.AssertionError - `:biff.graph/query [:*]` is invalid:
  [["should not be :*"]]

Root stack trace:
  com.biffweb.core.impl.validation$assertion_error/invokeStatic at validation.clj:19
  com.biffweb.core.impl.validation$assertion_error/doInvoke at validation.clj:18
  clojure.lang.RestFn/invoke at RestFn.java:554
  com.biffweb.core.impl.validation$validate_map/invokeStatic at validation.clj:58
  com.biffweb.core.impl.validation$validate_map/invoke at validation.clj:45
  com.biffweb.core.impl.validation$validate_STAR_/invokeStatic at validation.clj:72
  com.biffweb.core.impl.validation$validate_STAR_/doInvoke at validation.clj:61
  clojure.lang.RestFn/invoke at RestFn.java:413
  com.biffweb.graph.impl.ast$query__GT_ast/invokeStatic at ast.clj:72
  com.biffweb.graph.impl.ast$query__GT_ast/invoke at ast.clj:70
  com.biffweb.graph$query__GT_ast/invokeStatic at graph.clj:47
  com.biffweb.graph$query__GT_ast/invoke at graph.clj:46
  com.biffweb.graph.error_example.G__12449$eval12937/invokeStatic at NO_SOURCE_FILE:0
  com.biffweb.graph.error_example.G__12449$eval12937/invoke at NO_SOURCE_FILE:-1
  clojure.lang.Compiler/eval at Compiler.java:7757
  clojure.lang.Compiler/eval at Compiler.java:7712
  clojure.core$eval/invokeStatic at core.clj:3236
  clojure.core$eval/invoke at core.clj:3232
>>> error >>>
```

## Invalid Resolver

```clojure
(require '[com.biffweb.graph :as graph])

(graph/resolver
 {:id nil
  :input []
  :output []
  :resolve-fn (fn [_ctx _input] {})})
```

```
ERROR LOG com.biffweb.graph :biff.graph/error-example Invalid Resolver

<<< error <<<
Root: java.lang.AssertionError - `:biff.graph/id nil` is invalid: should be a
  qualified keyword
{:biff.graph/id nil}

Root stack trace:
  com.biffweb.core.impl.validation$assertion_error/invokeStatic at validation.clj:19
  com.biffweb.core.impl.validation$assertion_error/doInvoke at validation.clj:18
  clojure.lang.RestFn/invoke at RestFn.java:554
  com.biffweb.core.impl.validation$validate_map/invokeStatic at validation.clj:58
  com.biffweb.core.impl.validation$validate_map/invoke at validation.clj:45
  com.biffweb.core.impl.validation$validate_STAR_/invokeStatic at validation.clj:72
  com.biffweb.core.impl.validation$validate_STAR_/doInvoke at validation.clj:61
  clojure.lang.RestFn/invoke at RestFn.java:426
  com.biffweb.graph.impl.validation$validate_resolver/invokeStatic at validation.clj:17
  com.biffweb.graph.impl.validation$validate_resolver/invoke at validation.clj:16
  com.biffweb.graph.impl.resolver$resolver/invokeStatic at resolver.clj:16
  com.biffweb.graph.impl.resolver$resolver/invoke at resolver.clj:12
  com.biffweb.graph$resolver/invokeStatic at graph.clj:50
  com.biffweb.graph$resolver/invoke at graph.clj:49
  com.biffweb.graph.error_example.G__12939$eval12942/invokeStatic at NO_SOURCE_FILE:0
  com.biffweb.graph.error_example.G__12939$eval12942/invoke at NO_SOURCE_FILE:-1
  clojure.lang.Compiler/eval at Compiler.java:7757
  clojure.lang.Compiler/eval at Compiler.java:7712
  clojure.core$eval/invokeStatic at core.clj:3236
  clojure.core$eval/invoke at core.clj:3232
>>> error >>>
```

## Invalid Resolver Query

```clojure
(require '[com.biffweb.graph :as graph])

(graph/resolver
 {:id :example/x
  :output [:*]
  :resolve-fn (fn [_ctx _input] {})})
```

```
ERROR LOG com.biffweb.graph :biff.graph/error-example Invalid Resolver Query

<<< error <<<
Root: java.lang.AssertionError - `:biff.graph/output-query [:*]` is invalid:
  [["should not be :*"]]
{:biff.graph/id :example/x}

Root stack trace:
  com.biffweb.core.impl.validation$assertion_error/invokeStatic at validation.clj:19
  com.biffweb.core.impl.validation$assertion_error/doInvoke at validation.clj:18
  clojure.lang.RestFn/invoke at RestFn.java:554
  com.biffweb.core.impl.validation$validate_map/invokeStatic at validation.clj:58
  com.biffweb.core.impl.validation$validate_map/invoke at validation.clj:45
  com.biffweb.core.impl.validation$validate_STAR_/invokeStatic at validation.clj:72
  com.biffweb.core.impl.validation$validate_STAR_/doInvoke at validation.clj:61
  clojure.lang.RestFn/invoke at RestFn.java:426
  com.biffweb.graph.impl.resolver$resolver/invokeStatic at resolver.clj:13
  com.biffweb.graph.impl.resolver$resolver/invoke at resolver.clj:12
  com.biffweb.graph$resolver/invokeStatic at graph.clj:50
  com.biffweb.graph$resolver/invoke at graph.clj:49
  com.biffweb.graph.error_example.G__12946$eval12949/invokeStatic at NO_SOURCE_FILE:0
  com.biffweb.graph.error_example.G__12946$eval12949/invoke at NO_SOURCE_FILE:-1
  clojure.lang.Compiler/eval at Compiler.java:7757
  clojure.lang.Compiler/eval at Compiler.java:7712
  clojure.core$eval/invokeStatic at core.clj:3236
  clojure.core$eval/invoke at core.clj:3232
>>> error >>>
```

## Invalid defresolver

```clojure
(require '[com.biffweb.graph :as graph])

(graph/defresolver invalid-defresolver
  {:batch :not-boolean}
  [_ctx _input]
  {})
```

```
ERROR LOG com.biffweb.graph :biff.graph/error-example Invalid defresolver

<<< error <<<
Root: java.lang.AssertionError - `:biff.graph/batch :not-boolean` is invalid:
  should be a boolean

Caused: clojure.lang.Compiler$CompilerException - Syntax error macroexpanding at (NO_SOURCE_FILE:0:0).
data: #:clojure.error{:phase :execution, :line 0, :column 0, :source "NO_SOURCE_FILE"}

Root stack trace:
  com.biffweb.core.impl.validation$assertion_error/invokeStatic at validation.clj:19
  com.biffweb.core.impl.validation$assertion_error/doInvoke at validation.clj:18
  clojure.lang.RestFn/invoke at RestFn.java:554
  com.biffweb.core.impl.validation$validate_map/invokeStatic at validation.clj:58
  com.biffweb.core.impl.validation$validate_map/invoke at validation.clj:45
  com.biffweb.core.impl.validation$validate_STAR_/invokeStatic at validation.clj:72
  com.biffweb.core.impl.validation$validate_STAR_/doInvoke at validation.clj:61
  clojure.lang.RestFn/invoke at RestFn.java:413
  com.biffweb.graph.error_example.G__12953$fn__12956/invokeStatic at NO_SOURCE_FILE:0
  com.biffweb.graph.error_example.G__12953$fn__12956/invoke at NO_SOURCE_FILE:-1
  clojure.lang.AFn/applyToHelper at AFn.java:152
  clojure.lang.AFn/applyTo at AFn.java:144
  clojure.lang.Compiler$InvokeExpr/eval at Compiler.java:4222
  clojure.lang.Compiler$DefExpr/eval at Compiler.java:464
  clojure.lang.Compiler/eval at Compiler.java:7762
  clojure.lang.Compiler/eval at Compiler.java:7712
  clojure.core$eval/invokeStatic at core.clj:3236
  clojure.core$eval/invoke at core.clj:3232
>>> error >>>
```

## Resolver Returns Scalar For Join

```clojure
(require '[com.biffweb.graph :as graph])

(def env
  (graph/new-env
   [(graph/resolver
     {:id :example/x
      :output [{:x [:y]}]
      :resolve-fn (fn [_ctx _input] {:x 1})})]))

(graph/query env [{:x [:y]}])
```

```
ERROR LOG com.biffweb.graph :biff.graph/error-example Resolver Returns Scalar For Join

<<< error <<<
Root: java.lang.AssertionError - Assert failed: :example/x declared :x as a
  join but value is a scalar
(impl.v/join-value? value)

Root stack trace:
  com.biffweb.graph.impl.env$select_output_value/invokeStatic at env.clj:27
  com.biffweb.graph.impl.env$select_output_value/invoke at env.clj:22
  com.biffweb.graph.impl.env$select_output$fn__12669/invoke at env.clj:49
  clojure.core$keep$fn__8691$fn__8692/invoke at core.clj:7521
  clojure.core.protocols$iterator_reduce_BANG_/invokeStatic at protocols.clj:42
  clojure.core.protocols$iter_reduce/invokeStatic at protocols.clj:52
  clojure.core.protocols$fn__8260/invokeStatic at protocols.clj:74
  clojure.core.protocols$fn__8260/invoke at protocols.clj:74
  clojure.core.protocols$fn__8207$G__8202__8220/invoke at protocols.clj:13
  clojure.core$transduce/invokeStatic at core.clj:7030
  clojure.core$into/invokeStatic at core.clj:7046
  clojure.core$into/invoke at core.clj:7033
  com.biffweb.graph.impl.env$select_output/invokeStatic at env.clj:46
  com.biffweb.graph.impl.env$select_output/invoke at env.clj:43
  com.biffweb.graph.impl.env$wrap_select_output$resolve_fn__12677/invoke at env.clj:58
  com.biffweb.graph.impl.env$wrap_validate_output$fn__12682/invoke at env.clj:65
  com.biffweb.graph.impl.env$wrap_cache$fn__12696/invoke at env.clj:109
  com.biffweb.graph.impl.query$resolve_attr$resolve_fn__12583/invoke at query.clj:52
  com.biffweb.graph.impl.query$resolve_attr$fn__12585$fn__12586/invoke at query.clj:58
  clojure.core$mapv$fn__8569/invoke at core.clj:7063
  clojure.lang.PersistentVector/reduce at PersistentVector.java:418
  clojure.core$reduce/invokeStatic at core.clj:6968
  clojure.core$mapv/invokeStatic at core.clj:7054
  clojure.core$mapv/invoke at core.clj:7054
  com.biffweb.graph.impl.query$resolve_attr$fn__12585/invoke at query.clj:58
  com.biffweb.graph.impl.query$resolve_attr$fn__12592/invoke at query.clj:72
  com.biffweb.graph.impl.query$apply_indexed/invokeStatic at query.clj:10
  com.biffweb.graph.impl.query$apply_indexed/invoke at query.clj:7
  com.biffweb.graph.impl.query$resolve_attr/invokeStatic at query.clj:72
  com.biffweb.graph.impl.query$resolve_attr/invoke at query.clj:25
  com.biffweb.graph.impl.query$resolve_entities$fn__12617$fn__12623/invoke at query.clj:132
  com.biffweb.graph.impl.query$apply_indexed/invokeStatic at query.clj:10
  com.biffweb.graph.impl.query$apply_indexed/invoke at query.clj:7
  com.biffweb.graph.impl.query$resolve_entities$fn__12617/invoke at query.clj:132
  clojure.core.protocols$iterator_reduce_BANG_/invokeStatic at protocols.clj:42
  clojure.core.protocols$iter_reduce/invokeStatic at protocols.clj:52
  clojure.core.protocols$fn__8260/invokeStatic at protocols.clj:74
  clojure.core.protocols$fn__8260/invoke at protocols.clj:74
  clojure.core.protocols$fn__8207$G__8202__8220/invoke at protocols.clj:13
  clojure.core$reduce/invokeStatic at core.clj:6969
  clojure.core$reduce/invoke at core.clj:6951
  com.biffweb.graph.impl.query$resolve_entities/invokeStatic at query.clj:113
  com.biffweb.graph.impl.query$resolve_entities/invoke at query.clj:112
  com.biffweb.graph.impl.query$query/invokeStatic at query.clj:194
  com.biffweb.graph.impl.query$query/invoke at query.clj:171
  com.biffweb.graph.impl.query$query/invokeStatic at query.clj:173
  com.biffweb.graph.impl.query$query/invoke at query.clj:171
  com.biffweb.graph$query/invokeStatic at graph.clj:60
  com.biffweb.graph$query/invoke at graph.clj:58
  com.biffweb.graph.error_example.G__12961$eval12966/invokeStatic at NO_SOURCE_FILE:0
  com.biffweb.graph.error_example.G__12961$eval12966/invoke at NO_SOURCE_FILE:-1
  clojure.lang.Compiler/eval at Compiler.java:7757
  clojure.lang.Compiler/eval at Compiler.java:7712
  clojure.core$eval/invokeStatic at core.clj:3236
  clojure.core$eval/invoke at core.clj:3232
>>> error >>>
```

## Resolver Returns Join For Scalar

```clojure
(require '[com.biffweb.graph :as graph])

(def env
  (graph/new-env
   [(graph/resolver
     {:id :example/x
      :output [:x]
      :resolve-fn (fn [_ctx _input] {:x {:y 1}})})]))

(graph/query env [:x])
```

```
ERROR LOG com.biffweb.graph :biff.graph/error-example Resolver Returns Join For Scalar

<<< error <<<
Root: java.lang.AssertionError - Assert failed: :example/x declared :x as a
  scalar but value is a join
(impl.v/scalar-value? value)

Root stack trace:
  com.biffweb.graph.impl.env$select_output_value/invokeStatic at env.clj:30
  com.biffweb.graph.impl.env$select_output_value/invoke at env.clj:22
  com.biffweb.graph.impl.env$select_output$fn__12669/invoke at env.clj:49
  clojure.core$keep$fn__8691$fn__8692/invoke at core.clj:7521
  clojure.core.protocols$iterator_reduce_BANG_/invokeStatic at protocols.clj:42
  clojure.core.protocols$iter_reduce/invokeStatic at protocols.clj:52
  clojure.core.protocols$fn__8260/invokeStatic at protocols.clj:74
  clojure.core.protocols$fn__8260/invoke at protocols.clj:74
  clojure.core.protocols$fn__8207$G__8202__8220/invoke at protocols.clj:13
  clojure.core$transduce/invokeStatic at core.clj:7030
  clojure.core$into/invokeStatic at core.clj:7046
  clojure.core$into/invoke at core.clj:7033
  com.biffweb.graph.impl.env$select_output/invokeStatic at env.clj:46
  com.biffweb.graph.impl.env$select_output/invoke at env.clj:43
  com.biffweb.graph.impl.env$wrap_select_output$resolve_fn__12677/invoke at env.clj:58
  com.biffweb.graph.impl.env$wrap_validate_output$fn__12682/invoke at env.clj:65
  com.biffweb.graph.impl.env$wrap_cache$fn__12696/invoke at env.clj:109
  com.biffweb.graph.impl.query$resolve_attr$resolve_fn__12583/invoke at query.clj:52
  com.biffweb.graph.impl.query$resolve_attr$fn__12585$fn__12586/invoke at query.clj:58
  clojure.core$mapv$fn__8569/invoke at core.clj:7063
  clojure.lang.PersistentVector/reduce at PersistentVector.java:418
  clojure.core$reduce/invokeStatic at core.clj:6968
  clojure.core$mapv/invokeStatic at core.clj:7054
  clojure.core$mapv/invoke at core.clj:7054
  com.biffweb.graph.impl.query$resolve_attr$fn__12585/invoke at query.clj:58
  com.biffweb.graph.impl.query$resolve_attr$fn__12592/invoke at query.clj:72
  com.biffweb.graph.impl.query$apply_indexed/invokeStatic at query.clj:10
  com.biffweb.graph.impl.query$apply_indexed/invoke at query.clj:7
  com.biffweb.graph.impl.query$resolve_attr/invokeStatic at query.clj:72
  com.biffweb.graph.impl.query$resolve_attr/invoke at query.clj:25
  com.biffweb.graph.impl.query$resolve_entities$fn__12617$fn__12623/invoke at query.clj:132
  com.biffweb.graph.impl.query$apply_indexed/invokeStatic at query.clj:10
  com.biffweb.graph.impl.query$apply_indexed/invoke at query.clj:7
  com.biffweb.graph.impl.query$resolve_entities$fn__12617/invoke at query.clj:132
  clojure.core.protocols$iterator_reduce_BANG_/invokeStatic at protocols.clj:42
  clojure.core.protocols$iter_reduce/invokeStatic at protocols.clj:52
  clojure.core.protocols$fn__8260/invokeStatic at protocols.clj:74
  clojure.core.protocols$fn__8260/invoke at protocols.clj:74
  clojure.core.protocols$fn__8207$G__8202__8220/invoke at protocols.clj:13
  clojure.core$reduce/invokeStatic at core.clj:6969
  clojure.core$reduce/invoke at core.clj:6951
  com.biffweb.graph.impl.query$resolve_entities/invokeStatic at query.clj:113
  com.biffweb.graph.impl.query$resolve_entities/invoke at query.clj:112
  com.biffweb.graph.impl.query$query/invokeStatic at query.clj:194
  com.biffweb.graph.impl.query$query/invoke at query.clj:171
  com.biffweb.graph.impl.query$query/invokeStatic at query.clj:173
  com.biffweb.graph.impl.query$query/invoke at query.clj:171
  com.biffweb.graph$query/invokeStatic at graph.clj:60
  com.biffweb.graph$query/invoke at graph.clj:58
  com.biffweb.graph.error_example.G__12968$eval12973/invokeStatic at NO_SOURCE_FILE:0
  com.biffweb.graph.error_example.G__12968$eval12973/invoke at NO_SOURCE_FILE:-1
  clojure.lang.Compiler/eval at Compiler.java:7757
  clojure.lang.Compiler/eval at Compiler.java:7712
  clojure.core$eval/invokeStatic at core.clj:3236
  clojure.core$eval/invoke at core.clj:3232
>>> error >>>
```

## Resolver Returns Invalid Typed Data

```clojure
(require '[com.biffweb.graph :as graph])

(def env
  (graph/new-env
   [(graph/resolver
     {:id :example/id
      :output [:biff.graph/id]
      :resolve-fn (fn [_ctx _input]
                    {:biff.graph/id "not-a-keyword"})})]))

(graph/query env [:biff.graph/id])
```

```
ERROR LOG com.biffweb.graph :biff.graph/error-example Resolver Returns Invalid Typed Data

<<< error <<<
Root: java.lang.AssertionError - `:biff.graph/id "not-a-keyword"` is invalid:
  should be a qualified keyword
{:biff.graph/id :example/id}

Root stack trace:
  com.biffweb.core.impl.validation$assertion_error/invokeStatic at validation.clj:19
  com.biffweb.core.impl.validation$assertion_error/doInvoke at validation.clj:18
  clojure.lang.RestFn/invoke at RestFn.java:554
  com.biffweb.core.impl.validation$validate_map/invokeStatic at validation.clj:58
  com.biffweb.core.impl.validation$validate_map/invoke at validation.clj:45
  com.biffweb.core.impl.validation$validate_STAR_/invokeStatic at validation.clj:72
  com.biffweb.core.impl.validation$validate_STAR_/doInvoke at validation.clj:61
  clojure.lang.RestFn/invoke at RestFn.java:426
  com.biffweb.graph.impl.env$wrap_validate_output$fn__12682/invoke at env.clj:65
  com.biffweb.graph.impl.env$wrap_cache$fn__12696/invoke at env.clj:109
  com.biffweb.graph.impl.query$resolve_attr$resolve_fn__12583/invoke at query.clj:52
  com.biffweb.graph.impl.query$resolve_attr$fn__12585$fn__12586/invoke at query.clj:58
  clojure.core$mapv$fn__8569/invoke at core.clj:7063
  clojure.lang.PersistentVector/reduce at PersistentVector.java:418
  clojure.core$reduce/invokeStatic at core.clj:6968
  clojure.core$mapv/invokeStatic at core.clj:7054
  clojure.core$mapv/invoke at core.clj:7054
  com.biffweb.graph.impl.query$resolve_attr$fn__12585/invoke at query.clj:58
  com.biffweb.graph.impl.query$resolve_attr$fn__12592/invoke at query.clj:72
  com.biffweb.graph.impl.query$apply_indexed/invokeStatic at query.clj:10
  com.biffweb.graph.impl.query$apply_indexed/invoke at query.clj:7
  com.biffweb.graph.impl.query$resolve_attr/invokeStatic at query.clj:72
  com.biffweb.graph.impl.query$resolve_attr/invoke at query.clj:25
  com.biffweb.graph.impl.query$resolve_entities$fn__12617$fn__12623/invoke at query.clj:132
  com.biffweb.graph.impl.query$apply_indexed/invokeStatic at query.clj:10
  com.biffweb.graph.impl.query$apply_indexed/invoke at query.clj:7
  com.biffweb.graph.impl.query$resolve_entities$fn__12617/invoke at query.clj:132
  clojure.core.protocols$iterator_reduce_BANG_/invokeStatic at protocols.clj:42
  clojure.core.protocols$iter_reduce/invokeStatic at protocols.clj:52
  clojure.core.protocols$fn__8260/invokeStatic at protocols.clj:74
  clojure.core.protocols$fn__8260/invoke at protocols.clj:74
  clojure.core.protocols$fn__8207$G__8202__8220/invoke at protocols.clj:13
  clojure.core$reduce/invokeStatic at core.clj:6969
  clojure.core$reduce/invoke at core.clj:6951
  com.biffweb.graph.impl.query$resolve_entities/invokeStatic at query.clj:113
  com.biffweb.graph.impl.query$resolve_entities/invoke at query.clj:112
  com.biffweb.graph.impl.query$query/invokeStatic at query.clj:194
  com.biffweb.graph.impl.query$query/invoke at query.clj:171
  com.biffweb.graph.impl.query$query/invokeStatic at query.clj:173
  com.biffweb.graph.impl.query$query/invoke at query.clj:171
  com.biffweb.graph$query/invokeStatic at graph.clj:60
  com.biffweb.graph$query/invoke at graph.clj:58
  com.biffweb.graph.error_example.G__12975$eval12980/invokeStatic at NO_SOURCE_FILE:0
  com.biffweb.graph.error_example.G__12975$eval12980/invoke at NO_SOURCE_FILE:-1
  clojure.lang.Compiler/eval at Compiler.java:7757
  clojure.lang.Compiler/eval at Compiler.java:7712
  clojure.core$eval/invokeStatic at core.clj:3236
  clojure.core$eval/invoke at core.clj:3232
>>> error >>>
```

## Conflicting Attribute Shapes In Env

```clojure
(require '[com.biffweb.graph :as graph])

(graph/new-env
 [(graph/resolver
   {:id :example/scalar-x
    :output [:x]
    :resolve-fn (fn [_ctx _input] {})})
  (graph/resolver
   {:id :example/join-x
    :output [{:x [:y]}]
    :resolve-fn (fn [_ctx _input] {})})])
```

```
ERROR LOG com.biffweb.graph :biff.graph/error-example Conflicting Attribute Shapes In Env

<<< error <<<
Root: java.lang.AssertionError - Assert failed: Got conflicting attr shapes
  for `:x`: {:kind :scalar} (from :example/scalar-x), {:kind :join} (from
  :example/join-x)
(= shape expected-shape)

Root stack trace:
  com.biffweb.graph.impl.validation$validate_query/invokeStatic at validation.clj:11
  com.biffweb.graph.impl.validation$validate_query/invoke at validation.clj:6
  com.biffweb.graph.impl.env$new_env/invokeStatic at env.clj:135
  com.biffweb.graph.impl.env$new_env/doInvoke at env.clj:121
  clojure.lang.RestFn/invoke at RestFn.java:426
  com.biffweb.graph$new_env/invokeStatic at graph.clj:56
  com.biffweb.graph$new_env/doInvoke at graph.clj:55
  clojure.lang.RestFn/invoke at RestFn.java:413
  com.biffweb.graph.error_example.G__12982$eval12985/invokeStatic at NO_SOURCE_FILE:0
  com.biffweb.graph.error_example.G__12982$eval12985/invoke at NO_SOURCE_FILE:-1
  clojure.lang.Compiler/eval at Compiler.java:7757
  clojure.lang.Compiler/eval at Compiler.java:7712
  clojure.core$eval/invokeStatic at core.clj:3236
  clojure.core$eval/invoke at core.clj:3232
>>> error >>>
```

## Missing Resolver Keys

```clojure
(require '[com.biffweb.graph :as graph])

(graph/new-env
 [{:biff.graph/id :example/bad
   :biff.graph/resolve-fn (fn [_ctx] {})}])
```

```
ERROR LOG com.biffweb.graph :biff.graph/error-example Missing Resolver Keys

<<< error <<<
Root: java.lang.AssertionError - Missing required keys: :biff.graph/input-ast,
  :biff.graph/output-ast
{:biff.graph/id :example/bad}

Root stack trace:
  com.biffweb.core.impl.validation$assertion_error/invokeStatic at validation.clj:19
  com.biffweb.core.impl.validation$assertion_error/doInvoke at validation.clj:18
  clojure.lang.RestFn/invoke at RestFn.java:485
  com.biffweb.core.impl.validation$validate_map/invokeStatic at validation.clj:49
  com.biffweb.core.impl.validation$validate_map/invoke at validation.clj:45
  com.biffweb.core.impl.validation$validate_STAR_/invokeStatic at validation.clj:72
  com.biffweb.core.impl.validation$validate_STAR_/doInvoke at validation.clj:61
  clojure.lang.RestFn/invoke at RestFn.java:426
  com.biffweb.graph.impl.validation$validate_resolver/invokeStatic at validation.clj:17
  com.biffweb.graph.impl.validation$validate_resolver/invoke at validation.clj:16
  clojure.core$run_BANG_$fn__8926/invoke at core.clj:7911
  clojure.lang.PersistentVector/reduce at PersistentVector.java:418
  clojure.core$reduce/invokeStatic at core.clj:6968
  clojure.core$run_BANG_/invokeStatic at core.clj:7906
  clojure.core$run_BANG_/invoke at core.clj:7906
  com.biffweb.graph.impl.env$new_env/invokeStatic at env.clj:122
  com.biffweb.graph.impl.env$new_env/doInvoke at env.clj:121
  clojure.lang.RestFn/invoke at RestFn.java:426
  com.biffweb.graph$new_env/invokeStatic at graph.clj:56
  com.biffweb.graph$new_env/doInvoke at graph.clj:55
  clojure.lang.RestFn/invoke at RestFn.java:413
  com.biffweb.graph.error_example.G__12991$eval12994/invokeStatic at NO_SOURCE_FILE:0
  com.biffweb.graph.error_example.G__12991$eval12994/invoke at NO_SOURCE_FILE:-1
  clojure.lang.Compiler/eval at Compiler.java:7757
  clojure.lang.Compiler/eval at Compiler.java:7712
  clojure.core$eval/invokeStatic at core.clj:3236
  clojure.core$eval/invoke at core.clj:3232
>>> error >>>
```

## Invalid Sequential Query Input

```clojure
(require '[com.biffweb.graph :as graph])

(def env
  (graph/new-env
   [(graph/resolver
     {:id :example/x
      :output [{:x [:y]}]
      :resolve-fn (fn [_ctx _input]
                    {:x [{:y 1}]})})]))

(graph/query env '({}) [{:x [:y]}])
```

```
No exception thrown.
```

## Conflicting Query Input Shape

```clojure
(require '[com.biffweb.graph :as graph])

(def env
  (graph/new-env
   [(graph/resolver
     {:id :example/x
      :output [{:x [:y]} :z]
      :resolve-fn (fn [_ctx _input] {})})]))

(graph/query env {:x 1} [:z])
```

```
ERROR LOG com.biffweb.graph :biff.graph/error-example Conflicting Query Input Shape

<<< error <<<
Root: java.lang.AssertionError - Assert failed: Input attr :x is a join but
  value is a scalar
(join-value? value)

Root stack trace:
  com.biffweb.graph.impl.validation$validate_input_value/invokeStatic at validation.clj:36
  com.biffweb.graph.impl.validation$validate_input_value/invoke at validation.clj:33
  com.biffweb.graph.impl.validation$validate_input$visit__12537/invoke at validation.clj:45
  clojure.core$run_BANG_$fn__8926/invoke at core.clj:7911
  clojure.lang.PersistentVector/reduce at PersistentVector.java:418
  clojure.core$reduce/invokeStatic at core.clj:6968
  clojure.core$run_BANG_/invokeStatic at core.clj:7906
  clojure.core$run_BANG_/invoke at core.clj:7906
  com.biffweb.graph.impl.validation$validate_input/invokeStatic at validation.clj:52
  com.biffweb.graph.impl.validation$validate_input/invoke at validation.clj:41
  com.biffweb.graph.impl.query$query/invokeStatic at query.clj:191
  com.biffweb.graph.impl.query$query/invoke at query.clj:171
  com.biffweb.graph$query/invokeStatic at graph.clj:62
  com.biffweb.graph$query/invoke at graph.clj:58
  com.biffweb.graph.error_example.G__13005$eval13010/invokeStatic at NO_SOURCE_FILE:0
  com.biffweb.graph.error_example.G__13005$eval13010/invoke at NO_SOURCE_FILE:-1
  clojure.lang.Compiler/eval at Compiler.java:7757
  clojure.lang.Compiler/eval at Compiler.java:7712
  clojure.core$eval/invokeStatic at core.clj:3236
  clojure.core$eval/invoke at core.clj:3232
>>> error >>>
```

## Resolver Throws Exception

```clojure
(require '[com.biffweb.graph :as graph])

(def env
  (graph/new-env
   [(graph/resolver
     {:id :example/a
      :output [:a]
      :resolve-fn (fn [_ctx _input]
                    (throw (ex-info "Boom" {:detail 1})))})]))

(graph/query env [:a])
```

```
ERROR LOG com.biffweb.graph :biff.graph/error-example Resolver Throws Exception

<<< error <<<
Root: clojure.lang.ExceptionInfo - Boom
data: {:detail 1}

Caused: clojure.lang.ExceptionInfo - Resolver :example/a threw an exception
data: #:biff.graph{:trace [{:resolving :query, :path [:a]} {:resolving :example/a}], :input {}}

Root stack trace:
  com.biffweb.graph.error_example.G__13012$fn__13015/invokeStatic at NO_SOURCE_FILE:0
  com.biffweb.graph.error_example.G__13012$fn__13015/invoke at NO_SOURCE_FILE:0
  com.biffweb.graph.impl.resolver$wrap_input$fn__12894/invoke at resolver.clj:10
  com.biffweb.graph.impl.env$wrap_exception$fn__12653/invoke at env.clj:12
  com.biffweb.graph.impl.env$wrap_select_output$resolve_fn__12677/invoke at env.clj:57
  com.biffweb.graph.impl.env$wrap_validate_output$fn__12682/invoke at env.clj:65
  com.biffweb.graph.impl.env$wrap_cache$fn__12696/invoke at env.clj:109
  com.biffweb.graph.impl.query$resolve_attr$resolve_fn__12583/invoke at query.clj:52
  com.biffweb.graph.impl.query$resolve_attr$fn__12585$fn__12586/invoke at query.clj:58
  clojure.core$mapv$fn__8569/invoke at core.clj:7063
  clojure.lang.PersistentVector/reduce at PersistentVector.java:418
  clojure.core$reduce/invokeStatic at core.clj:6968
  clojure.core$mapv/invokeStatic at core.clj:7054
  clojure.core$mapv/invoke at core.clj:7054
  com.biffweb.graph.impl.query$resolve_attr$fn__12585/invoke at query.clj:58
  com.biffweb.graph.impl.query$resolve_attr$fn__12592/invoke at query.clj:72
  com.biffweb.graph.impl.query$apply_indexed/invokeStatic at query.clj:10
  com.biffweb.graph.impl.query$apply_indexed/invoke at query.clj:7
  com.biffweb.graph.impl.query$resolve_attr/invokeStatic at query.clj:72
  com.biffweb.graph.impl.query$resolve_attr/invoke at query.clj:25
  com.biffweb.graph.impl.query$resolve_entities$fn__12617$fn__12623/invoke at query.clj:132
  com.biffweb.graph.impl.query$apply_indexed/invokeStatic at query.clj:10
  com.biffweb.graph.impl.query$apply_indexed/invoke at query.clj:7
  com.biffweb.graph.impl.query$resolve_entities$fn__12617/invoke at query.clj:132
  clojure.core.protocols$iterator_reduce_BANG_/invokeStatic at protocols.clj:42
  clojure.core.protocols$iter_reduce/invokeStatic at protocols.clj:52
  clojure.core.protocols$fn__8260/invokeStatic at protocols.clj:74
  clojure.core.protocols$fn__8260/invoke at protocols.clj:74
  clojure.core.protocols$fn__8207$G__8202__8220/invoke at protocols.clj:13
  clojure.core$reduce/invokeStatic at core.clj:6969
  clojure.core$reduce/invoke at core.clj:6951
  com.biffweb.graph.impl.query$resolve_entities/invokeStatic at query.clj:113
  com.biffweb.graph.impl.query$resolve_entities/invoke at query.clj:112
  com.biffweb.graph.impl.query$query/invokeStatic at query.clj:194
  com.biffweb.graph.impl.query$query/invoke at query.clj:171
  com.biffweb.graph.impl.query$query/invokeStatic at query.clj:173
  com.biffweb.graph.impl.query$query/invoke at query.clj:171
  com.biffweb.graph$query/invokeStatic at graph.clj:60
  com.biffweb.graph$query/invoke at graph.clj:58
  com.biffweb.graph.error_example.G__13012$eval13017/invokeStatic at NO_SOURCE_FILE:0
  com.biffweb.graph.error_example.G__13012$eval13017/invoke at NO_SOURCE_FILE:-1
  clojure.lang.Compiler/eval at Compiler.java:7757
  clojure.lang.Compiler/eval at Compiler.java:7712
  clojure.core$eval/invokeStatic at core.clj:3236
  clojure.core$eval/invoke at core.clj:3232
>>> error >>>
```

## Invalid get-env

```clojure
(require '[com.biffweb.graph :as graph])

(graph/query {:biff.graph/attr->resolvers {}
              :biff.graph/attr->shape-info {}
              :biff.graph/get-env :not-a-function}
             [])
```

```
ERROR LOG com.biffweb.graph :biff.graph/error-example Invalid get-env

<<< error <<<
Root: clojure.lang.ArityException - Wrong number of args (0) passed to:
  :not-a-function

Root stack trace:
  clojure.lang.Keyword/throwArity at Keyword.java:108
  clojure.lang.Keyword/invoke at Keyword.java:120
  com.biffweb.graph.impl.query$query/invokeStatic at query.clj:180
  com.biffweb.graph.impl.query$query/invoke at query.clj:171
  com.biffweb.graph.impl.query$query/invokeStatic at query.clj:173
  com.biffweb.graph.impl.query$query/invoke at query.clj:171
  com.biffweb.graph$query/invokeStatic at graph.clj:60
  com.biffweb.graph$query/invoke at graph.clj:58
  com.biffweb.graph.error_example.G__13019$eval13022/invokeStatic at NO_SOURCE_FILE:0
  com.biffweb.graph.error_example.G__13019$eval13022/invoke at NO_SOURCE_FILE:-1
  clojure.lang.Compiler/eval at Compiler.java:7757
  clojure.lang.Compiler/eval at Compiler.java:7712
  clojure.core$eval/invokeStatic at core.clj:3236
  clojure.core$eval/invoke at core.clj:3232
>>> error >>>
```

## Invalid Resolver Map In Env

```clojure
(require '[com.biffweb.graph :as graph])

(graph/query {:biff.graph/attr->resolvers
              {:x [{:biff.graph/id :example/bad
                    :biff.graph/output-ast {:x {:kind :scalar}}
                    :biff.graph/resolve-fn (fn [_ctx] {})}]}
              :biff.graph/attr->shape-info
              {:x {:biff.graph/id :example/bad
                   :biff.graph/attr :x
                   :biff.graph/attr-shape {:kind :scalar}}}}
             [:x])
```

```
ERROR LOG com.biffweb.graph :biff.graph/error-example Invalid Resolver Map In Env

<<< error <<<
Root: java.lang.AssertionError - `:biff.graph/attr->resolvers {:x
  [#:biff.graph{:id :example/bad, :output-ast {…` is invalid: {:x
  [#:biff.graph{:input-ast ["missing required key"]}]}

Root stack trace:
  com.biffweb.core.impl.validation$assertion_error/invokeStatic at validation.clj:19
  com.biffweb.core.impl.validation$assertion_error/doInvoke at validation.clj:18
  clojure.lang.RestFn/invoke at RestFn.java:554
  com.biffweb.core.impl.validation$validate_map/invokeStatic at validation.clj:58
  com.biffweb.core.impl.validation$validate_map/invoke at validation.clj:45
  com.biffweb.core.impl.validation$validate_STAR_/invokeStatic at validation.clj:72
  com.biffweb.core.impl.validation$validate_STAR_/doInvoke at validation.clj:61
  clojure.lang.RestFn/invoke at RestFn.java:426
  com.biffweb.graph.impl.query$query/invokeStatic at query.clj:183
  com.biffweb.graph.impl.query$query/invoke at query.clj:171
  com.biffweb.graph.impl.query$query/invokeStatic at query.clj:173
  com.biffweb.graph.impl.query$query/invoke at query.clj:171
  com.biffweb.graph$query/invokeStatic at graph.clj:60
  com.biffweb.graph$query/invoke at graph.clj:58
  com.biffweb.graph.error_example.G__13024$eval13027/invokeStatic at NO_SOURCE_FILE:0
  com.biffweb.graph.error_example.G__13024$eval13027/invoke at NO_SOURCE_FILE:-1
  clojure.lang.Compiler/eval at Compiler.java:7757
  clojure.lang.Compiler/eval at Compiler.java:7712
  clojure.core$eval/invokeStatic at core.clj:3236
  clojure.core$eval/invoke at core.clj:3232
>>> error >>>
```

## Conflicting Join Cardinalities

```clojure
(require '[com.biffweb.graph :as graph])

(def env
  (graph/new-env
   [(graph/resolver
     {:id :example/x-one
      :input [:id]
      :output [{:x [:y]}]
      :resolve-fn (fn [_ctx {:keys [id]}]
                    (when (= id 1)
                      {:x {:y 1}}))})
    (graph/resolver
     {:id :example/x-many
      :input [:id]
      :output [{:x [:y]}]
      :resolve-fn (fn [_ctx {:keys [id]}]
                    (when (= id 2)
                      {:x [{:y 2}]}))})]))

(graph/query env [{:id 1} {:id 2}] [{:x [:y]}])
```

```
ERROR LOG com.biffweb.graph :biff.graph/error-example Conflicting Join Cardinalities

<<< error <<<
Root: java.lang.AssertionError - Assert failed: Got conflicting cardinalities
  for :x. The value should either always be a map or always be a sequence of
  maps.
(or all-maps? all-seqs?)

Root stack trace:
  com.biffweb.graph.impl.query$resolve_joins/invokeStatic at query.clj:89
  com.biffweb.graph.impl.query$resolve_joins/invoke at query.clj:83
  com.biffweb.graph.impl.query$resolve_entities$fn__12617$fn__12625/invoke at query.clj:143
  com.biffweb.graph.impl.query$apply_indexed/invokeStatic at query.clj:10
  com.biffweb.graph.impl.query$apply_indexed/invoke at query.clj:7
  com.biffweb.graph.impl.query$resolve_entities$fn__12617/invoke at query.clj:143
  clojure.core.protocols$iterator_reduce_BANG_/invokeStatic at protocols.clj:42
  clojure.core.protocols$iter_reduce/invokeStatic at protocols.clj:52
  clojure.core.protocols$fn__8260/invokeStatic at protocols.clj:74
  clojure.core.protocols$fn__8260/invoke at protocols.clj:74
  clojure.core.protocols$fn__8207$G__8202__8220/invoke at protocols.clj:13
  clojure.core$reduce/invokeStatic at core.clj:6969
  clojure.core$reduce/invoke at core.clj:6951
  com.biffweb.graph.impl.query$resolve_entities/invokeStatic at query.clj:113
  com.biffweb.graph.impl.query$resolve_entities/invoke at query.clj:112
  com.biffweb.graph.impl.query$query/invokeStatic at query.clj:194
  com.biffweb.graph.impl.query$query/invoke at query.clj:171
  com.biffweb.graph$query/invokeStatic at graph.clj:62
  com.biffweb.graph$query/invoke at graph.clj:58
  com.biffweb.graph.error_example.G__13031$eval13042/invokeStatic at NO_SOURCE_FILE:0
  com.biffweb.graph.error_example.G__13031$eval13042/invoke at NO_SOURCE_FILE:-1
  clojure.lang.Compiler/eval at Compiler.java:7757
  clojure.lang.Compiler/eval at Compiler.java:7712
  clojure.core$eval/invokeStatic at core.clj:3236
  clojure.core$eval/invoke at core.clj:3232
>>> error >>>
```

## Invalid Query In graph/query

```clojure
(require '[com.biffweb.graph :as graph])

(graph/query {} [:*])
```

```
ERROR LOG com.biffweb.graph :biff.graph/error-example Invalid Query In graph/query

<<< error <<<
Root: java.lang.AssertionError - `:biff.graph/query [:*]` is invalid:
  [["should not be :*"]]

Root stack trace:
  com.biffweb.core.impl.validation$assertion_error/invokeStatic at validation.clj:19
  com.biffweb.core.impl.validation$assertion_error/doInvoke at validation.clj:18
  clojure.lang.RestFn/invoke at RestFn.java:554
  com.biffweb.core.impl.validation$validate_map/invokeStatic at validation.clj:58
  com.biffweb.core.impl.validation$validate_map/invoke at validation.clj:45
  com.biffweb.core.impl.validation$validate_STAR_/invokeStatic at validation.clj:72
  com.biffweb.core.impl.validation$validate_STAR_/doInvoke at validation.clj:61
  clojure.lang.RestFn/invoke at RestFn.java:413
  com.biffweb.graph.impl.query$query/invokeStatic at query.clj:175
  com.biffweb.graph.impl.query$query/invoke at query.clj:171
  com.biffweb.graph.impl.query$query/invokeStatic at query.clj:173
  com.biffweb.graph.impl.query$query/invoke at query.clj:171
  com.biffweb.graph$query/invokeStatic at graph.clj:60
  com.biffweb.graph$query/invoke at graph.clj:58
  com.biffweb.graph.error_example.G__13044$eval13047/invokeStatic at NO_SOURCE_FILE:0
  com.biffweb.graph.error_example.G__13044$eval13047/invoke at NO_SOURCE_FILE:-1
  clojure.lang.Compiler/eval at Compiler.java:7757
  clojure.lang.Compiler/eval at Compiler.java:7712
  clojure.core$eval/invokeStatic at core.clj:3236
  clojure.core$eval/invoke at core.clj:3232
>>> error >>>
```

## Invalid Query Input

```clojure
(require '[com.biffweb.graph :as graph])

(graph/query {} :invalid-input [:x])
```

```
ERROR LOG com.biffweb.graph :biff.graph/error-example Invalid Query Input

<<< error <<<
Root: java.lang.AssertionError - `:biff.graph/input :invalid-input` is
  invalid: invalid type

Root stack trace:
  com.biffweb.core.impl.validation$assertion_error/invokeStatic at validation.clj:19
  com.biffweb.core.impl.validation$assertion_error/doInvoke at validation.clj:18
  clojure.lang.RestFn/invoke at RestFn.java:554
  com.biffweb.core.impl.validation$validate_map/invokeStatic at validation.clj:58
  com.biffweb.core.impl.validation$validate_map/invoke at validation.clj:45
  com.biffweb.core.impl.validation$validate_STAR_/invokeStatic at validation.clj:72
  com.biffweb.core.impl.validation$validate_STAR_/doInvoke at validation.clj:61
  clojure.lang.RestFn/invoke at RestFn.java:413
  com.biffweb.graph.impl.query$query/invokeStatic at query.clj:175
  com.biffweb.graph.impl.query$query/invoke at query.clj:171
  com.biffweb.graph$query/invokeStatic at graph.clj:62
  com.biffweb.graph$query/invoke at graph.clj:58
  com.biffweb.graph.error_example.G__13049$eval13052/invokeStatic at NO_SOURCE_FILE:0
  com.biffweb.graph.error_example.G__13049$eval13052/invoke at NO_SOURCE_FILE:-1
  clojure.lang.Compiler/eval at Compiler.java:7757
  clojure.lang.Compiler/eval at Compiler.java:7712
  clojure.core$eval/invokeStatic at core.clj:3236
  clojure.core$eval/invoke at core.clj:3232
>>> error >>>
```

## Missing Env

```clojure
(require '[com.biffweb.graph :as graph])

(graph/query {} [:x])
```

```
ERROR LOG com.biffweb.graph :biff.graph/error-example Missing Env

<<< error <<<
Root: java.lang.AssertionError - Missing required keys:
  :biff.graph/attr->resolvers, :biff.graph/attr->shape-info

Root stack trace:
  com.biffweb.core.impl.validation$assertion_error/invokeStatic at validation.clj:19
  com.biffweb.core.impl.validation$assertion_error/doInvoke at validation.clj:18
  clojure.lang.RestFn/invoke at RestFn.java:485
  com.biffweb.core.impl.validation$validate_map/invokeStatic at validation.clj:49
  com.biffweb.core.impl.validation$validate_map/invoke at validation.clj:45
  com.biffweb.core.impl.validation$validate_STAR_/invokeStatic at validation.clj:72
  com.biffweb.core.impl.validation$validate_STAR_/doInvoke at validation.clj:61
  clojure.lang.RestFn/invoke at RestFn.java:426
  com.biffweb.graph.impl.query$query/invokeStatic at query.clj:183
  com.biffweb.graph.impl.query$query/invoke at query.clj:171
  com.biffweb.graph.impl.query$query/invokeStatic at query.clj:173
  com.biffweb.graph.impl.query$query/invoke at query.clj:171
  com.biffweb.graph$query/invokeStatic at graph.clj:60
  com.biffweb.graph$query/invoke at graph.clj:58
  com.biffweb.graph.error_example.G__13054$eval13057/invokeStatic at NO_SOURCE_FILE:0
  com.biffweb.graph.error_example.G__13054$eval13057/invoke at NO_SOURCE_FILE:-1
  clojure.lang.Compiler/eval at Compiler.java:7757
  clojure.lang.Compiler/eval at Compiler.java:7712
  clojure.core$eval/invokeStatic at core.clj:3236
  clojure.core$eval/invoke at core.clj:3232
>>> error >>>
```

## Conflicting Query Shape

```clojure
(require '[com.biffweb.graph :as graph])

(graph/query {:biff.graph/attr->resolvers {}
              :biff.graph/attr->shape-info
              {:x {:biff.graph/attr :x
                   :biff.graph/attr-shape {:kind :scalar}
                   :biff.graph/id :example/x}}}
             [{:x [:y]}])
```

```
ERROR LOG com.biffweb.graph :biff.graph/error-example Conflicting Query Shape

<<< error <<<
Root: java.lang.AssertionError - Assert failed: Got conflicting attr shapes
  for `:x`: {:kind :join} (from query), {:kind :scalar} (from :example/x)
(= shape expected-shape)

Root stack trace:
  com.biffweb.graph.impl.validation$validate_query/invokeStatic at validation.clj:11
  com.biffweb.graph.impl.validation$validate_query/invoke at validation.clj:6
  com.biffweb.graph.impl.query$query/invokeStatic at query.clj:186
  com.biffweb.graph.impl.query$query/invoke at query.clj:171
  com.biffweb.graph.impl.query$query/invokeStatic at query.clj:173
  com.biffweb.graph.impl.query$query/invoke at query.clj:171
  com.biffweb.graph$query/invokeStatic at graph.clj:60
  com.biffweb.graph$query/invoke at graph.clj:58
  com.biffweb.graph.error_example.G__13059$eval13062/invokeStatic at NO_SOURCE_FILE:0
  com.biffweb.graph.error_example.G__13059$eval13062/invoke at NO_SOURCE_FILE:-1
  clojure.lang.Compiler/eval at Compiler.java:7757
  clojure.lang.Compiler/eval at Compiler.java:7712
  clojure.core$eval/invokeStatic at core.clj:3236
  clojure.core$eval/invoke at core.clj:3232
>>> error >>>
```

## Unresolved Required Attribute

```clojure
(require '[com.biffweb.graph :as graph])

(graph/query {:biff.graph/attr->resolvers {}
              :biff.graph/attr->shape-info
              {:x {:biff.graph/attr :x
                   :biff.graph/attr-shape {:kind :scalar}}}}
             [:x])
```

```
ERROR LOG com.biffweb.graph :biff.graph/error-example Unresolved Required Attribute

<<< error <<<
Root: clojure.lang.ExceptionInfo - Entity could not be fully resolved
data: #:biff.graph{:missing [:x]}

Root stack trace:
  com.biffweb.graph.impl.query$query/invokeStatic at query.clj:197
  com.biffweb.graph.impl.query$query/invoke at query.clj:171
  com.biffweb.graph.impl.query$query/invokeStatic at query.clj:173
  com.biffweb.graph.impl.query$query/invoke at query.clj:171
  com.biffweb.graph$query/invokeStatic at graph.clj:60
  com.biffweb.graph$query/invoke at graph.clj:58
  com.biffweb.graph.error_example.G__13064$eval13067/invokeStatic at NO_SOURCE_FILE:0
  com.biffweb.graph.error_example.G__13064$eval13067/invoke at NO_SOURCE_FILE:-1
  clojure.lang.Compiler/eval at Compiler.java:7757
  clojure.lang.Compiler/eval at Compiler.java:7712
  clojure.core$eval/invokeStatic at core.clj:3236
  clojure.core$eval/invoke at core.clj:3232
>>> error >>>
```
