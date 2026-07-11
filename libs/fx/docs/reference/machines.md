# Machines

Machines (the functions returned by
[machine](../api/com.biffweb.fx.md#machine) / defined by
[defmachine](#defmachine)) have the form:

```clojure
(fn [ctx & [state]])
```

If a machine is called with a single argument (`ctx`), the `:start` state
function is called first, and then the machine transitions to other states until
completion. Effects are executed after each state function runs. If the function
is also called with the second `state` argument (a keyword), the associated
state function will be called with no transitions or effects (useful for unit
testing).

Each state funtion is called with a single argument: the `ctx` map, merged with
output from the previous state function (see below), with fresh `:biff.fx/now`
and `:biff.fx/seed` keys injected. Each state function must return either a map
or a sequence of maps.

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

When calling a handler function, `ctx` is merged with the \"output so far\" from
the current state function. If the state function returned a map, this is the
output map with any effect descriptors (and their associated keys) removed. If
the state function returned a sequence of maps, this includes the output map
that's currently being evaluated (without effect descriptors) merged with all
the previous maps (including the effect results).

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
