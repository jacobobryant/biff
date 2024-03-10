---
title: Configuration
---

Most of your app's configuration is stored in the `resources/config.edn` file:

```clojure
{:biff/base-url #profile {:prod #join ["https://" #biff/env DOMAIN]
                          :default "http://localhost:8080"}
 :biff/host     #profile {:dev "0.0.0.0"
                          :default "localhost"}
 :biff/port     8080

 :biff.xtdb/dir      "storage/xtdb"
 :biff.xtdb/topology #keyword #or [#profile {:prod #biff/env "PROD_XTDB_TOPOLOGY"
                                             :default #biff/env "XTDB_TOPOLOGY"}
                                   "standalone"]
 :biff.xtdb.jdbc/jdbcUrl #biff/secret "XTDB_JDBC_URL"
...
```

This file is parsed with [Aero](https://github.com/juxt/aero). The
[`biff/use-aero-config`](/docs/api/utilities/#use-aero-config) component sets
the profile to the value of the `BIFF_PROFILE` environment variable. In
production, `BIFF_PROFILE` is set to `prod`; during development, it's set to
`dev`. You can add new profiles if needed, like a `:ci` profile for running
automated tests.

`biff/use-aero-config` merges your config into the system map. Since the system map
is in turn merged with incoming requests, you can read config values in your
request handlers like so:

```clojure
(defn hello [{:keys [biff/base-url] :as ctx}]
  [:html
   [:body
    [:p "This website is located at " base-url]]])

(def module
  {:routes [["/hello" {:get hello}]]})
```

Configuration is only read by `biff/use-aero-config` during app startup, so if you
modify `resources/config.edn` or `config.env` during development, you'll need to call
`com.example/refresh` for the changes to take effect.

## Secrets

Secrets should always be kept in the `config.env` file, which isn't checked into git:

```bash
MAILERSEND_API_KEY=abc123
...
```

The `biff/use-aero-secrets` component sets the `:biff/secret` key in the system map to a
function. That function takes a keyword and returns the associated secret. For
example, if your config and secrets files have the following contents:

```clojure
# config.env
MAILERSEND_API_KEY=abc123

;; config.edn
{:mailersend/api-key #biff/secret MAILERSEND_API_KEY ...
```

then the following handler would print `abc123` to the console:

```clojure
(defn hello [{:keys [biff/secret] :as ctx}]
  (println (secret :mailersend/api-key))
  ...)

(def module
  {:routes [["/hello" {:get hello}]]})
```

This is done so that your secrets won't be exposed if you serialize your system
map (e.g. by printing it to your logs).

If you need to provide different values for a secret in different
environments, you can specify separate environment variable names in
`resources/config.edn`:

```clojure
:stripe/api-key #profile {:prod "STRIPE_API_KEY_PROD"
                          :dev "STRIPE_API_KEY_TEST"}
```

## Customizing config

If you need to store your config and/or secrets in some other way, you can
replace `biff/use-aero-config` with a custom component:

```clojure
(defn use-custom-config [system]
  (let [config ...]
    (merge system config)))

(def components
  [use-custom-config
   biff/use-xtdb
   ...])
```

`use-custom-config` can load configuration in whatever way you'd like it to, as
long as it satisfies two conditions:

- `:biff/secret` is set to a 1-arg function that takes a keyword and returns the
  associated secret value.
- Non-secret config is merged directly into the system map.

---

See also:

- [`use-aero-config`](/docs/api/utilities/#use-aero-config)
- [Aero](https://github.com/juxt/aero)
