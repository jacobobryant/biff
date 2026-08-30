# Machines

Machines (the functions returned by
[machine](../api/com.biffweb.fx.md#machine) / defined by
[defmachine](../api/com.biffweb.fx.md#defmachine)) have the form:

```clojure
(fn [ctx & args])
```

The `:start` state function is called first and receives `ctx` and any
additional arguments from the caller. Other states receive `ctx` and the
previous state's output map. Effects are executed after each state function
runs. State functions receive fresh `:biff.fx/now` and `:biff.fx/seed` values in
ctx.

Values in those maps that represent effects (\"effect descriptors\") are then
replaced with the results of their associated effect handler functions. A state
function can also return a standalone effect descriptor instead of a map. Any
other non-map value is returned as-is.

An effect descriptor is a vector whose first element is a key from your handlers
map (e.g. `[:com.example/http ...]`). Effect descriptors are only recognized
when they are the top-level values of the returned map or when they're placed
under `:biff.fx/seq` (see below).

The handlers map is constructed by merging in this order (1) a default handlers
map provided by biff.fx which contains a single `:biff.fx/http` handler, (2) the
value of `:biff.fx/handlers` from ctx, (3) the return value of calling the
`:biff.fx/get-handlers` function from ctx, if set.

Each value in the handlers map must be a function which takes at least one
argument (`ctx`) and any number of additional arguments. It performs an effect
and returns whatever value is appropriate. biff.fx will inject the `ctx`
argument, and the remaining arguments are taken from the effect descriptor.

Handler functions receive ctx as it was passed to the machine.

An initial effect descriptor may be placed before the state definitions. Its
result is passed to `:start` immediately after ctx and before additional machine
arguments.

```clojure
(fx/defmachine my-machine
  [:example/get-something ...]

  :start
  (fn [ctx something]
    ...))
```

If the state function includes `:biff.fx/next <state keyword>` in its output,
that state is transitioned to next. Otherwise the output is returned as the
final return value. `:biff.fx/return <value>` returns `<value>` after evaluating
the map's effects, which can be useful if you want to evaluate effects while
returning a non-map value. It's invalid to set both `:biff.fx/next` and
`:biff.fx/return` in the same output map.

If you need to evalaute multiple effects in a certain order, set `:biff.fx/seq`
to a sequence of effect descriptors and output maps. Standalone effect results
are discarded. The sequence's maps are merged from left to right, then the
enclosing output map is evaluated and merged last.

## Default handlers

To use the default `:biff.fx/http` handler, you must add Hato to your
dependencies. The handler calls `hato.client/request`. It accepts either a
single request map or a sequence of request maps, and it returns either a single
response map or a vector of response maps.

```clojure
[:biff.fx/http [request1 request2 ...]]
```

The response map(s) includes `:url` from the request. If you set
`:throw-exceptions true` on the request, biff.fx will catch exceptions and
return `{:url ..., :exception ...}` if there is one.
