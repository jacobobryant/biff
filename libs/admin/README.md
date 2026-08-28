# biff.admin

An admin dashboard for Biff applications. Includes:

- Metrics for active users, signups, and revenue.

- User directory, including controls for impersonation.

- Performance profiling (via [Tufte](https://github.com/taoensso/tufte)).

- Simple email-based alerting for logged errors / uncaught exceptions
  (via [Telemere](https://github.com/taoensso/telemere)).

- System resource usage.

![biff.admin demo](https://obryant-dev.nyc3.cdn.digitaloceanspaces.com/biff/biff-admin-demo-v2.gif)

### Dependency

```clojure
com.biffweb/admin {:mvn/version "2.0.0-rc21"}
```

### Status

This library will be a release candidate until all [the other Biff 2
libraries](/README.md) have been released. Until then there could be breaking
changes, but I don't anticipate any.

## Reference

- [Schema](docs/schema.md)
- [API](docs/api/com.biffweb.admin.md)

## Usage

Add `module` and `use-alerts` to your biff.core modules/components:

```clojure
(require '[com.biffweb.admin :as biff.admin])

(def admin-module (biff.admin/module {...}))

(def modules
  [(biff.ring/module)
   (biff.background/module)
   (biff.graph/module)
   admin-module
   ...])

(def components
  [...
   biff.admin/use-alerts
   biff.background/use-scheduled-tasks
   biff.ring/use-jetty])
```

This module depends on `biff.ring` and `biff.background`. `biff.graph` is
optional; if you use it, the dashboard will include profiling data for your
resolvers. This module also depends on `:biff.core/kv-set` and
`:biff.core/kv-get` being set; if you use biff.sqlite or biff.xtdb those should
be configured already.

After setup, the admin dashboard will be available at `/_biff/admin`.

### Authenticating

You must set `:biff.admin/admin-user-id` (e.g. in `resources/config.edn`, if
you're using biff.config) to the value of `(:uid session)`. If that key is not
set and you navigate to `/_biff/admin`, your current `(:uid session)` value will
be displayed so you can copy it in to your config. The admin dashboard will
coerce the ID to a string.

```
;; resources/config.edn
{:biff.admin/admin-user-id #biff/env BIFF_ADMIN_USER_ID}

# config.env
BIFF_ADMIN_USER_ID=...
```

### Email alerts

To receive email alerts when errors are logged via Telemere or
`clojure.tools.logging`, you must set `:biff.admin/alert-email` and
`:biff.admin/send-email`:

```clojure
(defn send-email [ctx {:keys [to subject text html]}]
  ...)

(def admin-module
  (biff.admin/module
   {:biff.admin/send-email #'send-email
    ...}))

;; resources/config.edn
{:biff.admin/alert-email #biff/env BIFF_ADMIN_ALERT_EMAIL}
```

An example `send-email` implementation using
[MailerSend](https://mailersend.com):

```clojure
(require '[hato.client :as hato])
(require '[clojure.tools.logging :as log])

(defn send-email
  [{:mailersend/keys [api-key from from-name reply-to]}
   {:keys [to subject html text]}]
  (let [response
        (hato/post
         "https://api.mailersend.com/v1/email"
         {:headers          {;; use force to unwrap biff.config secrets
                             "Authorization" (str "Bearer " (force api-key))}
          :content-type     :json
          :throw-exceptions false
          :as               :json
          :form-params      {:from     {:email from
                                        :name  from-name}
                             :reply_to {:email reply-to
                                        :name  from-name}
                             :to       [{:email to}]
                             :subject  subject
                             :html     html
                             :text     text}})]
    (when (<= 400 (:status response))
      (log/warn "MailerSend error:" (:body response)))
    (< (:status response) 400)))
```

### Metrics

Provide these functions to populate the metrics page:

```clojure
(defn get-users [ctx]
  [{:user-id   #uuid "..."
    :email     "hello@example.com"
    :joined-at #inst "..."}
   ...])

(defn get-usage-events [ctx]
  [{:instant #inst "..."
    :user-id #uuid "..."}
   ...])

(defn get-revenue-events [ctx]
  [{:instant #inst "..."
    :revenue 19.99}
   ...])

(def admin-module
  (biff.admin/module
   {:biff.admin/get-users #'get-users
    :biff.admin/get-usage-events #'get-usage-events
    :biff.admin/get-revenue-events #'get-revenue-events
    ...}))
```

The metrics page displays up to 30 days of data. Revenue events older than 30
days will be ignored, and usage events older than 37 days will be ignored.

### User directory

To populate the user directory, provide the `get-users` function described in
[Metrics](#metrics). The user directory includes an impersonation feature: you
can create a sign-in link (valid for 5 minutes) that will set `(:uid session)`
to the selected user's ID.
