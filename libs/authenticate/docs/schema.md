# Schema

Notes:

- Email normalization: whitespace is trimmed from the start and beginning and
  characters are converted to lower case.

- Hiccup is rendered with `dev.onionpancakes.chassis.core/html`. See its
  documentation for details about what's valid.

## Integration

### :biff.auth/create-user

`(fn [ctx {:keys [email params]}]) -> user ID`

Create a new user for the given normalized `email`. Returns the user ID which is
used as the value for `(:uid session)`.

`params` is the `(:params ctx)` value from the request when the user submitted
the signin form (whereas this function is called in a subsequent request) and
can be used with a custom signup form to collect additional information on
signup.

This function should handle race conditions where the request is submitted
twice, i.e. it should ensure that two concurrent calls won't result in two
separate user entities being created with the same email address.

### :biff.auth/get-user-id

`(fn [ctx email]) -> user ID`

Returns the ID for the user associated with the given normalized `email`, or
`nil` if there is none. On successful signin for an existing user, the returned
value is used as the value for `(:uid session)`.

### :biff.auth/send-email

`(fn [ctx {:keys [to subject html text template url code]}]) -> boolean`

Sends a signin email to a user who may or may not have an account already.
Returns `true` if the email was sent successfully and `false` otherwise.

```clojure
:to        ; normalized email
:subject   ; string, subject line
:html      ; string, html email body
:text      ; string, text email body
:template  ; one of #{:signin-link :signin-code}
:url       ; string, the signin link. Set when :template is :signin-link.
:code      ; string, the signin code. Set when :template is :signin-code.
```

`:subject`, `:html`, and `:text` are provided by the default template. You can
use these and ignore `:template`, `:url`, and `:code`, or you can instead use
the latter parameters to generate your own subject / html / text.

## Appearance

### :biff.auth/app-name

String. The user-visible name of the application, used on the signin form and in
signin emails.

### :biff.auth/primary-color

String, default `#4F46E5` (indigo). The primary color for the signin form.

### :biff.auth/accent-color

String, default `#818CF8` (light indigo). The accent color for the signin form.

### :biff.auth/logo-url

String. If set, used to display a logo on the signin form.

## Captcha

### :biff.auth/captcha-button-attrs

`(fn [ctx]) -> {...}`

Returns a map of hiccup DOM options for use on the signin form submit button
that are needed by the captcha provider. For example:

```clojure
[:button (captcha-button-attrs ctx) ...]
```

### :biff.auth/captcha-configured?

`(fn [ctx]) -> boolean`

Returns true if the necessary configuration (e.g. site key, secret key) for the
captcha provider is present.

### :biff.auth/captcha-head

`(fn [ctx]) -> hiccup`

Returns hiccup to be included in the `<head>` element needed by the captcha
provider. For example:

```clojure
[:head ... (captcha-head ctx)]
```

### :biff.auth/captcha-param

Keyword. A key in `(:params ctx)` that will be omitted from the `params` map
passed to `create-user`.

### :biff.auth/captcha-verify

`(fn [ctx]) -> boolean`

Given an incoming Ring request triggered by a captcha-protected form, returns
true if the captcha test passed.

### :biff.auth/captcha-widget

`(fn [ctx]) -> hiccup`

Returns hiccup to be included in the `<form>` element being protected by the
captcha provider, above the submit button. For example:

```clojure
[:form
...
 (captcha-widget ctx)
 [:button ...]]
```

### :biff.auth/hcaptcha-secret

String wrapped with `#biff/secret` /
[`biff.core/secret-delay`](/libs/core/docs/api/com.biffweb.core.md#secret-delay).
Required when using `hcaptcha-config`.

### :biff.auth/hcaptcha-site-key

String. Required when using `hcaptcha-config`.

### :biff.auth/recaptcha-secret

String wrapped with `#biff/secret` /
[`biff.core/secret-delay`](/libs/core/docs/api/com.biffweb.core.md#secret-delay).
Required when using `recaptcha-config`.

### :biff.auth/recaptcha-site-key

String. Required when using `recaptcha-config`.

### :biff.auth/recaptcha-threshold

Number between 0 and 1, default 0.5. When using `recaptcha-config` and Recaptcha
v3, the minimum score for a recaptcha request to be considered successful.

### :biff.auth/turnstile-secret

String wrapped with `#biff/secret` /
[`biff.core/secret-delay`](/libs/core/docs/api/com.biffweb.core.md#secret-delay).
Required when using `turnstile-config`.

### :biff.auth/turnstile-site-key

String. Required when using `turnstile-config`.

## Behavior

### :biff.auth/app-path

String, default `/app`. The path to redirect to after a successful signin.

### :biff.auth/base-url

String, example `"https://example.com"`. The base URL to use for signin links.

### :biff.auth/code-expiry-minutes

Int, default 10. The number of minutes a new signin code is valid.

### :biff.auth/code-page

String, default `/signin`. The path for the signin-via-code page. Used for
redirects.

### :biff.auth/email-validator

`(fn [ctx email]) -> boolean`

A function that returns true if `email` is a valid email address. The default
function ensures that `email`:

- is a string.
- includes a `@` with at least one character before and after.
- does not include whitespace.

### :biff.auth/include-signin-page

Boolean, default true. If `false`, the default signin page will not be included
in Reitit routes.

### :biff.auth/link-expiry-minutes

Int, default 60. The number of minutes a new signin link is valid.

### :biff.auth/link-page

String, default `/signup`. The path for the signin-via-link page. Used for
redirects.

### :biff.auth/max-failed-attempts

Int, default 5. The number of times an incorrect signin code can be provided for
a particular email address before a new signin code must be requested.

### :biff.auth/skip-captcha

Boolean, default false. When true, a captcha test is not required to request a
signin code or link. This setting is only meant for development.

### :biff.auth/skip-csrf-protection

Boolean, default false. When true, the Reitit routes will not be wrapped with
`ring.middleware.anti-forgery`. You should only set this if you have other CSRF
protection in place.

### :biff.auth/verify-link-page

String, default `/signup/verify`. The path for the signin-via-link verification
page. Used for redirects.
