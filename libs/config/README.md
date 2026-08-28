# biff.config

A light Biff wrapper around [Aero](https://github.com/juxt/aero).

Includes a [Biff component](../core/README.md#components) for parsing a
`config.edn` file from your resources with Aero and merging it into the system
map. Also defines some reader tags for Aero:

- `#biff/env`: like `#env` but also reads dotenv-style values from a
  `config.env` file.
- `#biff/secret`: like `#biff/env` but wraps values in
  [`com.biffweb.core/secret-delay`](../core/README.md#secrets).

Also merges a `:biff.config/system-properties` map from your config into the
system properties, if set.

### Dependency

```clojure
com.biffweb/config {:mvn/version "2.0.0-rc21"}
```

### Status

This library will be a release candidate until all [the other Biff 2
libraries](/README.md) have been released. Until then there could be breaking
changes, but I don't anticipate any.

## Reference

- [Schema](docs/reference/schema.md)
- [API](docs/api/com.biffweb.config.md)

## Usage

Add a `resources/config.edn` to your project and ensure `"resources"` is on the
classpath. Put env vars in a `config.env` file in the working directory (and
list it in `.gitignore`, of course).

Then, if you're using biff.core, include this at the start of your `components`
vector:

```clojure
(require '[com.biffweb.config :as biff.config])

(def components
  [biff.config/use-aero-config
   ...])
```

And if not, call it yourself wherever is appropriate:

```clojure
(biff.config/use-aero-config {})
=> {...}
```

### Schema

If you register schema, biff.core will enforce it when `*assert*` is true:

```clojure
(require '[com.biffweb.core :as biff.core])

(biff.core/register
  {:com.example/client-id     :string
   :com.example/client-secret :biff.core/secret})
```

Use the `:biff.core/secret` schema for secrets.

### Migrating from Biff v1

`com.biffweb.config/use-aero-config` is a drop-in replacement for `com.biffweb/use-aero-config`.

## Tips / opinions

- See [an example resources/config.edn](../../demo/resources/config.edn) from
  the demo project.

- Prefer flat, namespaced keys over namespaced config (`:foo.bar/baz "quux"`
  instead of `:foo {:bar {:baz "quux"}}`

- When using biff.core, biff.config should be the interface between the config
  in your environment and the rest of your system. Your other code should always
  get config values from the system map instead of reading values from the
  environment directly.

- I used to use a different env var name for each environment (e.g.
  `MY_API_KEY_DEV` and `MY_API_KEY_PROD`) and then put conditional logic inside
  `resources/config.edn` via Aero's `#profile` tags. This is what Biff v1 does
  by default. I've since decided that it's cleaner to simply have different env
  var values but the same names for each environment (i.e. the normal way), and
  I no longer use `#profile` at all. Up to you.
