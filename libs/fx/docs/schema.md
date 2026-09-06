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

A state keyword to transition to after evaluating the current output map.

### :biff.fx/return

The value a machine returns after evaluating the current output map.

### :biff.fx/seq

A sequence of effect descriptors and output maps to evaluate in order. The
enclosing output map is evaluated and merged last.

### :biff.fx/now

`Instant`. The current time.

### :biff.fx/seed

`long`. A random seed. Use it like `(java.util.Random. seed)`."
