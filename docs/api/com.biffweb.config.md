# com.biffweb.config

- [use-aero-config](#use-aero-config)

```
A light Biff wrapper around Aero.

SCHEMA

:biff.config/profile
keyword

  The Aero :profile value. Intended for testing/development; see use-aero-config.

:biff.config/system-properties
{"property" "value", ...}

  See use-aero-config.
```

### use-aero-config

[view source](../../libs/config/src/com/biffweb/config.clj#L81)

```
(use-aero-config {:biff.config/keys [profile], :as ctx})

Parses config.edn and merges into ctx. Also sets system properties.

Loads a config.edn file from resources and parses it with Aero. (See
https://github.com/juxt/aero). Two additional reader tags are supported:
#biff/env and #biff/secret. Keys with nil values (e.g. from unset env vars)
are filtered out.

#biff/env is like #env, but environment variables can also be specified in an
optional config.env file (read from the filesystem, not from resources) and
in the system properties (variable names should be prefixed with biff.env,
e.g biff.env.BIFF_PROFILE). If values are defined in multiple places,
precedence is as follows:

  1. System properties
  2. Actual environment variables
  3. config.env

The :profile value for Aero is also taken from these sources, in the
BIFF_PROFILE key (e.g. `BIFF_PROFILE=prod` -- the value is converted to a
keyword). It can also be passed in with `ctx` via the :biff.config/profile
key, but this is only intended as a convenience for inspecting your config
from the REPL.

#biff/secret is like #biff/env, but wraps values in biff.core/secret-delay so
that they aren't visible if you serialize the system map. Secrets can be
unwrapped with `force`:

  (let [{:keys [com.example/my-api-key]} ctx]
    (force my-api-key))

After config is merged into ctx, any entries in
(:biff.config/system-properties ctx) will be added to the system map:

  :biff.config/system-properties {"user.timezone" "UTC"}
  ;; Equivalent to:
  (System/setProperty "user.timezone" "UTC")

For backwards compatibility with Biff v1:

- secrets can be unwrapped by calling them as a zero-arg function:
`((:com.example/api-key ctx))`

- there is a :biff/secret function which can be used to unwrap secrets:
`((:biff/secret ctx) :com.example/api-key)`

- keys with a namespace of "biff.system-properties" are also merged into
  the system properties.
```
