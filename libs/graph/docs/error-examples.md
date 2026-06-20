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
  com.biffweb.core.impl.validation$assertion_error/invokeStatic at validation.clj:18
  com.biffweb.core.impl.validation$assertion_error/doInvoke at validation.clj:17
  clojure.lang.RestFn/invoke at RestFn.java:515
  com.biffweb.core.impl.validation$validate_map/invokeStatic at validation.clj:48
  com.biffweb.core.impl.validation$validate_map/invoke at validation.clj:38
  com.biffweb.core.impl.validation$validate_STAR_/invokeStatic at validation.clj:61
  com.biffweb.core.impl.validation$validate_STAR_/doInvoke at validation.clj:50
  clojure.lang.RestFn/invoke at RestFn.java:413
  com.biffweb.graph.impl$query__GT_ast/invokeStatic at impl.clj:75
  com.biffweb.graph.impl$query__GT_ast/invoke at impl.clj:73
  com.biffweb.graph$query__GT_ast/invokeStatic at graph.clj:35
  com.biffweb.graph$query__GT_ast/invoke at graph.clj:34
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

Root stack trace:
  com.biffweb.core.impl.validation$assertion_error/invokeStatic at validation.clj:18
  com.biffweb.core.impl.validation$assertion_error/doInvoke at validation.clj:17
  clojure.lang.RestFn/invoke at RestFn.java:515
  com.biffweb.core.impl.validation$validate_map/invokeStatic at validation.clj:48
  com.biffweb.core.impl.validation$validate_map/invoke at validation.clj:38
  com.biffweb.core.impl.validation$validate_STAR_/invokeStatic at validation.clj:61
  com.biffweb.core.impl.validation$validate_STAR_/doInvoke at validation.clj:50
  clojure.lang.RestFn/invoke at RestFn.java:413
  com.biffweb.graph.impl$resolver/invokeStatic at impl.clj:83
  com.biffweb.graph.impl$resolver/invoke at impl.clj:82
  com.biffweb.graph$resolver/invokeStatic at graph.clj:38
  com.biffweb.graph$resolver/invoke at graph.clj:37
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
  com.biffweb.core.impl.validation$assertion_error/invokeStatic at validation.clj:18
  com.biffweb.core.impl.validation$assertion_error/doInvoke at validation.clj:17
  clojure.lang.RestFn/invoke at RestFn.java:515
  com.biffweb.core.impl.validation$validate_map/invokeStatic at validation.clj:48
  com.biffweb.core.impl.validation$validate_map/invoke at validation.clj:38
  com.biffweb.core.impl.validation$validate_STAR_/invokeStatic at validation.clj:61
  com.biffweb.core.impl.validation$validate_STAR_/doInvoke at validation.clj:50
  clojure.lang.RestFn/invoke at RestFn.java:413
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
  com.biffweb.graph.impl$select_output_value/invokeStatic at impl.clj:126
  com.biffweb.graph.impl$select_output_value/invoke at impl.clj:121
  com.biffweb.graph.impl$select_output$fn__12602/invoke at impl.clj:148
  clojure.core$keep$fn__8691$fn__8692/invoke at core.clj:7521
  clojure.core.protocols$iterator_reduce_BANG_/invokeStatic at protocols.clj:42
  clojure.core.protocols$iter_reduce/invokeStatic at protocols.clj:52
  clojure.core.protocols$fn__8260/invokeStatic at protocols.clj:74
  clojure.core.protocols$fn__8260/invoke at protocols.clj:74
  clojure.core.protocols$fn__8207$G__8202__8220/invoke at protocols.clj:13
  clojure.core$transduce/invokeStatic at core.clj:7030
  clojure.core$into/invokeStatic at core.clj:7046
  clojure.core$into/invoke at core.clj:7033
  com.biffweb.graph.impl$select_output/invokeStatic at impl.clj:145
  com.biffweb.graph.impl$select_output/invoke at impl.clj:142
  com.biffweb.graph.impl$wrap_select_output$resolve_fn__12610/invoke at impl.clj:157
  com.biffweb.graph.impl$wrap_validate$fn__12613$fn__12614/invoke at impl.clj:163
  com.biffweb.graph.impl$wrap_cache$fn__12629/invoke at impl.clj:206
  com.biffweb.graph.impl$resolve_attr$resolve_fn_SINGLEQUOTE___12712/invoke at impl.clj:291
  com.biffweb.graph.impl$resolve_attr$fn__12714/invoke at impl.clj:295
  clojure.core$mapv$fn__8569/invoke at core.clj:7063
  clojure.lang.PersistentVector/reduce at PersistentVector.java:418
  clojure.core$reduce/invokeStatic at core.clj:6968
  clojure.core$mapv/invokeStatic at core.clj:7054
  clojure.core$mapv/invoke at core.clj:7054
  com.biffweb.graph.impl$resolve_attr/invokeStatic at impl.clj:295
  com.biffweb.graph.impl$resolve_attr/invoke at impl.clj:258
  com.biffweb.graph.impl$resolve_entities$fn__12744/invoke at impl.clj:338
  clojure.core.protocols$iterator_reduce_BANG_/invokeStatic at protocols.clj:42
  clojure.core.protocols$iter_reduce/invokeStatic at protocols.clj:52
  clojure.core.protocols$fn__8260/invokeStatic at protocols.clj:74
  clojure.core.protocols$fn__8260/invoke at protocols.clj:74
  clojure.core.protocols$fn__8207$G__8202__8220/invoke at protocols.clj:13
  clojure.core$reduce/invokeStatic at core.clj:6969
  clojure.core$reduce/invoke at core.clj:6951
  com.biffweb.graph.impl$resolve_entities/invokeStatic at impl.clj:337
  com.biffweb.graph.impl$resolve_entities/invoke at impl.clj:336
  com.biffweb.graph.impl$query/invokeStatic at impl.clj:382
  com.biffweb.graph.impl$query/invoke at impl.clj:363
  com.biffweb.graph.impl$query/invokeStatic at impl.clj:365
  com.biffweb.graph.impl$query/invoke at impl.clj:363
  com.biffweb.graph$query/invokeStatic at graph.clj:48
  com.biffweb.graph$query/invoke at graph.clj:46
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
  com.biffweb.graph.impl$select_output_value/invokeStatic at impl.clj:129
  com.biffweb.graph.impl$select_output_value/invoke at impl.clj:121
  com.biffweb.graph.impl$select_output$fn__12602/invoke at impl.clj:148
  clojure.core$keep$fn__8691$fn__8692/invoke at core.clj:7521
  clojure.core.protocols$iterator_reduce_BANG_/invokeStatic at protocols.clj:42
  clojure.core.protocols$iter_reduce/invokeStatic at protocols.clj:52
  clojure.core.protocols$fn__8260/invokeStatic at protocols.clj:74
  clojure.core.protocols$fn__8260/invoke at protocols.clj:74
  clojure.core.protocols$fn__8207$G__8202__8220/invoke at protocols.clj:13
  clojure.core$transduce/invokeStatic at core.clj:7030
  clojure.core$into/invokeStatic at core.clj:7046
  clojure.core$into/invoke at core.clj:7033
  com.biffweb.graph.impl$select_output/invokeStatic at impl.clj:145
  com.biffweb.graph.impl$select_output/invoke at impl.clj:142
  com.biffweb.graph.impl$wrap_select_output$resolve_fn__12610/invoke at impl.clj:157
  com.biffweb.graph.impl$wrap_validate$fn__12613$fn__12614/invoke at impl.clj:163
  com.biffweb.graph.impl$wrap_cache$fn__12629/invoke at impl.clj:206
  com.biffweb.graph.impl$resolve_attr$resolve_fn_SINGLEQUOTE___12712/invoke at impl.clj:291
  com.biffweb.graph.impl$resolve_attr$fn__12714/invoke at impl.clj:295
  clojure.core$mapv$fn__8569/invoke at core.clj:7063
  clojure.lang.PersistentVector/reduce at PersistentVector.java:418
  clojure.core$reduce/invokeStatic at core.clj:6968
  clojure.core$mapv/invokeStatic at core.clj:7054
  clojure.core$mapv/invoke at core.clj:7054
  com.biffweb.graph.impl$resolve_attr/invokeStatic at impl.clj:295
  com.biffweb.graph.impl$resolve_attr/invoke at impl.clj:258
  com.biffweb.graph.impl$resolve_entities$fn__12744/invoke at impl.clj:338
  clojure.core.protocols$iterator_reduce_BANG_/invokeStatic at protocols.clj:42
  clojure.core.protocols$iter_reduce/invokeStatic at protocols.clj:52
  clojure.core.protocols$fn__8260/invokeStatic at protocols.clj:74
  clojure.core.protocols$fn__8260/invoke at protocols.clj:74
  clojure.core.protocols$fn__8207$G__8202__8220/invoke at protocols.clj:13
  clojure.core$reduce/invokeStatic at core.clj:6969
  clojure.core$reduce/invoke at core.clj:6951
  com.biffweb.graph.impl$resolve_entities/invokeStatic at impl.clj:337
  com.biffweb.graph.impl$resolve_entities/invoke at impl.clj:336
  com.biffweb.graph.impl$query/invokeStatic at impl.clj:382
  com.biffweb.graph.impl$query/invoke at impl.clj:363
  com.biffweb.graph.impl$query/invokeStatic at impl.clj:365
  com.biffweb.graph.impl$query/invoke at impl.clj:363
  com.biffweb.graph$query/invokeStatic at graph.clj:48
  com.biffweb.graph$query/invoke at graph.clj:46
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

