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
```

<details>
<summary>Root stack trace</summary>

```
Root stack trace:
  com.biffweb.core.impl.validation$assertion_error/invokeStatic at validation.clj:19
  com.biffweb.core.impl.validation$assertion_error/doInvoke at validation.clj:18
  clojure.lang.RestFn/invoke at RestFn.java:554
  com.biffweb.core.impl.validation$validate_map/invokeStatic at validation.clj:58
  com.biffweb.core.impl.validation$validate_map/invoke at validation.clj:45
  com.biffweb.core.impl.validation$validate_STAR_/invokeStatic at validation.clj:72
  com.biffweb.core.impl.validation$validate_STAR_/doInvoke at validation.clj:61
  clojure.lang.RestFn/invoke at RestFn.java:413
  com.biffweb.graph.impl.ast$query__GT_ast/invokeStatic at ast.clj:80
  com.biffweb.graph.impl.ast$query__GT_ast/invoke at ast.clj:78
  com.biffweb.graph$query__GT_ast/invokeStatic at graph.clj:48
  com.biffweb.graph$query__GT_ast/invoke at graph.clj:47
  com.biffweb.graph.error_example.G__12459$eval12956/invokeStatic at NO_SOURCE_FILE:0
  com.biffweb.graph.error_example.G__12459$eval12956/invoke at NO_SOURCE_FILE:-1
  clojure.lang.Compiler/eval at Compiler.java:7757
  clojure.lang.Compiler/eval at Compiler.java:7712
  clojure.core$eval/invokeStatic at core.clj:3236
  clojure.core$eval/invoke at core.clj:3232
>>> error >>>
```
</details>

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
```

<details>
<summary>Root stack trace</summary>

```
Root stack trace:
  com.biffweb.core.impl.validation$assertion_error/invokeStatic at validation.clj:19
  com.biffweb.core.impl.validation$assertion_error/doInvoke at validation.clj:18
  clojure.lang.RestFn/invoke at RestFn.java:554
  com.biffweb.core.impl.validation$validate_map/invokeStatic at validation.clj:58
  com.biffweb.core.impl.validation$validate_map/invoke at validation.clj:45
  com.biffweb.core.impl.validation$validate_STAR_/invokeStatic at validation.clj:72
  com.biffweb.core.impl.validation$validate_STAR_/doInvoke at validation.clj:61
  clojure.lang.RestFn/invoke at RestFn.java:426
  com.biffweb.graph.impl.validation$validate_resolver/invokeStatic at validation.clj:20
  com.biffweb.graph.impl.validation$validate_resolver/invoke at validation.clj:19
  com.biffweb.graph.impl.resolver$resolver/invokeStatic at resolver.clj:16
  com.biffweb.graph.impl.resolver$resolver/invoke at resolver.clj:12
  com.biffweb.graph$resolver/invokeStatic at graph.clj:51
  com.biffweb.graph$resolver/invoke at graph.clj:50
  com.biffweb.graph.error_example.G__12958$eval12961/invokeStatic at NO_SOURCE_FILE:0
  com.biffweb.graph.error_example.G__12958$eval12961/invoke at NO_SOURCE_FILE:-1
  clojure.lang.Compiler/eval at Compiler.java:7757
  clojure.lang.Compiler/eval at Compiler.java:7712
  clojure.core$eval/invokeStatic at core.clj:3236
  clojure.core$eval/invoke at core.clj:3232
>>> error >>>
```
</details>

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
```

<details>
<summary>Root stack trace</summary>

```
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
  com.biffweb.graph$resolver/invokeStatic at graph.clj:51
  com.biffweb.graph$resolver/invoke at graph.clj:50
  com.biffweb.graph.error_example.G__12965$eval12968/invokeStatic at NO_SOURCE_FILE:0
  com.biffweb.graph.error_example.G__12965$eval12968/invoke at NO_SOURCE_FILE:-1
  clojure.lang.Compiler/eval at Compiler.java:7757
  clojure.lang.Compiler/eval at Compiler.java:7712
  clojure.core$eval/invokeStatic at core.clj:3236
  clojure.core$eval/invoke at core.clj:3232
>>> error >>>
```
</details>

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
```

<details>
<summary>Root stack trace</summary>

