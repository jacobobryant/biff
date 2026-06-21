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
  com.biffweb.graph.impl$query__GT_ast/invokeStatic at impl.clj:75
  com.biffweb.graph.impl$query__GT_ast/invoke at impl.clj:73
  com.biffweb.graph$query__GT_ast/invokeStatic at graph.clj:42
  com.biffweb.graph$query__GT_ast/invoke at graph.clj:41
  com.biffweb.graph.error_example.G__12449$eval12877/invokeStatic at NO_SOURCE_FILE:0
  com.biffweb.graph.error_example.G__12449$eval12877/invoke at NO_SOURCE_FILE:-1
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
  com.biffweb.graph.impl$validate_resolver/invokeStatic at impl.clj:83
  com.biffweb.graph.impl$validate_resolver/invoke at impl.clj:82
  com.biffweb.graph.impl$resolver/invokeStatic at impl.clj:93
  com.biffweb.graph.impl$resolver/invoke at impl.clj:89
  com.biffweb.graph$resolver/invokeStatic at graph.clj:45
  com.biffweb.graph$resolver/invoke at graph.clj:44
  com.biffweb.graph.error_example.G__12879$eval12882/invokeStatic at NO_SOURCE_FILE:0
  com.biffweb.graph.error_example.G__12879$eval12882/invoke at NO_SOURCE_FILE:-1
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
  com.biffweb.graph.impl$resolver/invokeStatic at impl.clj:90
  com.biffweb.graph.impl$resolver/invoke at impl.clj:89
  com.biffweb.graph$resolver/invokeStatic at graph.clj:45
  com.biffweb.graph$resolver/invoke at graph.clj:44
  com.biffweb.graph.error_example.G__12886$eval12889/invokeStatic at NO_SOURCE_FILE:0
  com.biffweb.graph.error_example.G__12886$eval12889/invoke at NO_SOURCE_FILE:-1
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
  com.biffweb.graph.error_example.G__12893$fn__12896/invokeStatic at NO_SOURCE_FILE:0
  com.biffweb.graph.error_example.G__12893$fn__12896/invoke at NO_SOURCE_FILE:-1
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
(join-value? value)

Root stack trace:
  com.biffweb.graph.impl$select_output_value/invokeStatic at impl.clj:136
  com.biffweb.graph.impl$select_output_value/invoke at impl.clj:131
  com.biffweb.graph.impl$select_output$fn__12611/invoke at impl.clj:158
  clojure.core$keep$fn__8691$fn__8692/invoke at core.clj:7521
  clojure.core.protocols$iterator_reduce_BANG_/invokeStatic at protocols.clj:42
  clojure.core.protocols$iter_reduce/invokeStatic at protocols.clj:52
  clojure.core.protocols$fn__8260/invokeStatic at protocols.clj:74
  clojure.core.protocols$fn__8260/invoke at protocols.clj:74
  clojure.core.protocols$fn__8207$G__8202__8220/invoke at protocols.clj:13
  clojure.core$transduce/invokeStatic at core.clj:7030
  clojure.core$into/invokeStatic at core.clj:7046
  clojure.core$into/invoke at core.clj:7033
  com.biffweb.graph.impl$select_output/invokeStatic at impl.clj:155
  com.biffweb.graph.impl$select_output/invoke at impl.clj:152
  com.biffweb.graph.impl$wrap_select_output$resolve_fn__12619/invoke at impl.clj:167
  com.biffweb.graph.impl$wrap_validate_output$fn__12624/invoke at impl.clj:174
  com.biffweb.graph.impl$wrap_cache$fn__12638/invoke at impl.clj:218
  com.biffweb.graph.impl$resolve_attr$resolve_fn_SINGLEQUOTE___12779/invoke at impl.clj:310
  com.biffweb.graph.impl$resolve_attr$fn__12781/invoke at impl.clj:314
  clojure.core$mapv$fn__8569/invoke at core.clj:7063
  clojure.lang.PersistentVector/reduce at PersistentVector.java:418
  clojure.core$reduce/invokeStatic at core.clj:6968
  clojure.core$mapv/invokeStatic at core.clj:7054
  clojure.core$mapv/invoke at core.clj:7054
  com.biffweb.graph.impl$resolve_attr/invokeStatic at impl.clj:314
  com.biffweb.graph.impl$resolve_attr/invoke at impl.clj:277
  com.biffweb.graph.impl$resolve_entities$fn__12830/invoke at impl.clj:380
  clojure.core.protocols$iterator_reduce_BANG_/invokeStatic at protocols.clj:42
  clojure.core.protocols$iter_reduce/invokeStatic at protocols.clj:52
  clojure.core.protocols$fn__8260/invokeStatic at protocols.clj:74
  clojure.core.protocols$fn__8260/invoke at protocols.clj:74
  clojure.core.protocols$fn__8207$G__8202__8220/invoke at protocols.clj:13
  clojure.core$reduce/invokeStatic at core.clj:6969
  clojure.core$reduce/invoke at core.clj:6951
  com.biffweb.graph.impl$resolve_entities/invokeStatic at impl.clj:379
  com.biffweb.graph.impl$resolve_entities/invoke at impl.clj:378
  com.biffweb.graph.impl$query/invokeStatic at impl.clj:427
  com.biffweb.graph.impl$query/invoke at impl.clj:405
  com.biffweb.graph.impl$query/invokeStatic at impl.clj:407
  com.biffweb.graph.impl$query/invoke at impl.clj:405
  com.biffweb.graph$query/invokeStatic at graph.clj:55
  com.biffweb.graph$query/invoke at graph.clj:53
  com.biffweb.graph.error_example.G__12901$eval12906/invokeStatic at NO_SOURCE_FILE:0
  com.biffweb.graph.error_example.G__12901$eval12906/invoke at NO_SOURCE_FILE:-1
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
(scalar-value? value)