Root stack trace:
  com.biffweb.core.impl.validation$assertion_error/invokeStatic at validation.clj:18
  com.biffweb.core.impl.validation$assertion_error/doInvoke at validation.clj:17
  clojure.lang.RestFn/invoke at RestFn.java:515
  com.biffweb.core.impl.validation$validate_map/invokeStatic at validation.clj:48
  com.biffweb.core.impl.validation$validate_map/invoke at validation.clj:38
  com.biffweb.core.impl.validation$validate_STAR_/invokeStatic at validation.clj:61
  com.biffweb.core.impl.validation$validate_STAR_/doInvoke at validation.clj:50
  clojure.lang.RestFn/invoke at RestFn.java:413
  com.biffweb.graph.impl$wrap_validate$fn__12613$fn__12614/invoke at impl.clj:163
  com.biffweb.graph.impl$wrap_cache$fn__12629/invoke at impl.clj:206
  com.biffweb.graph.impl$resolve_attr$resolve_fn_SINGLEQUOTE___12712/invoke at impl.clj:291
  com.biffweb.graph.impl$resolve_attr$fn__12714/invoke at impl.clj:295
  clojure.core$mapv$fn__8569/invoke at core.clj:7063
  clojure.lang.PersistentVector/reduce at PersistentVector.java:418
  clojure.core$reduce/invokeStatic at core.clj:6968
  clojure.core$mapv/invokeStatic at core.clj:7054
  clojure.core$mapv/invoke at core.clj:7054
  com.biffweb.graph.impl$resolve_attr/invokeStatic at impl.clj:295
  com.biffweb.graph.impl$resolve_attr/invoke at impl.clj:258
  com.biffweb.graph.impl$resolve_entities$fn__12744/invoke at impl.clj:338
  clojure.core.protocols$iterator_reduce_BANG_/invokeStatic at protocols.clj:42
  clojure.core.protocols$iter_reduce/invokeStatic at protocols.clj:52
  clojure.core.protocols$fn__8260/invokeStatic at protocols.clj:74
  clojure.core.protocols$fn__8260/invoke at protocols.clj:74
  clojure.core.protocols$fn__8207$G__8202__8220/invoke at protocols.clj:13
  clojure.core$reduce/invokeStatic at core.clj:6969
  clojure.core$reduce/invoke at core.clj:6951
  com.biffweb.graph.impl$resolve_entities/invokeStatic at impl.clj:337
  com.biffweb.graph.impl$resolve_entities/invoke at impl.clj:336
  com.biffweb.graph.impl$query/invokeStatic at impl.clj:382
  com.biffweb.graph.impl$query/invoke at impl.clj:363
  com.biffweb.graph.impl$query/invokeStatic at impl.clj:365
  com.biffweb.graph.impl$query/invoke at impl.clj:363
  com.biffweb.graph$query/invokeStatic at graph.clj:48
  com.biffweb.graph$query/invoke at graph.clj:46
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
Root: java.lang.AssertionError - Assert failed: :x has conflicting shapes:
  {:kind :scalar}, {:kind :join}
