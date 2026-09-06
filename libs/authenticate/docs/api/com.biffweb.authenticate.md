# com.biffweb.authenticate API

### turnstile-config

[view source](../../src/com/biffweb/authenticate.clj#L41)

```
Captcha config keys for Cloudflare Turnstile.

Include this map in the options passed to `routes` / `module`.
```

### recaptcha-config

[view source](../../src/com/biffweb/authenticate.clj#L48)

```
Captcha config keys for Google reCAPTCHA.

Include this map in the options passed to `routes` / `module`.
```

### hcaptcha-config

[view source](../../src/com/biffweb/authenticate.clj#L55)

```
Captcha config keys for hCaptcha.

Include this map in the options passed to `routes` / `module`.
```

### signout-path

[view source](../../src/com/biffweb/authenticate.clj#L62)

```
The URI path for the signout handler provided by `routes` / `module`.

To sign out, send a POST request to this path.
```

### routes

[view source](../../src/com/biffweb/authenticate.clj#L69)

```
(routes options)

Returns a collection of Reitit routes with `options` merged into requests.

Configuration:

- :biff.auth/captcha-configured?
- :biff.auth/include-signin-page
- :biff.auth/skip-captcha
- :biff.auth/skip-csrf-protection
- Additional configuration keys recognized by the individual routes

See docs/routes.md and docs/config.md. Configuration may be passed in
`options` or included in Ring request maps.
```

### module

[view source](../../src/com/biffweb/authenticate.clj#L85)

```
(module options)

A biff.core module that includes `routes` under :biff.ring/routes.
```