Root stack trace:
  com.biffweb.graph.impl$select_output_value/invokeStatic at impl.clj:139
  com.biffweb.graph.impl$select_output_value/invoke at impl.clj:131
  com.biffweb.graph.impl$select_output$fn__12611/invoke at impl.clj:158
  clojure.core$keep$fn__8691$fn__8692/invoke at core.clj:7521
  clojure.core.protocols$iterator_reduce_BANG_/invokeStatic at protocols.clj:42
  clojure.core.protocols$iter_reduce/invokeStatic at protocols.clj:52
  clojure.core.protocols$fn__8260/invokeStatic at protocols.clj:74
  clojure.core.protocols$fn__8260/invoke at protocols.clj:74
  clojure.core.protocols$fn__8207$G__8202__8220/invoke at protocols.clj:13
  clojure.core$transduce/invokeStatic at core.clj:7030
  clojure.core$into/invokeStatic at core.clj:7046
  clojure.core$into/invoke at core.clj:7033
  com.biffweb.graph.impl$select_output/invokeStatic at impl.clj:155
  com.biffweb.graph.impl$select_output/invoke at impl.clj:152
  com.biffweb.graph.impl$wrap_select_output$resolve_fn__12619/invoke at impl.clj:167
  com.biffweb.graph.impl$wrap_validate_output$fn__12624/invoke at impl.clj:174
  com.biffweb.graph.impl$wrap_cache$fn__12638/invoke at impl.clj:218
  com.biffweb.graph.impl$resolve_attr$resolve_fn_SINGLEQUOTE___12779/invoke at impl.clj:310
  com.biffweb.graph.impl$resolve_attr$fn__12781/invoke at impl.clj:314
  clojure.core$mapv$fn__8569/invoke at core.clj:7063
  clojure.lang.PersistentVector/reduce at PersistentVector.java:418
  clojure.core$reduce/invokeStatic at core.clj:6968
  clojure.core$mapv/invokeStatic at core.clj:7054
  clojure.core$mapv/invoke at core.clj:7054
  com.biffweb.graph.impl$resolve_attr/invokeStatic at impl.clj:314
  com.biffweb.graph.impl$resolve_attr/invoke at impl.clj:277
  com.biffweb.graph.impl$resolve_entities$fn__12830/invoke at impl.clj:380
  clojure.core.protocols$iterator_reduce_BANG_/invokeStatic at protocols.clj:42
  clojure.core.protocols$iter_reduce/invokeStatic at protocols.clj:52
  clojure.core.protocols$fn__8260/invokeStatic at protocols.clj:74
  clojure.core.protocols$fn__8260/invoke at protocols.clj:74
  clojure.core.protocols$fn__8207$G__8202__8220/invoke at protocols.clj:13
  clojure.core$reduce/invokeStatic at core.clj:6969
  clojure.core$reduce/invoke at core.clj:6951
  com.biffweb.graph.impl$resolve_entities/invokeStatic at impl.clj:379
  com.biffweb.graph.impl$resolve_entities/invoke at impl.clj:378
  com.biffweb.graph.impl$query/invokeStatic at impl.clj:427
  com.biffweb.graph.impl$query/invoke at impl.clj:405
  com.biffweb.graph.impl$query/invokeStatic at impl.clj:407
  com.biffweb.graph.impl$query/invoke at impl.clj:405
  com.biffweb.graph$query/invokeStatic at graph.clj:55
  com.biffweb.graph$query/invoke at graph.clj:53
  com.biffweb.graph.error_example.G__12908$eval12913/invokeStatic at NO_SOURCE_FILE:0
  com.biffweb.graph.error_example.G__12908$eval12913/invoke at NO_SOURCE_FILE:-1
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
  com.biffweb.graph.impl$wrap_validate_output$fn__12624/invoke at impl.clj:174
  com.biffweb.graph.impl$wrap_cache$fn__12638/invoke at impl.clj:218
  com.biffweb.graph.impl$resolve_attr$resolve_fn_SINGLEQUOTE___12779/invoke at impl.clj:310
  com.biffweb.graph.impl$resolve_attr$fn__12781/invoke at impl.clj:314
  clojure.core$mapv$fn__8569/invoke at core.clj:7063
  clojure.lang.PersistentVector/reduce at PersistentVector.java:418
  clojure.core$reduce/invokeStatic at core.clj:6968
  clojure.core$mapv/invokeStatic at core.clj:7054
  clojure.core$mapv/invoke at core.clj:7054
  com.biffweb.graph.impl$resolve_attr/invokeStatic at impl.clj:314
  com.biffweb.graph.impl$resolve_attr/invoke at impl.clj:277
  com.biffweb.graph.impl$resolve_entities$fn__12830/invoke at impl.clj:380
  clojure.core.protocols$iterator_reduce_BANG_/invokeStatic at protocols.clj:42
  clojure.core.protocols$iter_reduce/invokeStatic at protocols.clj:52
  clojure.core.protocols$fn__8260/invokeStatic at protocols.clj:74
  clojure.core.protocols$fn__8260/invoke at protocols.clj:74
  clojure.core.protocols$fn__8207$G__8202__8220/invoke at protocols.clj:13
  clojure.core$reduce/invokeStatic at core.clj:6969
  clojure.core$reduce/invoke at core.clj:6951
  com.biffweb.graph.impl$resolve_entities/invokeStatic at impl.clj:379
  com.biffweb.graph.impl$resolve_entities/invoke at impl.clj:378
  com.biffweb.graph.impl$query/invokeStatic at impl.clj:427
  com.biffweb.graph.impl$query/invoke at impl.clj:405
  com.biffweb.graph.impl$query/invokeStatic at impl.clj:407
  com.biffweb.graph.impl$query/invoke at impl.clj:405
  com.biffweb.graph$query/invokeStatic at graph.clj:55
  com.biffweb.graph$query/invoke at graph.clj:53
  com.biffweb.graph.error_example.G__12915$eval12920/invokeStatic at NO_SOURCE_FILE:0
  com.biffweb.graph.error_example.G__12915$eval12920/invoke at NO_SOURCE_FILE:-1
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
  for :x: {:kind :scalar} (from :example/scalar-x), {:kind :join} (from
  :example/join-x)
