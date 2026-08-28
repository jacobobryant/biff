# com.biffweb.admin API

### profile!

[view source](../../src/com/biffweb/admin.clj#L35)

```
(profile! ctx id f)

Calls `f`, records Tufte profiling data under `id`, and returns the result.

Profiling data is added to :biff.admin/pstats. If `ctx` does not contain
:biff.admin/pstats or `id` is nil, calls `f` without profiling it.
```

### wrap-profiling

[view source](../../src/com/biffweb/admin.clj#L43)

```
(wrap-profiling handler)

Wraps a Ring handler with `profile!`.

Requests are identified by their uppercase HTTP method followed by their
Reitit route name or path template. Requests without a matched route are not
profiled.
```

### wrap-resolver-profiling

[view source](../../src/com/biffweb/admin.clj#L52)

```
(wrap-resolver-profiling resolver)

Wraps a biff.graph resolver's :biff.graph/resolve-fn with `profile!`.

The resolver's :biff.graph/id is used as the profiling ID.
```

### flush-pstats!

[view source](../../src/com/biffweb/admin.clj#L59)

```
(flush-pstats! ctx)

Persists profiling data with :biff.core/kv-set.

Data is stored under :biff.admin/pstats using UTC date strings as keys.
After flushing, only data for the current UTC date is retained in memory.
```

### routes

[view source](../../src/com/biffweb/admin.clj#L67)

```
(routes options)

Returns the admin dashboard's Reitit routes.

`options` is merged into each request. See docs/schema.md for the available
options.
```

### module

[view source](../../src/com/biffweb/admin.clj#L75)

```
(module params)

Returns a biff.core module for the admin dashboard.

Includes `routes`, Ring and biff.graph profiling middleware, an hourly task
that calls `flush-pstats!`, and initialization for profiling state.
```

### use-alerts

[view source](../../src/com/biffweb/admin.clj#L83)

```
(use-alerts ctx)

Adds an error handler and alert state to `ctx`.

Error signals are retained for display in the admin dashboard. If
:biff.admin/send-email and :biff.admin/alert-email are set, error alerts are
also sent by email. `conj`s a handler-removal function onto
:biff.core/stop.
```
