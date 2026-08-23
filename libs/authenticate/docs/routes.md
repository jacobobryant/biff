# Routes

Notes:

- Endpoints accept both form and query parameters. Parameters are read from
  `:params`, `:body`, `:body-params`, `:json-params`, `:form-params`, and
  `:query-params`, in that order. Parameter keys may be keywords or strings.

- See the [configuration reference](config.md) for details about
  configuration keys. Configuration keys can be passed to `routes` / `module` or
  included on the Ring request map.

## Send signin code

`POST /_biff/auth/send-code`

Sends a signin code to the given email address. Captcha-protected. Works for new
users and existing users. Parameters:

```clojure
:email  ; The user's email address. Required.
```

Whatever parameters your captcha provider needs are also required.

Configuration:

```clojure
:biff.auth/captcha-verify
:biff.auth/signin-page
:biff.auth/email-valid?
:biff.auth/send-email
:biff.core/kv-set
```

Redirects to the signin page (`:biff.auth/signin-page`) with the following
query parameters:

- `sent-to={email}` on success.
- `error=invalid-email` if `:biff.auth/email-valid?` returned false.
- `error=captcha` if the captcha test failed.
- `error=send-failed` if `:biff.auth/send-email` returned false.

Any additional parameters are saved so they can later be provided to
`:biff.auth/create-user`. See [verify signin code](#verify-signin-code).

## Verify signin code

`POST /_biff/auth/verify-code`

Verifies a signin code for the given email address. Parameters:

```clojure
:email  ; The user's email address. Required.
:code   ; The signin code. Required.
```

Configuration:

```clojure
:biff.auth/app-path
:biff.auth/code-expiry-minutes
:biff.auth/signin-page
:biff.auth/create-user
:biff.auth/get-user-id
:biff.auth/max-failed-attempts
:biff.core/kv-get
:biff.core/kv-set
```

On success, redirects to `:biff.auth/app-path` and stores the user ID at `:uid`
on the session. For new users, the saved parameters from the [send signin
code](#send-signin-code) request are passed to `:biff.auth/create-user`.

On failure, the request redirects to the signin page (`:biff.auth/signin-page`)
with the query parameters `sent-to={email}&error=invalid-code`.

## Signout

`POST /_biff/auth/signout`

Removes all keys from `:session` and redirects to `/`.

## Signin page

`GET /signin`

Renders a default signin page backed by [send signin code](#send-signin-code)
and [verify signin code](#verify-signin-code). Parameters:

```clojure
:sent-to  ; Email address.
:error    ; One of "invalid-email", "captcha", "send-failed", "invalid-code".
```

Configuration:

```clojure
:biff.auth/app-name
:biff.auth/captcha-button-attrs
:biff.auth/captcha-configured?
:biff.auth/captcha-head
:biff.auth/captcha-widget
:biff.auth/logo-url
:biff.auth/primary-color
:biff.auth/signin-page
:biff.auth/skip-captcha
```