(= shape expected-shape)

Root stack trace:
  com.biffweb.graph.impl$validate_query/invokeStatic at impl.clj:241
  com.biffweb.graph.impl$validate_query/invoke at impl.clj:236
  com.biffweb.graph.impl$new_env/invokeStatic at impl.clj:259
  com.biffweb.graph.impl$new_env/doInvoke at impl.clj:246
  clojure.lang.RestFn/invoke at RestFn.java:426
  com.biffweb.graph$new_env/invokeStatic at graph.clj:51
  com.biffweb.graph$new_env/doInvoke at graph.clj:50
  clojure.lang.RestFn/invoke at RestFn.java:413
  com.biffweb.graph.error_example.G__12922$eval12925/invokeStatic at NO_SOURCE_FILE:0
  com.biffweb.graph.error_example.G__12922$eval12925/invoke at NO_SOURCE_FILE:-1
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
  com.biffweb.graph.impl$validate_resolver/invokeStatic at impl.clj:83
  com.biffweb.graph.impl$validate_resolver/invoke at impl.clj:82
  clojure.core$run_BANG_$fn__8926/invoke at core.clj:7911
  clojure.lang.PersistentVector/reduce at PersistentVector.java:418
  clojure.core$reduce/invokeStatic at core.clj:6968
  clojure.core$run_BANG_/invokeStatic at core.clj:7906
  clojure.core$run_BANG_/invoke at core.clj:7906
  com.biffweb.graph.impl$new_env/invokeStatic at impl.clj:247
  com.biffweb.graph.impl$new_env/doInvoke at impl.clj:246
  clojure.lang.RestFn/invoke at RestFn.java:426
  com.biffweb.graph$new_env/invokeStatic at graph.clj:51
  com.biffweb.graph$new_env/doInvoke at graph.clj:50
  clojure.lang.RestFn/invoke at RestFn.java:413
  com.biffweb.graph.error_example.G__12931$eval12934/invokeStatic at NO_SOURCE_FILE:0
  com.biffweb.graph.error_example.G__12931$eval12934/invoke at NO_SOURCE_FILE:-1
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
ERROR LOG com.biffweb.graph :biff.graph/error-example Invalid Sequential Query Input