```
Root stack trace:
  com.biffweb.core.impl.validation$assertion_error/invokeStatic at validation.clj:19
  com.biffweb.core.impl.validation$assertion_error/doInvoke at validation.clj:18
  clojure.lang.RestFn/invoke at RestFn.java:554
  com.biffweb.core.impl.validation$validate_map/invokeStatic at validation.clj:58
  com.biffweb.core.impl.validation$validate_map/invoke at validation.clj:45
  com.biffweb.core.impl.validation$validate_STAR_/invokeStatic at validation.clj:72
  com.biffweb.core.impl.validation$validate_STAR_/doInvoke at validation.clj:61
  clojure.lang.RestFn/invoke at RestFn.java:413
  com.biffweb.graph.error_example.G__12972$fn__12975/invokeStatic at NO_SOURCE_FILE:0
  com.biffweb.graph.error_example.G__12972$fn__12975/invoke at NO_SOURCE_FILE:-1
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
</details>

## Resolver Returns Scalar For Join

```clojure
(require '[com.biffweb.graph :as graph])

(def ctx
  (graph/new-ctx
   [(graph/resolver
     {:id :example/x
      :output [{:x [:y]}]
      :resolve-fn (fn [_ctx _input] {:x 1})})]))

(graph/query ctx [{:x [:y]}])
```

```
ERROR LOG com.biffweb.graph :biff.graph/error-example Resolver Returns Scalar For Join

<<< error <<<
Root: java.lang.AssertionError - Assert failed: :example/x declared :x as a
  join but value is a scalar
(impl.v/join-value? value)
```

<details>
<summary>Root stack trace</summary>

```
Root stack trace:
  com.biffweb.graph.impl.ctx$select_output_value/invokeStatic at ctx.clj:27
  com.biffweb.graph.impl.ctx$select_output_value/invoke at ctx.clj:22
  com.biffweb.graph.impl.ctx$select_output$fn__12688/invoke at ctx.clj:49
  clojure.core$keep$fn__8691$fn__8692/invoke at core.clj:7521
  clojure.core.protocols$iterator_reduce_BANG_/invokeStatic at protocols.clj:42
  clojure.core.protocols$iter_reduce/invokeStatic at protocols.clj:52
  clojure.core.protocols$fn__8260/invokeStatic at protocols.clj:74
  clojure.core.protocols$fn__8260/invoke at protocols.clj:74
  clojure.core.protocols$fn__8207$G__8202__8220/invoke at protocols.clj:13
  clojure.core$transduce/invokeStatic at core.clj:7030
  clojure.core$into/invokeStatic at core.clj:7046
  clojure.core$into/invoke at core.clj:7033
  com.biffweb.graph.impl.ctx$select_output/invokeStatic at ctx.clj:46
  com.biffweb.graph.impl.ctx$select_output/invoke at ctx.clj:43
  com.biffweb.graph.impl.ctx$wrap_select_output$resolve_fn__12696/invoke at ctx.clj:58
  com.biffweb.graph.impl.ctx$wrap_validate_output$fn__12701/invoke at ctx.clj:65
  com.biffweb.graph.impl.ctx$wrap_cache$fn__12715/invoke at ctx.clj:109
  com.biffweb.graph.impl.query$resolve_attr$resolve_fn__12600/invoke at query.clj:52
  com.biffweb.graph.impl.query$resolve_attr$fn__12602$fn__12603/invoke at query.clj:58
  clojure.core$mapv$fn__8569/invoke at core.clj:7063
  clojure.lang.PersistentVector/reduce at PersistentVector.java:418
  clojure.core$reduce/invokeStatic at core.clj:6968
  clojure.core$mapv/invokeStatic at core.clj:7054
  clojure.core$mapv/invoke at core.clj:7054
  com.biffweb.graph.impl.query$resolve_attr$fn__12602/invoke at query.clj:58
  com.biffweb.graph.impl.query$resolve_attr$fn__12609/invoke at query.clj:72
  com.biffweb.graph.impl.query$apply_indexed/invokeStatic at query.clj:10
  com.biffweb.graph.impl.query$apply_indexed/invoke at query.clj:7
  com.biffweb.graph.impl.query$resolve_attr/invokeStatic at query.clj:72
  com.biffweb.graph.impl.query$resolve_attr/invoke at query.clj:25
  com.biffweb.graph.impl.query$resolve_entities$fn__12633$fn__12639/invoke at query.clj:127
  com.biffweb.graph.impl.query$apply_indexed/invokeStatic at query.clj:10
  com.biffweb.graph.impl.query$apply_indexed/invoke at query.clj:7
  com.biffweb.graph.impl.query$resolve_entities$fn__12633/invoke at query.clj:127
  clojure.core.protocols$iterator_reduce_BANG_/invokeStatic at protocols.clj:42
  clojure.core.protocols$iter_reduce/invokeStatic at protocols.clj:52
  clojure.core.protocols$fn__8260/invokeStatic at protocols.clj:74
  clojure.core.protocols$fn__8260/invoke at protocols.clj:74
  clojure.core.protocols$fn__8207$G__8202__8220/invoke at protocols.clj:13
  clojure.core$reduce/invokeStatic at core.clj:6969
  clojure.core$reduce/invoke at core.clj:6951
  com.biffweb.graph.impl.query$resolve_entities/invokeStatic at query.clj:109
  com.biffweb.graph.impl.query$resolve_entities/invoke at query.clj:108
  com.biffweb.graph.impl.query$query/invokeStatic at query.clj:195
  com.biffweb.graph.impl.query$query/invoke at query.clj:167
  com.biffweb.graph.impl.query$query/invokeStatic at query.clj:169
  com.biffweb.graph.impl.query$query/invoke at query.clj:167
  com.biffweb.graph$query/invokeStatic at graph.clj:61
  com.biffweb.graph$query/invoke at graph.clj:59
  com.biffweb.graph.error_example.G__12980$eval12985/invokeStatic at NO_SOURCE_FILE:0
  com.biffweb.graph.error_example.G__12980$eval12985/invoke at NO_SOURCE_FILE:-1
  clojure.lang.Compiler/eval at Compiler.java:7757
  clojure.lang.Compiler/eval at Compiler.java:7712
  clojure.core$eval/invokeStatic at core.clj:3236
  clojure.core$eval/invoke at core.clj:3232
>>> error >>>
```
</details>

## Resolver Returns Join For Scalar

```clojure
(require '[com.biffweb.graph :as graph])

(def ctx
  (graph/new-ctx
   [(graph/resolver
     {:id :example/x
      :output [:x]
      :resolve-fn (fn [_ctx _input] {:x {:y 1}})})]))

(graph/query ctx [:x])
```

```
ERROR LOG com.biffweb.graph :biff.graph/error-example Resolver Returns Join For Scalar

<<< error <<<
Root: java.lang.AssertionError - Assert failed: :example/x declared :x as a
  scalar but value is a join
(impl.v/scalar-value? value)
```

<details>
<summary>Root stack trace</summary>

```
Root stack trace:
  com.biffweb.graph.impl.ctx$select_output_value/invokeStatic at ctx.clj:30
  com.biffweb.graph.impl.ctx$select_output_value/invoke at ctx.clj:22
  com.biffweb.graph.impl.ctx$select_output$fn__12688/invoke at ctx.clj:49
  clojure.core$keep$fn__8691$fn__8692/invoke at core.clj:7521
  clojure.core.protocols$iterator_reduce_BANG_/invokeStatic at protocols.clj:42
  clojure.core.protocols$iter_reduce/invokeStatic at protocols.clj:52
  clojure.core.protocols$fn__8260/invokeStatic at protocols.clj:74
  clojure.core.protocols$fn__8260/invoke at protocols.clj:74
  clojure.core.protocols$fn__8207$G__8202__8220/invoke at protocols.clj:13
  clojure.core$transduce/invokeStatic at core.clj:7030
  clojure.core$into/invokeStatic at core.clj:7046
  clojure.core$into/invoke at core.clj:7033
  com.biffweb.graph.impl.ctx$select_output/invokeStatic at ctx.clj:46
  com.biffweb.graph.impl.ctx$select_output/invoke at ctx.clj:43
  com.biffweb.graph.impl.ctx$wrap_select_output$resolve_fn__12696/invoke at ctx.clj:58
  com.biffweb.graph.impl.ctx$wrap_validate_output$fn__12701/invoke at ctx.clj:65
  com.biffweb.graph.impl.ctx$wrap_cache$fn__12715/invoke at ctx.clj:109
  com.biffweb.graph.impl.query$resolve_attr$resolve_fn__12600/invoke at query.clj:52
  com.biffweb.graph.impl.query$resolve_attr$fn__12602$fn__12603/invoke at query.clj:58
  clojure.core$mapv$fn__8569/invoke at core.clj:7063
  clojure.lang.PersistentVector/reduce at PersistentVector.java:418
  clojure.core$reduce/invokeStatic at core.clj:6968
  clojure.core$mapv/invokeStatic at core.clj:7054
  clojure.core$mapv/invoke at core.clj:7054
  com.biffweb.graph.impl.query$resolve_attr$fn__12602/invoke at query.clj:58
  com.biffweb.graph.impl.query$resolve_attr$fn__12609/invoke at query.clj:72
  com.biffweb.graph.impl.query$apply_indexed/invokeStatic at query.clj:10
  com.biffweb.graph.impl.query$apply_indexed/invoke at query.clj:7
  com.biffweb.graph.impl.query$resolve_attr/invokeStatic at query.clj:72
  com.biffweb.graph.impl.query$resolve_attr/invoke at query.clj:25
  com.biffweb.graph.impl.query$resolve_entities$fn__12633$fn__12639/invoke at query.clj:127
  com.biffweb.graph.impl.query$apply_indexed/invokeStatic at query.clj:10
  com.biffweb.graph.impl.query$apply_indexed/invoke at query.clj:7
  com.biffweb.graph.impl.query$resolve_entities$fn__12633/invoke at query.clj:127
  clojure.core.protocols$iterator_reduce_BANG_/invokeStatic at protocols.clj:42
  clojure.core.protocols$iter_reduce/invokeStatic at protocols.clj:52
  clojure.core.protocols$fn__8260/invokeStatic at protocols.clj:74
  clojure.core.protocols$fn__8260/invoke at protocols.clj:74
  clojure.core.protocols$fn__8207$G__8202__8220/invoke at protocols.clj:13
  clojure.core$reduce/invokeStatic at core.clj:6969
  clojure.core$reduce/invoke at core.clj:6951
  com.biffweb.graph.impl.query$resolve_entities/invokeStatic at query.clj:109
  com.biffweb.graph.impl.query$resolve_entities/invoke at query.clj:108
  com.biffweb.graph.impl.query$query/invokeStatic at query.clj:195
  com.biffweb.graph.impl.query$query/invoke at query.clj:167
  com.biffweb.graph.impl.query$query/invokeStatic at query.clj:169
  com.biffweb.graph.impl.query$query/invoke at query.clj:167
  com.biffweb.graph$query/invokeStatic at graph.clj:61
  com.biffweb.graph$query/invoke at graph.clj:59
  com.biffweb.graph.error_example.G__12987$eval12992/invokeStatic at NO_SOURCE_FILE:0
  com.biffweb.graph.error_example.G__12987$eval12992/invoke at NO_SOURCE_FILE:-1
  clojure.lang.Compiler/eval at Compiler.java:7757
  clojure.lang.Compiler/eval at Compiler.java:7712
  clojure.core$eval/invokeStatic at core.clj:3236
  clojure.core$eval/invoke at core.clj:3232
>>> error >>>
```
</details>

## Resolver Returns Invalid Typed Data

```clojure
(require '[com.biffweb.graph :as graph])

(def ctx
  (graph/new-ctx
   [(graph/resolver
     {:id :example/id
      :output [:biff.graph/id]
      :resolve-fn (fn [_ctx _input]
                    {:biff.graph/id "not-a-keyword"})})]))

(graph/query ctx [:biff.graph/id])
```

```
ERROR LOG com.biffweb.graph :biff.graph/error-example Resolver Returns Invalid Typed Data

<<< error <<<
Root: java.lang.AssertionError - `:biff.graph/id "not-a-keyword"` is invalid:
  should be a qualified keyword
{:biff.graph/id :example/id}
```

<details>
<summary>Root stack trace</summary>

```
Root stack trace:
  com.biffweb.core.impl.validation$assertion_error/invokeStatic at validation.clj:19
  com.biffweb.core.impl.validation$assertion_error/doInvoke at validation.clj:18
  clojure.lang.RestFn/invoke at RestFn.java:554
  com.biffweb.core.impl.validation$validate_map/invokeStatic at validation.clj:58
  com.biffweb.core.impl.validation$validate_map/invoke at validation.clj:45
  com.biffweb.core.impl.validation$validate_STAR_/invokeStatic at validation.clj:72
  com.biffweb.core.impl.validation$validate_STAR_/doInvoke at validation.clj:61
  clojure.lang.RestFn/invoke at RestFn.java:426
  com.biffweb.graph.impl.ctx$wrap_validate_output$fn__12701/invoke at ctx.clj:65
  com.biffweb.graph.impl.ctx$wrap_cache$fn__12715/invoke at ctx.clj:109
  com.biffweb.graph.impl.query$resolve_attr$resolve_fn__12600/invoke at query.clj:52
  com.biffweb.graph.impl.query$resolve_attr$fn__12602$fn__12603/invoke at query.clj:58
  clojure.core$mapv$fn__8569/invoke at core.clj:7063
  clojure.lang.PersistentVector/reduce at PersistentVector.java:418
  clojure.core$reduce/invokeStatic at core.clj:6968
  clojure.core$mapv/invokeStatic at core.clj:7054
  clojure.core$mapv/invoke at core.clj:7054
  com.biffweb.graph.impl.query$resolve_attr$fn__12602/invoke at query.clj:58
  com.biffweb.graph.impl.query$resolve_attr$fn__12609/invoke at query.clj:72
  com.biffweb.graph.impl.query$apply_indexed/invokeStatic at query.clj:10
  com.biffweb.graph.impl.query$apply_indexed/invoke at query.clj:7
  com.biffweb.graph.impl.query$resolve_attr/invokeStatic at query.clj:72
  com.biffweb.graph.impl.query$resolve_attr/invoke at query.clj:25
  com.biffweb.graph.impl.query$resolve_entities$fn__12633$fn__12639/invoke at query.clj:127
  com.biffweb.graph.impl.query$apply_indexed/invokeStatic at query.clj:10
  com.biffweb.graph.impl.query$apply_indexed/invoke at query.clj:7
  com.biffweb.graph.impl.query$resolve_entities$fn__12633/invoke at query.clj:127
  clojure.core.protocols$iterator_reduce_BANG_/invokeStatic at protocols.clj:42
  clojure.core.protocols$iter_reduce/invokeStatic at protocols.clj:52
  clojure.core.protocols$fn__8260/invokeStatic at protocols.clj:74
  clojure.core.protocols$fn__8260/invoke at protocols.clj:74
  clojure.core.protocols$fn__8207$G__8202__8220/invoke at protocols.clj:13
  clojure.core$reduce/invokeStatic at core.clj:6969
  clojure.core$reduce/invoke at core.clj:6951
  com.biffweb.graph.impl.query$resolve_entities/invokeStatic at query.clj:109
  com.biffweb.graph.impl.query$resolve_entities/invoke at query.clj:108
  com.biffweb.graph.impl.query$query/invokeStatic at query.clj:195
  com.biffweb.graph.impl.query$query/invoke at query.clj:167
  com.biffweb.graph.impl.query$query/invokeStatic at query.clj:169
  com.biffweb.graph.impl.query$query/invoke at query.clj:167
  com.biffweb.graph$query/invokeStatic at graph.clj:61
  com.biffweb.graph$query/invoke at graph.clj:59
  com.biffweb.graph.error_example.G__12994$eval12999/invokeStatic at NO_SOURCE_FILE:0
  com.biffweb.graph.error_example.G__12994$eval12999/invoke at NO_SOURCE_FILE:-1
  clojure.lang.Compiler/eval at Compiler.java:7757
  clojure.lang.Compiler/eval at Compiler.java:7712
  clojure.core$eval/invokeStatic at core.clj:3236
  clojure.core$eval/invoke at core.clj:3232
>>> error >>>
```
</details>

## Conflicting Attribute Shapes In Ctx

```clojure
(require '[com.biffweb.graph :as graph])

(graph/new-ctx
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
ERROR LOG com.biffweb.graph :biff.graph/error-example Conflicting Attribute Shapes In Ctx

<<< error <<<
Root: java.lang.AssertionError - Assert failed: Got conflicting attr shapes
  for `:x`: {:kind :scalar} (from :example/scalar-x), {:kind :join} (from
  :example/join-x)
(= shape expected-shape)
```

<details>
<summary>Root stack trace</summary>

```
Root stack trace:
  com.biffweb.graph.impl.validation$validate_query/invokeStatic at validation.clj:14
  com.biffweb.graph.impl.validation$validate_query/invoke at validation.clj:6
  com.biffweb.graph.impl.ctx$new_ctx/invokeStatic at ctx.clj:135
  com.biffweb.graph.impl.ctx$new_ctx/doInvoke at ctx.clj:121
  clojure.lang.RestFn/invoke at RestFn.java:426
  com.biffweb.graph$new_ctx/invokeStatic at graph.clj:57
  com.biffweb.graph$new_ctx/doInvoke at graph.clj:56
  clojure.lang.RestFn/invoke at RestFn.java:413
  com.biffweb.graph.error_example.G__13001$eval13004/invokeStatic at NO_SOURCE_FILE:0
  com.biffweb.graph.error_example.G__13001$eval13004/invoke at NO_SOURCE_FILE:-1
  clojure.lang.Compiler/eval at Compiler.java:7757
  clojure.lang.Compiler/eval at Compiler.java:7712
  clojure.core$eval/invokeStatic at core.clj:3236
  clojure.core$eval/invoke at core.clj:3232
>>> error >>>
```
</details>

## Missing Resolver Keys

```clojure
(require '[com.biffweb.graph :as graph])

(graph/new-ctx
 [{:biff.graph/id :example/bad
   :biff.graph/resolve-fn (fn [_ctx] {})}])
```

```
ERROR LOG com.biffweb.graph :biff.graph/error-example Missing Resolver Keys

<<< error <<<
Root: java.lang.AssertionError - Missing required keys: :biff.graph/input-ast,
  :biff.graph/output-ast
{:biff.graph/id :example/bad}
```

<details>
<summary>Root stack trace</summary>

```
Root stack trace:
  com.biffweb.core.impl.validation$assertion_error/invokeStatic at validation.clj:19
  com.biffweb.core.impl.validation$assertion_error/doInvoke at validation.clj:18
  clojure.lang.RestFn/invoke at RestFn.java:485
  com.biffweb.core.impl.validation$validate_map/invokeStatic at validation.clj:49
  com.biffweb.core.impl.validation$validate_map/invoke at validation.clj:45
  com.biffweb.core.impl.validation$validate_STAR_/invokeStatic at validation.clj:72
  com.biffweb.core.impl.validation$validate_STAR_/doInvoke at validation.clj:61
  clojure.lang.RestFn/invoke at RestFn.java:426
  com.biffweb.graph.impl.validation$validate_resolver/invokeStatic at validation.clj:20
  com.biffweb.graph.impl.validation$validate_resolver/invoke at validation.clj:19
  clojure.core$run_BANG_$fn__8926/invoke at core.clj:7911
  clojure.lang.PersistentVector/reduce at PersistentVector.java:418
  clojure.core$reduce/invokeStatic at core.clj:6968
  clojure.core$run_BANG_/invokeStatic at core.clj:7906
  clojure.core$run_BANG_/invoke at core.clj:7906
  com.biffweb.graph.impl.ctx$new_ctx/invokeStatic at ctx.clj:122
  com.biffweb.graph.impl.ctx$new_ctx/doInvoke at ctx.clj:121
  clojure.lang.RestFn/invoke at RestFn.java:426
  com.biffweb.graph$new_ctx/invokeStatic at graph.clj:57
  com.biffweb.graph$new_ctx/doInvoke at graph.clj:56
  clojure.lang.RestFn/invoke at RestFn.java:413
  com.biffweb.graph.error_example.G__13010$eval13013/invokeStatic at NO_SOURCE_FILE:0
  com.biffweb.graph.error_example.G__13010$eval13013/invoke at NO_SOURCE_FILE:-1
  clojure.lang.Compiler/eval at Compiler.java:7757
  clojure.lang.Compiler/eval at Compiler.java:7712
  clojure.core$eval/invokeStatic at core.clj:3236
  clojure.core$eval/invoke at core.clj:3232
>>> error >>>
```
</details>

## Invalid Sequential Query Input

```clojure
(require '[com.biffweb.graph :as graph])

(def ctx
  (graph/new-ctx
   [(graph/resolver
     {:id :example/x
      :output [{:x [:y]}]
      :resolve-fn (fn [_ctx _input]
                    {:x [{:y 1}]})})]))

(graph/query ctx '({}) [{:x [:y]}])
```

```
No exception thrown.
```

## Conflicting Query Input Shape

```clojure
(require '[com.biffweb.graph :as graph])

(def ctx
  (graph/new-ctx
   [(graph/resolver
     {:id :example/x
      :output [{:x [:y]} :z]
      :resolve-fn (fn [_ctx _input] {})})]))

(graph/query ctx {:x 1} [:z])
```

```
ERROR LOG com.biffweb.graph :biff.graph/error-example Conflicting Query Input Shape

<<< error <<<
Root: java.lang.AssertionError - Assert failed: Input attr :x is a join but
  value is a scalar
(join-value? value)
```

<details>
<summary>Root stack trace</summary>

```
Root stack trace:
  com.biffweb.graph.impl.validation$validate_input_value/invokeStatic at validation.clj:37
  com.biffweb.graph.impl.validation$validate_input_value/invoke at validation.clj:34
  com.biffweb.graph.impl.validation$validate_input$visit__12553/invoke at validation.clj:46
  clojure.core$run_BANG_$fn__8926/invoke at core.clj:7911
  clojure.lang.PersistentVector/reduce at PersistentVector.java:418
  clojure.core$reduce/invokeStatic at core.clj:6968
  clojure.core$run_BANG_/invokeStatic at core.clj:7906
  clojure.core$run_BANG_/invoke at core.clj:7906
  com.biffweb.graph.impl.validation$validate_input/invokeStatic at validation.clj:53
  com.biffweb.graph.impl.validation$validate_input/invoke at validation.clj:42
  com.biffweb.graph.impl.query$query/invokeStatic at query.clj:192
  com.biffweb.graph.impl.query$query/invoke at query.clj:167
  com.biffweb.graph$query/invokeStatic at graph.clj:63
  com.biffweb.graph$query/invoke at graph.clj:59
  com.biffweb.graph.error_example.G__13024$eval13029/invokeStatic at NO_SOURCE_FILE:0
  com.biffweb.graph.error_example.G__13024$eval13029/invoke at NO_SOURCE_FILE:-1
  clojure.lang.Compiler/eval at Compiler.java:7757
  clojure.lang.Compiler/eval at Compiler.java:7712
  clojure.core$eval/invokeStatic at core.clj:3236
  clojure.core$eval/invoke at core.clj:3232
>>> error >>>
```
</details>

## Resolver Throws Exception

```clojure
(require '[com.biffweb.graph :as graph])

(def ctx
  (graph/new-ctx
   [(graph/resolver
     {:id :example/a
      :output [:a]
      :resolve-fn (fn [_ctx _input]
                    (throw (ex-info "Boom" {:detail 1})))})]))

(graph/query ctx [:a])
```

```
ERROR LOG com.biffweb.graph :biff.graph/error-example Resolver Throws Exception

<<< error <<<
Root: clojure.lang.ExceptionInfo - Boom
data: {:detail 1}

Caused: clojure.lang.ExceptionInfo - Resolver :example/a threw an exception
data: #:biff.graph{:trace [{:resolving :query, :path [:a]} {:resolving :example/a}], :input {}}
```

<details>
<summary>Root stack trace</summary>

```
Root stack trace:
  com.biffweb.graph.error_example.G__13031$fn__13034/invokeStatic at NO_SOURCE_FILE:0
  com.biffweb.graph.error_example.G__13031$fn__13034/invoke at NO_SOURCE_FILE:0
  com.biffweb.graph.impl.resolver$wrap_input$fn__12913/invoke at resolver.clj:10
  com.biffweb.graph.impl.ctx$wrap_exception$fn__12672/invoke at ctx.clj:12
  com.biffweb.graph.impl.ctx$wrap_select_output$resolve_fn__12696/invoke at ctx.clj:57
  com.biffweb.graph.impl.ctx$wrap_validate_output$fn__12701/invoke at ctx.clj:65
  com.biffweb.graph.impl.ctx$wrap_cache$fn__12715/invoke at ctx.clj:109
  com.biffweb.graph.impl.query$resolve_attr$resolve_fn__12600/invoke at query.clj:52
  com.biffweb.graph.impl.query$resolve_attr$fn__12602$fn__12603/invoke at query.clj:58
  clojure.core$mapv$fn__8569/invoke at core.clj:7063
  clojure.lang.PersistentVector/reduce at PersistentVector.java:418
  clojure.core$reduce/invokeStatic at core.clj:6968
  clojure.core$mapv/invokeStatic at core.clj:7054
  clojure.core$mapv/invoke at core.clj:7054
  com.biffweb.graph.impl.query$resolve_attr$fn__12602/invoke at query.clj:58
  com.biffweb.graph.impl.query$resolve_attr$fn__12609/invoke at query.clj:72
  com.biffweb.graph.impl.query$apply_indexed/invokeStatic at query.clj:10
  com.biffweb.graph.impl.query$apply_indexed/invoke at query.clj:7
  com.biffweb.graph.impl.query$resolve_attr/invokeStatic at query.clj:72
  com.biffweb.graph.impl.query$resolve_attr/invoke at query.clj:25
  com.biffweb.graph.impl.query$resolve_entities$fn__12633$fn__12639/invoke at query.clj:127
  com.biffweb.graph.impl.query$apply_indexed/invokeStatic at query.clj:10
  com.biffweb.graph.impl.query$apply_indexed/invoke at query.clj:7
  com.biffweb.graph.impl.query$resolve_entities$fn__12633/invoke at query.clj:127
  clojure.core.protocols$iterator_reduce_BANG_/invokeStatic at protocols.clj:42
  clojure.core.protocols$iter_reduce/invokeStatic at protocols.clj:52
  clojure.core.protocols$fn__8260/invokeStatic at protocols.clj:74
  clojure.core.protocols$fn__8260/invoke at protocols.clj:74
  clojure.core.protocols$fn__8207$G__8202__8220/invoke at protocols.clj:13
  clojure.core$reduce/invokeStatic at core.clj:6969
  clojure.core$reduce/invoke at core.clj:6951
  com.biffweb.graph.impl.query$resolve_entities/invokeStatic at query.clj:109
  com.biffweb.graph.impl.query$resolve_entities/invoke at query.clj:108
  com.biffweb.graph.impl.query$query/invokeStatic at query.clj:195
  com.biffweb.graph.impl.query$query/invoke at query.clj:167
  com.biffweb.graph.impl.query$query/invokeStatic at query.clj:169
  com.biffweb.graph.impl.query$query/invoke at query.clj:167
  com.biffweb.graph$query/invokeStatic at graph.clj:61
  com.biffweb.graph$query/invoke at graph.clj:59
  com.biffweb.graph.error_example.G__13031$eval13036/invokeStatic at NO_SOURCE_FILE:0
  com.biffweb.graph.error_example.G__13031$eval13036/invoke at NO_SOURCE_FILE:-1
  clojure.lang.Compiler/eval at Compiler.java:7757
  clojure.lang.Compiler/eval at Compiler.java:7712
  clojure.core$eval/invokeStatic at core.clj:3236
  clojure.core$eval/invoke at core.clj:3232
>>> error >>>
```
</details>

## Invalid get-ctx

```clojure
(require '[com.biffweb.graph :as graph])

(graph/query {:biff.graph/attr->resolvers {}
              :biff.graph/attr->shape-info {}
              :biff.graph/get-ctx :not-a-function}
             [])
```

```
ERROR LOG com.biffweb.graph :biff.graph/error-example Invalid get-ctx

<<< error <<<
Root: clojure.lang.ArityException - Wrong number of args (0) passed to:
  :not-a-function
```

<details>
<summary>Root stack trace</summary>

```
Root stack trace:
  clojure.lang.Keyword/throwArity at Keyword.java:108
  clojure.lang.Keyword/invoke at Keyword.java:120
  com.biffweb.graph.impl.query$query/invokeStatic at query.clj:177
  com.biffweb.graph.impl.query$query/invoke at query.clj:167
  com.biffweb.graph.impl.query$query/invokeStatic at query.clj:169
  com.biffweb.graph.impl.query$query/invoke at query.clj:167
  com.biffweb.graph$query/invokeStatic at graph.clj:61
  com.biffweb.graph$query/invoke at graph.clj:59
  com.biffweb.graph.error_example.G__13038$eval13041/invokeStatic at NO_SOURCE_FILE:0
  com.biffweb.graph.error_example.G__13038$eval13041/invoke at NO_SOURCE_FILE:-1
  clojure.lang.Compiler/eval at Compiler.java:7757
  clojure.lang.Compiler/eval at Compiler.java:7712
  clojure.core$eval/invokeStatic at core.clj:3236
  clojure.core$eval/invoke at core.clj:3232
>>> error >>>
```
</details>

## Invalid Resolver Map In Ctx

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
ERROR LOG com.biffweb.graph :biff.graph/error-example Invalid Resolver Map In Ctx

<<< error <<<
Root: java.lang.AssertionError - `:biff.graph/attr->resolvers {:x
  [#:biff.graph{:id :example/bad, :output-ast {…` is invalid: {:x
  [#:biff.graph{:input-ast ["missing required key"]}]}
```

<details>
<summary>Root stack trace</summary>

```
Root stack trace:
  com.biffweb.core.impl.validation$assertion_error/invokeStatic at validation.clj:19
  com.biffweb.core.impl.validation$assertion_error/doInvoke at validation.clj:18
  clojure.lang.RestFn/invoke at RestFn.java:554
  com.biffweb.core.impl.validation$validate_map/invokeStatic at validation.clj:58
  com.biffweb.core.impl.validation$validate_map/invoke at validation.clj:45
  com.biffweb.core.impl.validation$validate_STAR_/invokeStatic at validation.clj:72
  com.biffweb.core.impl.validation$validate_STAR_/doInvoke at validation.clj:61
  clojure.lang.RestFn/invoke at RestFn.java:426
  com.biffweb.graph.impl.query$query/invokeStatic at query.clj:180
  com.biffweb.graph.impl.query$query/invoke at query.clj:167
  com.biffweb.graph.impl.query$query/invokeStatic at query.clj:169
  com.biffweb.graph.impl.query$query/invoke at query.clj:167
  com.biffweb.graph$query/invokeStatic at graph.clj:61
  com.biffweb.graph$query/invoke at graph.clj:59
  com.biffweb.graph.error_example.G__13043$eval13046/invokeStatic at NO_SOURCE_FILE:0
  com.biffweb.graph.error_example.G__13043$eval13046/invoke at NO_SOURCE_FILE:-1
  clojure.lang.Compiler/eval at Compiler.java:7757
  clojure.lang.Compiler/eval at Compiler.java:7712
  clojure.core$eval/invokeStatic at core.clj:3236
  clojure.core$eval/invoke at core.clj:3232
>>> error >>>
```
</details>

## Conflicting Join Cardinalities

```clojure
(require '[com.biffweb.graph :as graph])

(def ctx
  (graph/new-ctx
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

(graph/query ctx [{:id 1} {:id 2}] [{:x [:y]}])
```

```
ERROR LOG com.biffweb.graph :biff.graph/error-example Conflicting Join Cardinalities

<<< error <<<
Root: java.lang.AssertionError - Assert failed: Got conflicting cardinalities
  for :x. The value should either always be a map or always be a sequence of
  maps.
(or all-maps? all-seqs?)
```

<details>
<summary>Root stack trace</summary>

```
Root stack trace:
  com.biffweb.graph.impl.query$resolve_joins/invokeStatic at query.clj:89
  com.biffweb.graph.impl.query$resolve_joins/invoke at query.clj:84
  com.biffweb.graph.impl.query$resolve_entities$fn__12633$fn__12642/invoke at query.clj:140
  com.biffweb.graph.impl.query$apply_indexed/invokeStatic at query.clj:10
  com.biffweb.graph.impl.query$apply_indexed/invoke at query.clj:7
  com.biffweb.graph.impl.query$resolve_entities$fn__12633/invoke at query.clj:140
  clojure.core.protocols$iterator_reduce_BANG_/invokeStatic at protocols.clj:42
  clojure.core.protocols$iter_reduce/invokeStatic at protocols.clj:52
  clojure.core.protocols$fn__8260/invokeStatic at protocols.clj:74
  clojure.core.protocols$fn__8260/invoke at protocols.clj:74
  clojure.core.protocols$fn__8207$G__8202__8220/invoke at protocols.clj:13
  clojure.core$reduce/invokeStatic at core.clj:6969
  clojure.core$reduce/invoke at core.clj:6951
  com.biffweb.graph.impl.query$resolve_entities/invokeStatic at query.clj:109
  com.biffweb.graph.impl.query$resolve_entities/invoke at query.clj:108
  com.biffweb.graph.impl.query$query/invokeStatic at query.clj:195
  com.biffweb.graph.impl.query$query/invoke at query.clj:167
  com.biffweb.graph$query/invokeStatic at graph.clj:63
  com.biffweb.graph$query/invoke at graph.clj:59
  com.biffweb.graph.error_example.G__13050$eval13061/invokeStatic at NO_SOURCE_FILE:0
  com.biffweb.graph.error_example.G__13050$eval13061/invoke at NO_SOURCE_FILE:-1
  clojure.lang.Compiler/eval at Compiler.java:7757
  clojure.lang.Compiler/eval at Compiler.java:7712
  clojure.core$eval/invokeStatic at core.clj:3236
  clojure.core$eval/invoke at core.clj:3232
>>> error >>>
```
</details>

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
```

<details>
<summary>Root stack trace</summary>

```
Root stack trace:
  com.biffweb.core.impl.validation$assertion_error/invokeStatic at validation.clj:19
  com.biffweb.core.impl.validation$assertion_error/doInvoke at validation.clj:18
  clojure.lang.RestFn/invoke at RestFn.java:554
  com.biffweb.core.impl.validation$validate_map/invokeStatic at validation.clj:58
  com.biffweb.core.impl.validation$validate_map/invoke at validation.clj:45
  com.biffweb.core.impl.validation$validate_STAR_/invokeStatic at validation.clj:72
  com.biffweb.core.impl.validation$validate_STAR_/doInvoke at validation.clj:61
  clojure.lang.RestFn/invoke at RestFn.java:413
  com.biffweb.graph.impl.query$query/invokeStatic at query.clj:171
  com.biffweb.graph.impl.query$query/invoke at query.clj:167
  com.biffweb.graph.impl.query$query/invokeStatic at query.clj:169
  com.biffweb.graph.impl.query$query/invoke at query.clj:167
  com.biffweb.graph$query/invokeStatic at graph.clj:61
  com.biffweb.graph$query/invoke at graph.clj:59
  com.biffweb.graph.error_example.G__13063$eval13066/invokeStatic at NO_SOURCE_FILE:0
  com.biffweb.graph.error_example.G__13063$eval13066/invoke at NO_SOURCE_FILE:-1
  clojure.lang.Compiler/eval at Compiler.java:7757
  clojure.lang.Compiler/eval at Compiler.java:7712
  clojure.core$eval/invokeStatic at core.clj:3236
  clojure.core$eval/invoke at core.clj:3232
>>> error >>>
```
</details>

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
```

<details>
<summary>Root stack trace</summary>

```
Root stack trace:
  com.biffweb.core.impl.validation$assertion_error/invokeStatic at validation.clj:19
  com.biffweb.core.impl.validation$assertion_error/doInvoke at validation.clj:18
  clojure.lang.RestFn/invoke at RestFn.java:554
  com.biffweb.core.impl.validation$validate_map/invokeStatic at validation.clj:58
  com.biffweb.core.impl.validation$validate_map/invoke at validation.clj:45
  com.biffweb.core.impl.validation$validate_STAR_/invokeStatic at validation.clj:72
  com.biffweb.core.impl.validation$validate_STAR_/doInvoke at validation.clj:61
  clojure.lang.RestFn/invoke at RestFn.java:413
  com.biffweb.graph.impl.query$query/invokeStatic at query.clj:171
  com.biffweb.graph.impl.query$query/invoke at query.clj:167
  com.biffweb.graph$query/invokeStatic at graph.clj:63
  com.biffweb.graph$query/invoke at graph.clj:59
  com.biffweb.graph.error_example.G__13068$eval13071/invokeStatic at NO_SOURCE_FILE:0
  com.biffweb.graph.error_example.G__13068$eval13071/invoke at NO_SOURCE_FILE:-1
  clojure.lang.Compiler/eval at Compiler.java:7757
  clojure.lang.Compiler/eval at Compiler.java:7712
  clojure.core$eval/invokeStatic at core.clj:3236
  clojure.core$eval/invoke at core.clj:3232
>>> error >>>
```
</details>

## Missing Ctx

```clojure
(require '[com.biffweb.graph :as graph])

(graph/query {} [:x])
```

```
ERROR LOG com.biffweb.graph :biff.graph/error-example Missing Ctx

<<< error <<<
Root: java.lang.AssertionError - Missing required keys:
  :biff.graph/attr->resolvers, :biff.graph/attr->shape-info
```

<details>
<summary>Root stack trace</summary>

```
Root stack trace:
  com.biffweb.core.impl.validation$assertion_error/invokeStatic at validation.clj:19
  com.biffweb.core.impl.validation$assertion_error/doInvoke at validation.clj:18
  clojure.lang.RestFn/invoke at RestFn.java:485
  com.biffweb.core.impl.validation$validate_map/invokeStatic at validation.clj:49
  com.biffweb.core.impl.validation$validate_map/invoke at validation.clj:45
  com.biffweb.core.impl.validation$validate_STAR_/invokeStatic at validation.clj:72
  com.biffweb.core.impl.validation$validate_STAR_/doInvoke at validation.clj:61
  clojure.lang.RestFn/invoke at RestFn.java:426
  com.biffweb.graph.impl.query$query/invokeStatic at query.clj:180
  com.biffweb.graph.impl.query$query/invoke at query.clj:167
  com.biffweb.graph.impl.query$query/invokeStatic at query.clj:169
  com.biffweb.graph.impl.query$query/invoke at query.clj:167
  com.biffweb.graph$query/invokeStatic at graph.clj:61
  com.biffweb.graph$query/invoke at graph.clj:59
  com.biffweb.graph.error_example.G__13073$eval13076/invokeStatic at NO_SOURCE_FILE:0
  com.biffweb.graph.error_example.G__13073$eval13076/invoke at NO_SOURCE_FILE:-1
  clojure.lang.Compiler/eval at Compiler.java:7757
  clojure.lang.Compiler/eval at Compiler.java:7712
  clojure.core$eval/invokeStatic at core.clj:3236
  clojure.core$eval/invoke at core.clj:3232
>>> error >>>
```
</details>

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
```

<details>
<summary>Root stack trace</summary>

```
Root stack trace:
  com.biffweb.graph.impl.validation$validate_query/invokeStatic at validation.clj:14
  com.biffweb.graph.impl.validation$validate_query/invoke at validation.clj:6
  com.biffweb.graph.impl.query$query/invokeStatic at query.clj:186
  com.biffweb.graph.impl.query$query/invoke at query.clj:167
  com.biffweb.graph.impl.query$query/invokeStatic at query.clj:169
  com.biffweb.graph.impl.query$query/invoke at query.clj:167
  com.biffweb.graph$query/invokeStatic at graph.clj:61
  com.biffweb.graph$query/invoke at graph.clj:59
  com.biffweb.graph.error_example.G__13078$eval13081/invokeStatic at NO_SOURCE_FILE:0
  com.biffweb.graph.error_example.G__13078$eval13081/invoke at NO_SOURCE_FILE:-1
  clojure.lang.Compiler/eval at Compiler.java:7757
  clojure.lang.Compiler/eval at Compiler.java:7712
  clojure.core$eval/invokeStatic at core.clj:3236
  clojure.core$eval/invoke at core.clj:3232
>>> error >>>
```
</details>

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
Root: clojure.lang.ExceptionInfo - Could not resolve :x
data: #:biff.graph{:trace [{:resolving :query, :path [:x]}]}
```

<details>
<summary>Root stack trace</summary>

```
Root stack trace:
  com.biffweb.graph.impl.query$query/invokeStatic at query.clj:202
  com.biffweb.graph.impl.query$query/invoke at query.clj:167
  com.biffweb.graph.impl.query$query/invokeStatic at query.clj:169
  com.biffweb.graph.impl.query$query/invoke at query.clj:167
  com.biffweb.graph$query/invokeStatic at graph.clj:61
  com.biffweb.graph$query/invoke at graph.clj:59
  com.biffweb.graph.error_example.G__13083$eval13086/invokeStatic at NO_SOURCE_FILE:0
  com.biffweb.graph.error_example.G__13083$eval13086/invoke at NO_SOURCE_FILE:-1
  clojure.lang.Compiler/eval at Compiler.java:7757
  clojure.lang.Compiler/eval at Compiler.java:7712
  clojure.core$eval/invokeStatic at core.clj:3236
  clojure.core$eval/invoke at core.clj:3232
>>> error >>>
```
</details>

## Nested Unresolved Required Attribute

```clojure
(require '[com.biffweb.graph :as graph])

(def ctx
  (graph/new-ctx
   [(graph/resolver
     {:id :example/b
      :output [{:b [:seed]}]
      :resolve-fn (fn [_ctx _input] {})})
    (graph/resolver
     {:id :example/d
      :input [:g]
      :output [{:d [:ok]}]
      :resolve-fn (fn [_ctx _input]
                    {:d {:ok true}})})]))

(graph/query ctx {:b {:seed true}} [{:b [{:d [:ok]}]}])
```

```
ERROR LOG com.biffweb.graph :biff.graph/error-example Nested Unresolved Required Attribute

<<< error <<<
Root: clojure.lang.ExceptionInfo - Could not resolve :g
data: #:biff.graph{:trace [{:resolving :query, :path [:b :d]} {:resolving :example/d, :path [:g]}]}
```

<details>
<summary>Root stack trace</summary>

```
Root stack trace:
  com.biffweb.graph.impl.query$query/invokeStatic at query.clj:202
  com.biffweb.graph.impl.query$query/invoke at query.clj:167
  com.biffweb.graph$query/invokeStatic at graph.clj:63
  com.biffweb.graph$query/invoke at graph.clj:59
  com.biffweb.graph.error_example.G__13088$eval13095/invokeStatic at NO_SOURCE_FILE:0
  com.biffweb.graph.error_example.G__13088$eval13095/invoke at NO_SOURCE_FILE:-1
  clojure.lang.Compiler/eval at Compiler.java:7757
  clojure.lang.Compiler/eval at Compiler.java:7712
  clojure.core$eval/invokeStatic at core.clj:3236
  clojure.core$eval/invoke at core.clj:3232
>>> error >>>
```
</details>
