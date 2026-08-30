# Schema

### :biff.datastar/buffer-size

Int. The size in bytes to use for Brotli4j's input buffer. Default 16 KB.

### :biff.datastar/condition

`java.util.concurrent.locks.Condition`. Used by `refresh` to signal to
`wrap-sse-render` that `:biff.datastar/epoch` has changed.

### :biff.datastar/epoch

Atom containing an int. This value is incremented by `refresh` and is used by
`wrap-sse-render` to infer when another SSE push should be sent to connected
clients.

### :biff.datastar/get-user-id

`(fn [ctx]) -> string or UUID`

Returns the authenticated user ID for the given Ring request. This is used as
the scope for `:biff.datastar/tab-id` and must return a trusted value, not a
value that the client can set arbitrarily. Defaults to `(comp :uid :session)`.

### :biff.datastar/lock

`java.util.concurrent.locks.ReentrantLock`. Used by `refresh` to signal to
`wrap-sse-render` that `:biff.datastar/epoch` has changed.

### :biff.datastar/quality

Int between 0 and 11 inclusive. The higher the value, the greater the
compression (and resource usage). Default 5.

See
[`BROTLI_PARAM_QUALITY`](https://github.com/google/brotli/blob/0d1f6297d6a4f6e2acd5e50ae9a5d22c3f55ba6d/c/include/brotli/encode.h#L156).

### :biff.datastar/rate-limit-ms

Positive int. The minimum number of milliseconds that must pass in between SSE
pushes from `wrap-sse-render` for a single connection. Default 20 (50 pushes per
second).

### :biff.datastar/signals

A (possibly nested) map of the parsed signals [sent by Datastar
actions](https://data-star.dev/guide/backend_requests#reading-signals). Signal
keys are converted to keywords using `_` as a separator:

```clojure
"foo_bar_baz" => :foo.bar/baz
```

There is no kebab <-> camel case conversion.

### :biff.datastar/sse-request

Boolean. When true, the current Ring handler's response will be pushed to the
client via SSE. Thus the response body should include only the HTML that is
meant to be swapped into the DOM (e.g. a top-level `div` with the `id` set).

### :biff.datastar/tab-id

UUID. Used to uniquely and securely identify a browser tab for the user. Derived
from `:biff.datastar/client-tab-id` and `:biff.datastar/get-user-id`. This value
is safe to use as a primary key for backend tab state.

### :biff.datastar/window-size

Int between 10 and 24 inclusive. The approximate size of the previous input (as
a power of two, i.e. `2^{window size}`) that Brotli will use during compression.
Default 18 (256 KB).

See [the Brotli RFC](https://datatracker.ietf.org/doc/html/rfc7932#section-2).