<<< error <<<
Root: java.lang.AssertionError - Assert failed: (vector? entities)

Root stack trace:
  com.biffweb.graph.impl$resolve_attr/invokeStatic at impl.clj:279
  com.biffweb.graph.impl$resolve_attr/invoke at impl.clj:277
  com.biffweb.graph.impl$resolve_entities$fn__12830/invoke at impl.clj:380
  clojure.core.protocols$iterator_reduce_BANG_/invokeStatic at protocols.clj:42
  clojure.core.protocols$iter_reduce/invokeStatic at protocols.clj:52
  clojure.core.protocols$fn__8260/invokeStatic at protocols.clj:74
  clojure.core.protocols$fn__8260/invoke at protocols.clj:74
  clojure.core.protocols$fn__8207$G__8202__8220/invoke at protocols.clj:13
  clojure.core$reduce/invokeStatic at core.clj:6969
  clojure.core$reduce/invoke at core.clj:6951
  com.biffweb.graph.impl$resolve_entities/invokeStatic at impl.clj:379
  com.biffweb.graph.impl$resolve_entities/invoke at impl.clj:378
  com.biffweb.graph.impl$query/invokeStatic at impl.clj:427
  com.biffweb.graph.impl$query/invoke at impl.clj:405
  com.biffweb.graph$query/invokeStatic at graph.clj:57
  com.biffweb.graph$query/invoke at graph.clj:53
  com.biffweb.graph.error_example.G__12938$eval12943/invokeStatic at NO_SOURCE_FILE:0
  com.biffweb.graph.error_example.G__12938$eval12943/invoke at NO_SOURCE_FILE:-1
  clojure.lang.Compiler/eval at Compiler.java:7757
  clojure.lang.Compiler/eval at Compiler.java:7712
  clojure.core$eval/invokeStatic at core.clj:3236
  clojure.core$eval/invoke at core.clj:3232