(= shape (get attr->shape attr))

Root stack trace:
  com.biffweb.graph.impl$validate_query/invokeStatic at impl.clj:222
  com.biffweb.graph.impl$validate_query/invoke at impl.clj:220
  com.biffweb.graph.impl$new_env$fn__12657/invoke at impl.clj:243
  clojure.core$run_BANG_$fn__8926/invoke at core.clj:7911
  clojure.lang.PersistentVector/reduce at PersistentVector.java:418
  clojure.core$reduce/invokeStatic at core.clj:6968
  clojure.core$run_BANG_/invokeStatic at core.clj:7906
  clojure.core$run_BANG_/invoke at core.clj:7906
  com.biffweb.graph.impl$new_env/invokeStatic at impl.clj:243
  com.biffweb.graph.impl$new_env/doInvoke at impl.clj:228
  clojure.lang.RestFn/invoke at RestFn.java:426
  com.biffweb.graph$new_env/invokeStatic at graph.clj:44
  com.biffweb.graph$new_env/doInvoke at graph.clj:43
  clojure.lang.RestFn/invoke at RestFn.java:413
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
Root: java.lang.AssertionError - Missing required key: :biff.graph/output-ast

Root stack trace:
  com.biffweb.core.impl.validation$assertion_error/invokeStatic at validation.clj:18
  com.biffweb.core.impl.validation$assertion_error/doInvoke at validation.clj:17
  clojure.lang.RestFn/invoke at RestFn.java:460
  com.biffweb.core.impl.validation$validate_map/invokeStatic at validation.clj:40
  com.biffweb.core.impl.validation$validate_map/invoke at validation.clj:38
  com.biffweb.core.impl.validation$validate_STAR_/invokeStatic at validation.clj:61
  com.biffweb.core.impl.validation$validate_STAR_/doInvoke at validation.clj:50
  clojure.lang.RestFn/invoke at RestFn.java:426
  com.biffweb.graph.impl$new_env/invokeStatic at impl.clj:233
  com.biffweb.graph.impl$new_env/doInvoke at impl.clj:228
  clojure.lang.RestFn/invoke at RestFn.java:426
  com.biffweb.graph$new_env/invokeStatic at graph.clj:44
  com.biffweb.graph$new_env/doInvoke at graph.clj:43
  clojure.lang.RestFn/invoke at RestFn.java:413
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
  com.biffweb.graph.impl$resolve_attr/invokeStatic at impl.clj:260
  com.biffweb.graph.impl$resolve_attr/invoke at impl.clj:258
  com.biffweb.graph.impl$resolve_entities$fn__12744/invoke at impl.clj:338
  clojure.core.protocols$iterator_reduce_BANG_/invokeStatic at protocols.clj:42
  clojure.core.protocols$iter_reduce/invokeStatic at protocols.clj:52
  clojure.core.protocols$fn__8260/invokeStatic at protocols.clj:74
  clojure.core.protocols$fn__8260/invoke at protocols.clj:74
  clojure.core.protocols$fn__8207$G__8202__8220/invoke at protocols.clj:13
  clojure.core$reduce/invokeStatic at core.clj:6969
  clojure.core$reduce/invoke at core.clj:6951
  com.biffweb.graph.impl$resolve_entities/invokeStatic at impl.clj:337
  com.biffweb.graph.impl$resolve_entities/invoke at impl.clj:336
  com.biffweb.graph.impl$query/invokeStatic at impl.clj:382
  com.biffweb.graph.impl$query/invoke at impl.clj:363
  com.biffweb.graph$query/invokeStatic at graph.clj:50
  com.biffweb.graph$query/invoke at graph.clj:46
