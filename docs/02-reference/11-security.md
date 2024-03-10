---
title: Security
---

## Authentication

Biff includes an authentication module that implements passwordless,
email-based signin. There are two options: email links, where users click a
link in an email to sign in; and email codes, where users copy and paste a
six-digit code to sign in. The starter project uses email links for the signup
form and email codes for the signin form.

The authentication module provides the backend routes, which handle sending
emails to your users and verifying the links and codes. UI and email templates
are handled in your application code so that they can be easily customized.

The starter project comes with code for sending emails with
[MailerSend](https://www.mailersend.com/). Until you add API keys for MailerSend and
Recaptcha (which is used to protect your signin forms from bots), signin links
and codes will be printed to the console instead of being emailed.

After a user authenticates successfully, their user ID will be stored in the
session. Sessions are stored in encrypted cookies.
For new users, a user document will be created as well. You can get
the user's ID from the session like so:

```clojure
(defn whoami [{:keys [session biff/db]}]
  (let [user (xt/entity db (:uid session))]
    [:html
     [:body
      [:p "Signed in: " (some? user)]
      [:p "Email: " (:user/email user)]]]))

(def module
  {:routes [["/whoami" {:get whoami}]]})
```

See the [`authentication-module` API docs](https://biffweb.com/docs/api/authentication/) for full details.

If you need to modify the module beyond what the configuration options allow,
you can copy
[the source code](https://github.com/jacobobryant/biff/blob/master/src/com/biffweb/impl/auth.clj)
into your project or replace it altogether.

Your `config.env` file contains two secrets which are used to encrypt/sign
your session cookies and JWTs, respectively. If you want to rotate the secrets,
you can generate new values by running `clj -M:dev generate-secrets`.

## Authorization

You can use middleware to restrict routes to certain users. The starter project comes
with a `wrap-signed-in` middleware which redirects unauthenticated users to the signin page:

```clojure
(defn wrap-signed-in [handler]
  (fn [{:keys [session] :as ctx}]
    (if (some? (:uid session))
      (handler ctx)
      {:status 303
       :headers {"location" "/signin?error=not-signed-in"}})))
```

If you need additional roles, you could add a `:user/roles` key to your user documents:

```clojure
(defn wrap-admin [handler]
  (fn [{:keys [biff/db session] :as ctx}]
    (let [user (xt/entity db (:uid session))]
      (if (contains? (:user/roles user) :admin)
        (handler ctx)
        {:status 403
         :headers {"content-type" "text/plain"}
         :body "Unauthorized."}))))
```

## CSRF and CORS

Biff uses
[ring-anti-forgery](https://github.com/ring-clojure/ring-anti-forgery) for CSRF
protection. You can add a CSRF token to your forms like so:

```clojure
(ns com.example.app
  (:require [ring.middleware.anti-forgery :as csrf]))

(defn signin [ctx]
  [:html
   [:body
    [:form {:method "POST"
            :action "/signin"}
     [:input {:type "hidden"
              :name "__anti-forgery-token"
              :value csrf/*anti-forgery-token*}]
     [:input {:type "email" :name "email"}]
     ...]]])
```

There is a `biff/form` function which does this for you, in addition to
providing a couple other conveniences:

```clojure
(defn signin [ctx]
  [:html
   [:body
    (biff/form
     {:action "/signin"}
     [:input {:type "email" :name "email"}]
     ...)]])
```

The starter project also includes a `com.example.ui/page` function, which will inject the CSRF token
into all htmx requests that are triggered by child elements, even if they aren't triggered inside a form element:

```clojure
(defn page [ctx & body]
  (base
   ctx
   ...
   [:div (when (bound? #'csrf/*anti-forgery-token*)
           {:hx-headers (cheshire/generate-string
                         {:x-csrf-token csrf/*anti-forgery-token*})})
    body]
   ...))
```

CSRF protection only applies to routes that are included under the `:routes` key of your modules. If you want to
bypass CSRF protection (e.g. because you're providing a public API), you can use the `:api-routes` key:

```clojure
(defn hello [ctx]
  {:status 200
   :headers {"content-type" "application/json"}
   :body {:foo "bar"}})

(def module
  {:api-routes [["/api/hello" {:post hello}]]})
```

Biff doesn't include any CORS middleware by default. If you need to bypass CORS
protection (e.g. because you're providing a public API that needs to be called
from the frontend), you can use
[ring-cors](https://github.com/r0man/ring-cors).
