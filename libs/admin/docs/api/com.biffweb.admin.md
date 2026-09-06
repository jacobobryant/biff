# com.biffweb.admin API

### profile!

[view source](../../src/com/biffweb/admin.clj#L34)

```
(profile! #:biff.admin{:keys [pstats]} id f)

Calls `f`, storing profiling data from Tufte.

Stores the run time of `f` in the `pstats` atom, keyed by `id`.
```

### wrap-profiling

[view source](../../src/com/biffweb/admin.clj#L42)

```
(wrap-profiling handler)

Wraps a Reitit Ring handler with `profile!`.

Uses the request method and Reitit route name or path template as the
profiling ID.
```

### wrap-resolver-profiling

[view source](../../src/com/biffweb/admin.clj#L50)

```
(wrap-resolver-profiling resolver)

Wraps a biff.graph resolver with `profile!`.

The resolver's :biff.graph/id is used as the profiling ID.
```

### flush-pstats!

[view source](../../src/com/biffweb/admin.clj#L57)

```
(flush-pstats! {:keys [biff.admin/pstats biff.core/kv-set], :as ctx})

Persists profiling data with :biff.core/kv-set.

Moves data from the `pstats` atom into the database via `kv-set`, with
:biff.admin/pstats as the KV namespace.
```

### routes

[view source](../../src/com/biffweb/admin.clj#L66)

```
(routes options)

Returns the admin dashboard's Reitit routes.

`options` is merged into each request. See docs/schema.md for the available
options.
```

### module

[view source](../../src/com/biffweb/admin.clj#L74)

```
(module params)

A biff.core module for the admin dashboard. Module ID is :biff.admin/module.

Includes `routes`, biff.ring and biff.graph profiling middleware, an hourly
biff.background task that calls `flush-pstats!`, and initialization for
profiling state.

Forwards clojure.tools.logging errors to Telemere, and adds a Telemere signal
handler that stores logged errors in memory. Errors are reported via email in
batches of at most 20. Emails are rate-limited at 1 per 5 minutes. If more
than 20 errors are logged in that time, only the most recent 20 are reported.

Errors reported via email are also stored via :biff.core/kv-set and are
viewable on the biff.admin dashboard. This module should start after whatever
module defines the :biff.core/kv-* functions.
```