>>> error >>>
```

## Invalid get-env

```clojure
(require '[com.biffweb.graph :as graph])

(graph/query {:biff.graph/attr->resolvers {}
              :biff.graph/attr->shape {}
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
  com.biffweb.graph.impl$query/invokeStatic at impl.clj:372
  com.biffweb.graph.impl$query/invoke at impl.clj:363
  com.biffweb.graph.impl$query/invokeStatic at impl.clj:365
  com.biffweb.graph.impl$query/invoke at impl.clj:363
  com.biffweb.graph$query/invokeStatic at graph.clj:48
  com.biffweb.graph$query/invoke at graph.clj:46
>>> error >>>
```

## Invalid Resolver Map In Env

```clojure
(require '[com.biffweb.graph :as graph])

(graph/query {:biff.graph/attr->resolvers
              {:x [{:biff.graph/id :example/bad
                    :biff.graph/output-ast {:x {:kind :scalar}}
                    :biff.graph/resolve-fn (fn [_ctx] {})}]}
              :biff.graph/attr->shape {:x {:kind :scalar}}}
             [:x])
```

```
ERROR LOG com.biffweb.graph :biff.graph/error-example Invalid Resolver Map In Env

<<< error <<<
Root: java.lang.AssertionError - `:biff.graph/attr->resolvers {:x
  [#:biff.graph{:id :example/bad, :output-ast {…` is invalid: {:x
  [#:biff.graph{:input-ast ["missing required key"]}]}

Root stack trace:
  com.biffweb.core.impl.validation$assertion_error/invokeStatic at validation.clj:18
  com.biffweb.core.impl.validation$assertion_error/doInvoke at validation.clj:17
  clojure.lang.RestFn/invoke at RestFn.java:515
  com.biffweb.core.impl.validation$validate_map/invokeStatic at validation.clj:48
  com.biffweb.core.impl.validation$validate_map/invoke at validation.clj:38
  com.biffweb.core.impl.validation$validate_STAR_/invokeStatic at validation.clj:61
  com.biffweb.core.impl.validation$validate_STAR_/doInvoke at validation.clj:50
  clojure.lang.RestFn/invoke at RestFn.java:426
  com.biffweb.graph.impl$query/invokeStatic at impl.clj:374
  com.biffweb.graph.impl$query/invoke at impl.clj:363
  com.biffweb.graph.impl$query/invokeStatic at impl.clj:365
  com.biffweb.graph.impl$query/invoke at impl.clj:363
  com.biffweb.graph$query/invokeStatic at graph.clj:48
  com.biffweb.graph$query/invoke at graph.clj:46
>>> error >>>
```

## Conflicting Join Cardinalities

```clojure
(require '[com.biffweb.graph :as graph])

(def env
  (graph/new-env
   [(graph/resolver
     {:id :example/x
      :input [:id]
      :output [{:x [:y]}]
      :resolve-fn (fn [_ctx {:keys [id]}]
                    {:x (if (= id 1)
                          {:y 1}
                          [{:y 2}])})})]))

(graph/query env [{:id 1} {:id 2}] [{:x [:y]}])
```

```
ERROR LOG com.biffweb.graph :biff.graph/error-example Conflicting Join Cardinalities

<<< error <<<
Root: clojure.lang.ExceptionInfo - Got conflicting cardinalities

Root stack trace:
  com.biffweb.graph.impl$resolve_joins/invokeStatic at impl.clj:318
  com.biffweb.graph.impl$resolve_joins/invoke at impl.clj:314
  com.biffweb.graph.impl$resolve_entities$fn__12744/invoke at impl.clj:345
  clojure.core.protocols$iterator_reduce_BANG_/invokeStatic at protocols.clj:42
  clojure.core.protocols$iter_reduce/invokeStatic at protocols.clj:52
  clojure.core.protocols$fn__8260/invokeStatic at protocols.clj:74
  clojure.core.protocols$fn__8260/invoke at protocols.clj:74
  clojure.core.protocols$fn__8207$G__8202__8220/invoke at protocols.clj:13
  clojure.core$reduce/invokeStatic at core.clj:6969
  clojure.core$reduce/invoke at core.clj:6951
  com.biffweb.graph.impl$resolve_entities/invokeStatic at impl.clj:337
  com.biffweb.graph.impl$resolve_entities/invoke at impl.clj:336
  com.biffweb.graph.impl$query/invokeStatic at impl.clj:382
  com.biffweb.graph.impl$query/invoke at impl.clj:363
  com.biffweb.graph$query/invokeStatic at graph.clj:50
  com.biffweb.graph$query/invoke at graph.clj:46
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
  com.biffweb.core.impl.validation$assertion_error/invokeStatic at validation.clj:18
  com.biffweb.core.impl.validation$assertion_error/doInvoke at validation.clj:17
  clojure.lang.RestFn/invoke at RestFn.java:515
  com.biffweb.core.impl.validation$validate_map/invokeStatic at validation.clj:48
  com.biffweb.core.impl.validation$validate_map/invoke at validation.clj:38
  com.biffweb.core.impl.validation$validate_STAR_/invokeStatic at validation.clj:61
  com.biffweb.core.impl.validation$validate_STAR_/doInvoke at validation.clj:50
  clojure.lang.RestFn/invoke at RestFn.java:413
  com.biffweb.graph.impl$query/invokeStatic at impl.clj:367
  com.biffweb.graph.impl$query/invoke at impl.clj:363
  com.biffweb.graph.impl$query/invokeStatic at impl.clj:365
  com.biffweb.graph.impl$query/invoke at impl.clj:363
  com.biffweb.graph$query/invokeStatic at graph.clj:48
  com.biffweb.graph$query/invoke at graph.clj:46
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
  com.biffweb.core.impl.validation$assertion_error/invokeStatic at validation.clj:18
  com.biffweb.core.impl.validation$assertion_error/doInvoke at validation.clj:17
  clojure.lang.RestFn/invoke at RestFn.java:515
  com.biffweb.core.impl.validation$validate_map/invokeStatic at validation.clj:48
  com.biffweb.core.impl.validation$validate_map/invoke at validation.clj:38
  com.biffweb.core.impl.validation$validate_STAR_/invokeStatic at validation.clj:61
  com.biffweb.core.impl.validation$validate_STAR_/doInvoke at validation.clj:50
  clojure.lang.RestFn/invoke at RestFn.java:413
  com.biffweb.graph.impl$query/invokeStatic at impl.clj:367
  com.biffweb.graph.impl$query/invoke at impl.clj:363
  com.biffweb.graph$query/invokeStatic at graph.clj:50
  com.biffweb.graph$query/invoke at graph.clj:46
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
  :biff.graph/attr->resolvers, :biff.graph/attr->shape

Root stack trace:
  com.biffweb.core.impl.validation$assertion_error/invokeStatic at validation.clj:18
  com.biffweb.core.impl.validation$assertion_error/doInvoke at validation.clj:17
  clojure.lang.RestFn/invoke at RestFn.java:460
  com.biffweb.core.impl.validation$validate_map/invokeStatic at validation.clj:40
  com.biffweb.core.impl.validation$validate_map/invoke at validation.clj:38
  com.biffweb.core.impl.validation$validate_STAR_/invokeStatic at validation.clj:61
  com.biffweb.core.impl.validation$validate_STAR_/doInvoke at validation.clj:50
  clojure.lang.RestFn/invoke at RestFn.java:426
  com.biffweb.graph.impl$query/invokeStatic at impl.clj:374
  com.biffweb.graph.impl$query/invoke at impl.clj:363
  com.biffweb.graph.impl$query/invokeStatic at impl.clj:365
  com.biffweb.graph.impl$query/invoke at impl.clj:363
  com.biffweb.graph$query/invokeStatic at graph.clj:48
  com.biffweb.graph$query/invoke at graph.clj:46
>>> error >>>
```

## Conflicting Query Shape

```clojure
(require '[com.biffweb.graph :as graph])

(graph/query {:biff.graph/attr->resolvers {}
              :biff.graph/attr->shape {:x {:kind :scalar}}}
             [{:x [:y]}])
```

```
ERROR LOG com.biffweb.graph :biff.graph/error-example Conflicting Query Shape

<<< error <<<
Root: java.lang.AssertionError - Assert failed: :x has conflicting shapes:
  {:kind :join}, {:kind :scalar}
(= shape (get attr->shape attr))

Root stack trace:
  com.biffweb.graph.impl$validate_query/invokeStatic at impl.clj:222
  com.biffweb.graph.impl$validate_query/invoke at impl.clj:220
  com.biffweb.graph.impl$query/invokeStatic at impl.clj:377
  com.biffweb.graph.impl$query/invoke at impl.clj:363
  com.biffweb.graph.impl$query/invokeStatic at impl.clj:365
  com.biffweb.graph.impl$query/invoke at impl.clj:363
  com.biffweb.graph$query/invokeStatic at graph.clj:48
  com.biffweb.graph$query/invoke at graph.clj:46
>>> error >>>
```

## Unresolved Required Attribute

```clojure
(require '[com.biffweb.graph :as graph])

(graph/query {:biff.graph/attr->resolvers {}
              :biff.graph/attr->shape {:x {:kind :scalar}}}
             [:x])
```

```
ERROR LOG com.biffweb.graph :biff.graph/error-example Unresolved Required Attribute

<<< error <<<
Root: clojure.lang.ExceptionInfo - TODO
data: #:com.biffweb.graph.impl{:unresolved true}

Root stack trace:
  com.biffweb.graph.impl$query/invokeStatic at impl.clj:385
  com.biffweb.graph.impl$query/invoke at impl.clj:363
  com.biffweb.graph.impl$query/invokeStatic at impl.clj:365
  com.biffweb.graph.impl$query/invoke at impl.clj:363
  com.biffweb.graph$query/invokeStatic at graph.clj:48
  com.biffweb.graph$query/invoke at graph.clj:46
>>> error >>>
```
