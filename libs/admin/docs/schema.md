# Schema

### :biff.admin/alert-email

String. The email address to which error alerts will be sent.

### :biff.admin/get-revenue-events

`(fn [ctx]) -> sequence of revenue events`

Returns the revenue events used to calculate daily revenue on the metrics page.
Each revenue event is a map containing the keys:

```clojure
:instant  ; Instant. The time at which the revenue was received.
:revenue  ; Number. The amount of revenue received. Will be formatted as
          ; dollars.
```

### :biff.admin/get-usage-events

`(fn [ctx]) -> sequence of usage events`

Returns the activity events used to calculate daily and weekly active users on
the metrics page. Each usage event is a map containing the keys:

```clojure
:user-id  ; The user ID. Any type.
:instant  ; Instant. The time at which the user performed some action.
```

A user is considered "active" on any days for which they have at least one usage
event.

### :biff.admin/get-users

`(fn [ctx]) -> sequence of users`

Returns the users shown on the users page and used to calculate daily signups on
the metrics page. Each user is a map containing the keys:

```clojure
:user-id    ; The user ID. Any type.
:email      ; String.
:joined-at  ; Instant. The time at which the user joined.
```

### :biff.admin/send-email

`(fn [ctx email-opts])`

Sends an error alert email. `email-opts` is a map containing the keys:

```clojure
:to       ; String. Recipient email address.
:subject  ; String. Subject line.
:text     ; String. Plain-text email body.
:html     ; String. HTML email body.
```

`:biff.admin/alert-email` is used as the `:to` value.

### :biff.admin/admin-user-id

Any type. The ID of the user who can access the admin dashboard. Will be
coerced to a string and compared to `(str (:uid session))`.
