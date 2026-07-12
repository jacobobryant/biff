# com.biffweb.fx API

### machine

[view source](../../src/com/biffweb/fx.clj#L12)

```
(machine machine-name & {:as state->fn})

Returns a function that runs your code as a state machine.

state->fn
  A map from state keywords to state functions. Must include :start.
  Functions return a map describing effects to execute and (optionally) which
  state to transition to.

machine-name
  An identifier (string, symbol, keyword...) that will be included in ex-data
  for any exceptions thrown by your state functions or handler functions.

Returns (fn [ctx & [state]]).

Example (see defmachine):

  (def handlers {:example.fx/http (fn [ctx request] ...)})
  (def ctx {:biff.fx/handlers handlers})

  (defmachine my-machine
    :start
    (fn [ctx]
      {:response     [:example.fx/http {:url ...}]
       :biff.fx/next :process})

    :process
    (fn [{:keys [response]}]
      {:result (process-response response)}))

  (my-machine ctx)
  => {:result ...}
```

### defmachine

[view source](../../src/com/biffweb/fx.clj#L46)

```
(defmachine sym & {:as state->fn})

Defines a var containing an fx machine. Constructs machine-name from the
given symbol and the current namespace.

See com.biffweb.fx/machine.
```

### module

[view source](../../src/com/biffweb/fx.clj#L55)

```
(module)

A biff.core module that collects :biff.fx/handlers from other modules.

Includes an init function that sets :biff.fx/get-handlers on the system map.
```

### uuid4

[view source](../../src/com/biffweb/fx.clj#L62)

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

[view source](../../src/com/biffweb/fx.clj#L75)

```
(uuid7 seed instant)

Deterministically generates a v7 (sequential random) UUID.

seed
  a long, e.g. the :biff.fx/seed value injected by `machine`.

Returns [uuid next-seed].

For subsequent RNG operations, be sure to use next-seed instead of the seed
you passed to this function.
```
