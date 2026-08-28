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

Set `:biff.fx/test` in ctx to a state keyword to call only that state. Effects
are not evaluated in test mode, and additional machine arguments are passed
directly to the state function.

Values in those maps that represent effects (\"effect descriptors\") are then
replaced with the results of their associated effect handler functions. If the
state function returned a sequence of maps, those maps are updated in order and
then merged together as the \"output.\"

An effect descriptor is a vector whose first element is a key from your handlers
map (e.g. `[:com.example/http ...]`). Effect descriptors are only recognized
when they are the top-level values of the returned map.

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
arguments. It is not evaluated in test mode.

```clojure
(fx/defmachine my-machine
  [:example/get-something ...]

  :start
  (fn [ctx something]
    ...))
```

If the state function includes `:biff.fx/next <state keyword>` in its output,
that state is transitioned to next. Otherwise the output is returned as the
final return value. If you want the machine to return something other than a
map, you can include `:biff.fx/return <value>` in the output, in which case
`<value>` will be the final return value. It's invalid to set both
`:biff.fx/next` and `:biff.fx/return` in the same output map.

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
