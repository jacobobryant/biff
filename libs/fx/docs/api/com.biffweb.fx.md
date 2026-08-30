# com.biffweb.fx API

### machine

[view source](../../src/com/biffweb/fx.clj#L13)

```
(machine machine-name & {:as state->fn})
(machine machine-name initial-fx & {:as state->fn})

Returns a function that runs your code as a state machine.

machine-name
  An identifier (string, symbol, keyword...) that will be included in ex-data
  for any exceptions thrown by your state functions or handler functions.

state->fn
  A map from state keywords to state functions. Must include :start.
  Functions return a map describing effects to execute and (optionally) which
  state to transition to, or a single effect descriptor, or a non-map value.

An initial effect descriptor may be placed before the state definitions. Its
result is passed to :start immediately after ctx.

Returns (fn [ctx & args]). The :start function receives ctx followed by the
machine arguments. Other states receive ctx and the previous output map.
Call the machine with no arguments to get its state->fn map.

Example (see defmachine):

  (def handlers {:example.fx/http (fn [ctx request] ...)})
  (def ctx {:biff.fx/handlers handlers})

  (defmachine my-machine
    :start
    (fn [ctx]
      {:response     [:example.fx/http {:url ...}]
       :biff.fx/next :process})

    :process
    (fn [ctx {:keys [response]}]
      {:result (process-response response)}))

  (my-machine ctx)
  => {:result ...}
```

### defmachine

[view source](../../src/com/biffweb/fx.clj#L54)

```
(defmachine sym & {:as state->fn})

Defines a var containing an fx machine. Constructs machine-name from the
given symbol and the current namespace.

See com.biffweb.fx/machine.
```

### pipeline

[view source](../../src/com/biffweb/fx.clj#L63)

```
(pipeline machine-name & state-fns)
(pipeline machine-name state-fns)
(pipeline machine-name initial-fx & state-fns)
(pipeline machine-name initial-fx state-fns)

Like fx/machine but takes a sequence of unnamed state functions that
transition sequentially instead of using :biff.fx/next.

  (defpipeline my-pipeline
    (fn [ctx arg1 arg2]
      ...)

    (fn [ctx input]
      ...))

(See defpipeline.)

The first state is the :start state. States transition to the next state
function in the sequence. If a state function returns a map with
:biff.fx/return, the pipeline exits immediately.

State functions may be passed as varargs or as a single sequence. Also
supports an initial effect descriptor.

Call the pipeline function with no arguments to get the sequence of state
functions (for unit testing).
```

### defpipeline

[view source](../../src/com/biffweb/fx.clj#L92)

```
(defpipeline sym & args)

Defines a var containing an fx pipeline.
```

### module

[view source](../../src/com/biffweb/fx.clj#L97)

```
(module)

A biff.core module that collects :biff.fx/handlers from other modules.

Includes an init function that sets :biff.fx/get-handlers on the system map.
```

### uuid4

[view source](../../src/com/biffweb/fx.clj#L104)

```
(uuid4 seed)

Deterministically generates a v4 (random) UUID.

seed
  a long, e.g. the :biff.fx/seed value injected by `machine`.

Returns [uuid next-seed].

For subsequent RNG operations, be sure to use next-seed instead of the seed
you passed to this function.
```

### uuid7

[view source](../../src/com/biffweb/fx.clj#L117)

```
(uuid7 seed instant)

Deterministically generates a v7 (sequential random) UUID.

seed
  a long, e.g. the :biff.fx/seed value injected by `machine`.

Returns [uuid next-seed].

For subsequent RNG operations, be sure to use next-seed instead of the seed
you passed to this function.
```
