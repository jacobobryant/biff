---
title: Configuration
---

Most of your app's configuration is stored in the `config.edn` file:

```clojure
{:prod {:biff/base-url "https://example.com"
        :biff.xtdb/dir "storage/xtdb"
        :biff.xtdb/topology :standalone
        :postmark/api-key "POSTMARK_API_KEY"
        :postmark/from "hello@example.com"
        ...}
 :dev {:merge [:prod]
       :biff/base-url "http://localhost:8080"
       :biff.middleware/secure false
       ...}
 :tasks {:biff.tasks/deploy-cmd ["git" "push" "prod" "master"]
         :biff.tasks/server "example.com"
         ...}}
```

The `biff/use-config` component checks the `BIFF_ENV` environment variable to
know which section of the config file should be used. In production, `BIFF_ENV`
is set to `prod`; during development, it's set to `dev`. You can add new
sections if needed, like a `:ci` section for running automated tests. You can
add `:merge [:prod]` or similar to a config section to make it inherit values
from other sections.

The `:tasks` section isn't used by `biff/use-config`; it's instead read by
[bb tasks](/docs/reference/bb-tasks/).

`biff/use-config` merges your config into the system map. Since the system map
is in turn merged with incoming requests, you can read config values in your
request handlers like so:

```clojure
(defn hello [{:keys [biff/base-url] :as ctx}]
  [:html
   [:body
    [:p "This website is located at " base-url]]])

(def plugin
  {:routes [["/hello" {:get hello}]]})
```

Configuration is only read by `biff/use-config` during app startup, so if you
modify the `config.edn` file during development, you'll need to call
`com.example/refresh` for the changes to take effect.

## Secrets

Secrets are stored in the `secrets.env` file, which is used to populate
environment variables before starting your app:

```bash
export POSTMARK_API_KEY=abc123
...
```

The `biff/use-secrets` component sets the `:biff/secret` key in the system map to a
function. That function takes a keyword and returns the associated secret. For
example, if your config and secrets files have the following contents:

```clojure
# secrets.env
export POSTMARK_API_KEY=abc123

;; config.edn
{:prod {:postmark/api-key "POSTMARK_API_KEY"
...
```

then the following handler would print `abc123` to the console:

```clojure
(defn hello [{:keys [biff/secret] :as ctx}]
  (println (secret :postmark/api-key))
  ...)

(def plugin
  {:routes [["/hello" {:get hello}]]})
```

In other words: the `:biff/secret` function first looks up the value of the
given keyword in your configuration, which should be set to the name of an
environment variable. Then the `:biff/secret` function returns the value of
that environment variable.

If you need to provide different values for a secret in different
environmentns, you can specify separate environment variable names in
`config.edn`:

```clojure
{:prod {:stripe/api-key "STRIPE_API_KEY_PROD"
        ...}
 :dev {:stripe/api-key "STRIPE_API_KEY_TEST"
       ...}}
```

## Version control

`config.edn` isn't checked into git by default, but it's safe to do so as long
as you remember to store all your secrets in `secrets.env` instead of
`config.edn`. Leaving `config.edn` out of source control is helpful if you're
developing an open-source app, so other users can supply their own
configuration files.

`secrets.env` should not be checked into git.