>>> error >>>
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
  com.biffweb.graph.impl$validate_input_value/invokeStatic at impl.clj:360
  com.biffweb.graph.impl$validate_input_value/invoke at impl.clj:357
  com.biffweb.graph.impl$validate_input$visit__12814/invoke at impl.clj:369
  clojure.core$run_BANG_$fn__8926/invoke at core.clj:7911
  clojure.lang.PersistentVector/reduce at PersistentVector.java:418
  clojure.core$reduce/invokeStatic at core.clj:6968
  clojure.core$run_BANG_/invokeStatic at core.clj:7906
  clojure.core$run_BANG_/invoke at core.clj:7906
  com.biffweb.graph.impl$validate_input/invokeStatic at impl.clj:376
  com.biffweb.graph.impl$validate_input/invoke at impl.clj:365
  com.biffweb.graph.impl$query/invokeStatic at impl.clj:424
  com.biffweb.graph.impl$query/invoke at impl.clj:405
  com.biffweb.graph$query/invokeStatic at graph.clj:57
  com.biffweb.graph$query/invoke at graph.clj:53
  com.biffweb.graph.error_example.G__12945$eval12950/invokeStatic at NO_SOURCE_FILE:0
  com.biffweb.graph.error_example.G__12945$eval12950/invoke at NO_SOURCE_FILE:-1
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
  com.biffweb.graph.impl$query/invokeStatic at impl.clj:414
  com.biffweb.graph.impl$query/invoke at impl.clj:405
  com.biffweb.graph.impl$query/invokeStatic at impl.clj:407
  com.biffweb.graph.impl$query/invoke at impl.clj:405
  com.biffweb.graph$query/invokeStatic at graph.clj:55
  com.biffweb.graph$query/invoke at graph.clj:53
  com.biffweb.graph.error_example.G__12952$eval12955/invokeStatic at NO_SOURCE_FILE:0
  com.biffweb.graph.error_example.G__12952$eval12955/invoke at NO_SOURCE_FILE:-1
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
  com.biffweb.graph.impl$query/invokeStatic at impl.clj:416
  com.biffweb.graph.impl$query/invoke at impl.clj:405
  com.biffweb.graph.impl$query/invokeStatic at impl.clj:407
  com.biffweb.graph.impl$query/invoke at impl.clj:405
  com.biffweb.graph$query/invokeStatic at graph.clj:55
  com.biffweb.graph$query/invoke at graph.clj:53
  com.biffweb.graph.error_example.G__12957$eval12960/invokeStatic at NO_SOURCE_FILE:0
  com.biffweb.graph.error_example.G__12957$eval12960/invoke at NO_SOURCE_FILE:-1
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
  for :x
(or all-maps? all-seqs?)

