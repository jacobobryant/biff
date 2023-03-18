---
title: Authentication
---

Biff includes an authentication plugin that implements passwordless,
email-based signin. There are two options: email links, where users click a
link in an email to sign in; and email codes, where users copy and paste a
six-digit code to sign in. The example project uses email links for the signup
form and email codes for the signin form.

The authentication plugin provides the backend routes, which handle sending
emails to your users and verifying the links and codes. UI and email templates
are handled in your application code, so they can be easily customized.

The example project comes with code for sending emails with
[Postmark](https://postmarkapp.com/). Until you add API keys for Postmark and
Recaptcha (which is used to protect your signin forms from bots), signin links
and codes will be printed to the console instead of being emailed.

After a user authenticates successfully, their user ID will be stored in the
session. For new users, a user document will be created as well. You can get
the user's ID from the session like so:

```clojure
(defn whoami [{:keys [session biff/db]}]
  (let [user (xt/entity db (:uid session))]
    [:html
     [:body
      [:div "Signed in: " (some? user)]
      [:div "Email: " (:user/email user)]]]))

(def features
  {:routes [["/whoami" {:get whoami}]]})
```

See the [`authentication-plugin` API docs](https://biffweb.com/docs/api/authentication/) for full details.
