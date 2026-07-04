## Schema

### :biff.fx/handlers

```
{:com.example/do-something (fn [ctx & args]),
...}
```

A map from effect keywords to handler functions.

### :biff.fx/get-handlers

`(fn []) => {...}`

A function that returns a `:biff.fx/handlers` map. Intended for use by
`:biff.core/init` functions.

### :biff.fx/next

A key in the state->fn map passed to biff.fx/machine.

### :biff.fx/now

`Instant`. The current time.

### :biff.fx/seed

`long`. A random seed. Use it like `(java.util.Random. seed)`."