Root stack trace:
  com.biffweb.graph.impl$resolve_joins/invokeStatic at impl.clj:338
  com.biffweb.graph.impl$resolve_joins/invoke at impl.clj:333
  com.biffweb.graph.impl$resolve_entities$fn__12830/invoke at impl.clj:387
  clojure.core.protocols$iterator_reduce_BANG_/invokeStatic at protocols.clj:42
  clojure.core.protocols$iter_reduce/invokeStatic at protocols.clj:52
  clojure.core.protocols$fn__8260/invokeStatic at protocols.clj:74
  clojure.core.protocols$fn__8260/invoke at protocols.clj:74
  clojure.core.protocols$fn__8207$G__8202__8220/invoke at protocols.clj:13
  clojure.core$reduce/invokeStatic at core.clj:6969
  clojure.core$reduce/invoke at core.clj:6951
  com.biffweb.graph.impl$resolve_entities/invokeStatic at impl.clj:379
  com.biffweb.graph.impl$resolve_entities/invoke at impl.clj:378
  com.biffweb.graph.impl$query/invokeStatic at impl.clj:427
  com.biffweb.graph.impl$query/invoke at impl.clj:405
  com.biffweb.graph$query/invokeStatic at graph.clj:57
  com.biffweb.graph$query/invoke at graph.clj:53
  com.biffweb.graph.error_example.G__12964$eval12975/invokeStatic at NO_SOURCE_FILE:0
  com.biffweb.graph.error_example.G__12964$eval12975/invoke at NO_SOURCE_FILE:-1
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
  com.biffweb.graph.impl$query/invokeStatic at impl.clj:409
  com.biffweb.graph.impl$query/invoke at impl.clj:405
  com.biffweb.graph.impl$query/invokeStatic at impl.clj:407
  com.biffweb.graph.impl$query/invoke at impl.clj:405
  com.biffweb.graph$query/invokeStatic at graph.clj:55
  com.biffweb.graph$query/invoke at graph.clj:53
  com.biffweb.graph.error_example.G__12977$eval12980/invokeStatic at NO_SOURCE_FILE:0
  com.biffweb.graph.error_example.G__12977$eval12980/invoke at NO_SOURCE_FILE:-1
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
  com.biffweb.graph.impl$query/invokeStatic at impl.clj:409
  com.biffweb.graph.impl$query/invoke at impl.clj:405
  com.biffweb.graph$query/invokeStatic at graph.clj:57
  com.biffweb.graph$query/invoke at graph.clj:53
  com.biffweb.graph.error_example.G__12982$eval12985/invokeStatic at NO_SOURCE_FILE:0
  com.biffweb.graph.error_example.G__12982$eval12985/invoke at NO_SOURCE_FILE:-1
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
  com.biffweb.graph.impl$query/invokeStatic at impl.clj:416
  com.biffweb.graph.impl$query/invoke at impl.clj:405
  com.biffweb.graph.impl$query/invokeStatic at impl.clj:407
  com.biffweb.graph.impl$query/invoke at impl.clj:405
  com.biffweb.graph$query/invokeStatic at graph.clj:55
  com.biffweb.graph$query/invoke at graph.clj:53
  com.biffweb.graph.error_example.G__12987$eval12990/invokeStatic at NO_SOURCE_FILE:0
  com.biffweb.graph.error_example.G__12987$eval12990/invoke at NO_SOURCE_FILE:-1
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
                   :biff.graph/attr-shape {:kind :scalar}}}}
             [{:x [:y]}])
```

```
ERROR LOG com.biffweb.graph :biff.graph/error-example Conflicting Query Shape

<<< error <<<
Root: java.lang.AssertionError - Assert failed: Got conflicting attr shapes
  for :x: {:kind :join} (from query), {:kind :scalar} (from )
(= shape expected-shape)

Root stack trace:
  com.biffweb.graph.impl$validate_query/invokeStatic at impl.clj:241
  com.biffweb.graph.impl$validate_query/invoke at impl.clj:236
  com.biffweb.graph.impl$query/invokeStatic at impl.clj:419
  com.biffweb.graph.impl$query/invoke at impl.clj:405
  com.biffweb.graph.impl$query/invokeStatic at impl.clj:407
  com.biffweb.graph.impl$query/invoke at impl.clj:405
  com.biffweb.graph$query/invokeStatic at graph.clj:55
  com.biffweb.graph$query/invoke at graph.clj:53
  com.biffweb.graph.error_example.G__12992$eval12995/invokeStatic at NO_SOURCE_FILE:0
  com.biffweb.graph.error_example.G__12992$eval12995/invoke at NO_SOURCE_FILE:-1
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
Root: clojure.lang.ExceptionInfo - TODO
data: #:com.biffweb.graph.impl{:unresolved true}

Root stack trace:
  com.biffweb.graph.impl$query/invokeStatic at impl.clj:430
  com.biffweb.graph.impl$query/invoke at impl.clj:405
  com.biffweb.graph.impl$query/invokeStatic at impl.clj:407
  com.biffweb.graph.impl$query/invoke at impl.clj:405
  com.biffweb.graph$query/invokeStatic at graph.clj:55
  com.biffweb.graph$query/invoke at graph.clj:53
  com.biffweb.graph.error_example.G__12997$eval13000/invokeStatic at NO_SOURCE_FILE:0
  com.biffweb.graph.error_example.G__12997$eval13000/invoke at NO_SOURCE_FILE:-1
  clojure.lang.Compiler/eval at Compiler.java:7757
  clojure.lang.Compiler/eval at Compiler.java:7712
  clojure.core$eval/invokeStatic at core.clj:3236
  clojure.core$eval/invoke at core.clj:3232
>>> error >>>
```
