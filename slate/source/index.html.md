---
title: Biff

language_tabs: # must be one of https://git.io/vQNgJ
  - Clojure

toc_footers:

includes:

search: true
---

# Introduction

Biff is designed to make web development with Clojure fast and easy, especially
for early stage startups and hobby projects. Over time I'd like to make it
suitable for apps that need scale as well.
I use it in production for <a href="https://findka.com"
target="_blank">Findka</a>, my startup. I started writing Biff after 18 months
of experimenting with various web technologies like Firebase, Datomic and
several Clojure web frameworks/libraries. It includes:

- **Installation and deployment** on DigitalOcean.
- **Crux** (an immutable document database with Datalog queries).
- **Subscriptions**. Specify what data the frontend needs declaratively, and
  Biff will keep it up-to-date.
- **Read/write authorization rules**. No need to set up a bunch of endpoints
  for CRUD operations. Queries and transactions can be submitted from the
  frontend as long as they pass the rules you define.
- **Database triggers**. Run code when documents of certain types are created,
  updated or deleted.
- **Authentication**. Email link for now; password and SSO coming later.
- **Websocket communication**.
- Serving **static resources**.
- **Multitenancy**. Run multiple apps from the same process.

Also: instead of trying to do everything for everyone, Biff is designed to be
easy to take apart (without forking). This should help mitigate the main
drawback of frameworks, which is that it's often less work in the long run to
just stitch the libraries together yourself.

Biff is currently alpha quality. Join `#biff` on <a
href="http://clojurians.net" target="_blank">Clojurians Slack</a> for
discussion. Feel free to reach out for help, bug reports or anything else. I
spend most of my time trying to figure out how the h\*ck to do marketing for
Findka, so Biff-related distractions are always welcome. Also see the <a
href="https://github.com/jacobobryant/biff/issues" target="_blank">issues</a>
on Github.

# Getting started

The fastest way to get started with Biff is by cloning the Github repo and running the
official example project (an implementation of Tic Tac Toe):

1. Install dependencies (clj, npm and overmind)
2. `git clone https://github.com/jacobobryant/biff`
3. `cd biff/example`
4. `./task setup`
5. `./task dev`
6. Go to `localhost:9630` and start the `app` build
7. Go to `localhost:8080`

You can tinker with this app and use it as a template for your own projects. See
[Production](#production) when you want to deploy.

# Build system

The example project uses tools.deps, Shadow CLJS, and Overmind.
`task` is the main entrypoint:

<div class="file-heading"><a href="https://github.com/jacobobryant/biff/blob/master/example/task" target="_blank">
task</a></div>
```bash
#!/bin/bash
set -e

setup () {
  which clj npm overmind > /dev/null # Assert dependencies
  npm install # Or `npm init; npm install --save-dev shadow-cljs; npm install --save react react-dom`
  clj -Stree > /dev/null # Download project dependencies
}

repl () {
  BIFF_ENV=dev clj -m biff.core
}

cljs () {
  npx shadow-cljs server
}

dev () {
  overmind start
}

...

"$@"
```

<div class="file-heading"><a href="https://github.com/jacobobryant/biff/blob/master/example/Procfile" target="_blank">
Procfile</a></div>
```shell
repl: ./task repl
cljs: ./task cljs
```

So `./task dev` will use Overmind to run `clj` and `shadow-cljs` in the same
terminal window. Hit `ctrl-c` to exit. If you'd like to restart just one
process, run e.g. `overmind restart repl` in another window. You can easily add
new build tasks by creating new functions in `task`. Also, I recommend putting
`alias t='./task'` in your `.bashrc`.

To fetch the latest Biff version, start out with only `:git/url` and `:tag` in the dependency:

<div class="file-heading"><a href="https://github.com/jacobobryant/biff/blob/master/example/deps.edn" target="_blank">
deps.edn</a></div>
```clojure
{...
 :deps
 {github-jacobobryant/biff
  {:git/url "https://github.com/jacobobryant/biff"
   :tag "HEAD"}}}
```

Then run `clj -Sresolve-tags`. The latest commit hash will be added to `deps.edn`.

# Backend entrypoint

When you run `clj -m biff.core`, Biff searches the classpath for plugins and then starts
them in a certain order. To define a plugin, you must set `^:biff` metadata on a namespace
and then set a `components` var to a list of plugin objects in that namespace:

<div class="file-heading"><a href="https://github.com/jacobobryant/biff/blob/master/example/src/hello/core.clj" target="_blank">
src/hello/core.clj</a></div>
```clojure
(ns ^:biff hello.core
  (:require
    [biff.system]
    [clojure.pprint :refer [pprint]]
    [hello.handlers]
    [hello.routes]
    [hello.rules]
    [hello.static]
    [hello.triggers]))

(defn send-email [opts]
  (pprint [:send-email opts]))

(defn start-hello [sys]
  (-> sys
    (merge #:hello.biff.auth{:send-email send-email
                             :on-signup "/signin/sent/"
                             :on-signin-request "/signin/sent/"
                             :on-signin-fail "/signin/fail/"
                             :on-signin "/app/"
                             :on-signout "/"})
    (merge #:hello.biff{:routes hello.routes/routes
                        :static-pages hello.static/pages
                        :event-handler #(hello.handlers/api % (:?data %))
                        :rules hello.rules/rules
                        :triggers hello.triggers/triggers})
    (biff.system/start-biff 'hello)))

(def components
  [{:name :hello/core
    :requires [:biff/init]
    :required-by [:biff/web-server]
    :start start-hello}])
```

`:biff/init` and `:biff/web-server` are plugins defined in the `biff.core`
namespace. The `:requires` and `:required-by` values are used to start plugins
in the right order. You can think of plugins kind of like Ring middleware. They
receive a system map and pass along a modified version of it.

`biff.system/start-biff` starts a Biff app. That includes initializing:

 - a Crux node
 - a Sente websocket listener and event router
 - some default event handlers that handle queries, transactions and subscriptions from the frontend
 - a Crux transaction listener, so clients can be notified of subscription updates
 - some Reitit HTTP routes for authentication
 - any static resources you've included with your app

If you connect to nrepl port 7888, you can access the system with
`@biff.core/system`. Biff provides some helper functions for repl-driven
development:

```clojure
(biff.util/stop-system @biff.core/system)
(biff.core/start)
(biff.core/refresh) ; stops, reloads namespaces from filesystem, starts.
```

# Configuration

App-specific configuration can go in your plugin, as shown above. For example, we set
`:hello.biff.auth/on-signin` so that clients will be redirected to `/app/` after they
sign in successfully.

Environment-specific configuration and secrets can go in `config.edn`. They will be read in
by the `:biff/init` plugin.

<div class="file-heading"><a href="https://github.com/jacobobryant/biff/blob/master/example/config.edn" target="_blank">
config.edn</a></div>
```clojure
{:prod {:biff.crux.jdbc/user "..."
        :biff.crux.jdbc/password "..."
        :biff.crux.jdbc/host "..."
        :biff.crux.jdbc/port ...
        :hello.biff/host "example.com"
        :hello.biff.crux.jdbc/dbname "hello"
        :hello.mailgun/api-key "..."}
 :dev {:inherit [:prod]
       :biff/dev true
       :hello.biff/host "localhost"}}
```

<aside class="warning">Keep this file out of source control if it contains any secrets.</aside>

The system map is mostly flat, with namespaced keys. For example, Biff
configuration for the example app is stored under the `:hello.biff` namespace.
You could run multiple Biff apps in the same process by using a different
namespace, e.g. `(biff.system/start-biff sys 'another-app)`. Keys under
the `:biff` namespace (e.g. `:biff/dev` from `config.edn` above) will become
defaults for all Biff apps.

Similarly, the return values from `biff.system/start-biff` will be namespaced. For example,
you can get the Crux node in our example app with `(:hello.biff/node @biff.core/system)`.

When the `:biff/init` plugin reads in your `config.edn` file, it will merge the
nested maps according to the current environment and the value of `:inherit`.
The default environment is `:prod`. This can be overridden by setting the
`BIFF_ENV` environment variable:

```shell
BIFF_ENV=dev clj -m biff.core
```

Here is a complete list of configuration options and their default values. See the following sections
for a deeper explanation.

Note: `:foo/*` is used to denote all keywords prefixed by `:foo/` or `:foo.`.

```clojure
; === Config for the :biff/init plugin ===

:biff.init/start-nrepl true
:biff.init/nrepl-port 7888
:biff.init/instrument false ; Calls orchestra.spec.test/instrument if true.
:timbre/* ...               ; These keys are passed to taoensso.timbre/merge-config!
                            ; (without the timbre prefix).


; === Config for biff.system/start-biff ===
; Note: app-ns is the second parameter in biff.system/start-biff

; RECOMMENDED
:biff/host nil          ; The hostname this app will be served on, e.g. "example.com" for prod
                        ; or "localhost" for dev.
:biff/static-pages nil  ; A map from paths to Rum data structures, e.g.
                        ; {"/hello/" [:html [:body [:p "hello"]]]}
:biff/rules nil         ; An authorization rules data structure.
:biff/fn-whitelist nil  ; Collection of fully-qualified function symbols to allow in
                        ; Crux queries sent from the frontend. Functions in clojure.core
                        ; need not be qualified. For example: '[map? example.core/frobulate]
:biff/triggers nil      ; A database triggers data structure.
:biff/routes nil        ; A vector of Reitit routes.
:biff/event-handler nil ; A Sente event handler function.

:biff.auth/send-email nil ; A function.
:biff.auth/on-signup nil  ; Redirect route, e.g. "/signup/success/".
:biff.auth/on-signin-request nil
:biff.auth/on-signin-fail nil
:biff.auth/on-signin nil
:biff.auth/on-signout nil

; Ignored if :biff.crux/topology isn't :jdbc.
:biff.crux.jdbc/user nil
:biff.crux.jdbc/password nil
:biff.crux.jdbc/host nil
:biff.crux.jdbc/port nil

:biff/dev false ; When true, sets the following config options (overriding any specified values):
                ; {:biff.crux/topology :standalone
                ;  :biff.handler/secure-defaults false
                ;  :biff.static/root-dev "www-dev"}

; OPTIONAL
:biff.crux/topology :jdbc ; One of #{:jdbc :standalone}
:biff.crux/storage-dir "data/{{app-ns}}/crux-db" ; Directory to store Crux files.
:biff.crux.jdbc/* ...     ; Passed to crux.api/start-node (without the biff prefix) if
                          ; :biff.crux/topology is :jdbc. In this case, you must set
                          ; :biff.crux.jdbc/{user,password,host,port}.
:biff.crux.jdbc/dbname (str app-ns)
:biff.crux.jdbc/dbtype "postgresql"

:biff.static/root "www/{{value of :biff/host}}" ; Directory from which to serve static files.
:biff.static/root-dev nil                       ; An additional static file directory.
:biff.static/resource-root "www/{{app-ns}}"     ; Resource directory where static files are stored.

:biff.handler/not-found-path "{{value of :biff.static/root}}/404.html"
:biff.handler/secure-defaults true ; Whether to use ring.middleware.defaults/secure-site-defaults
                                   ; or just site-defaults.


; === Config for the :biff/web-server plugin ===

:biff.web/host->handler ... ; Set by biff.system/start-biff. A map used to dispatch Ring
                            ; requests. For example:
                            ; {"localhost" (constantly {:status 200 ...})
                            ;  "example.com" (constantly {:status 200 ...})}
:biff.web/port 8080         ; Port for the web server to listen on. Also used in
                            ; biff.system/start-biff.
```

The following keys are added to the system map by `biff.system/start-biff`:

 - `:biff/base-url`: e.g. `"https://example.com"` or `"http://localhost:8080"`
 - `:biff/node`: the Crux node.
 - `:biff/send-event`: the value of `:send-fn` returned by `taoensso.sente/make-channel-socket!`.
 - `:biff.sente/connected-uids`: Ditto but for `:connected-uids`.
 - `:biff.crux/subscriptions`: An atom used to keep track of which clients have subscribed
   to which queries.

`biff.system/start-biff` merges the system map into incoming Ring requests and Sente events. It also
adds `:biff/db` (a Crux DB value) on each new request/event.
Note that
the keys will not be prefixed yet&mdash;so within a request/event handler, you'd use `:biff/node` to get
the Crux node, but within a separate Biff plugin you'd use e.g. `:example.biff/node`.

# Static resources

Relevant config:

```clojure
:biff/static-pages nil  ; A map from paths to Rum data structures, e.g.
                        ; {"/hello/" [:html [:body [:p "hello"]]]}

:biff.static/root "www/{{value of :biff/host}}" ; Directory from which to serve static files.
:biff.static/root-dev nil                       ; An additional static file directory.
:biff.static/resource-root "www/{{app-ns}}"     ; Resource directory where static files are stored.

:biff/dev false ; When true, sets the following config options (overriding any specified values):
                ; {:biff.static/root-dev "www-dev"
                ;  ...}
```

Biff will copy your static resources to `www/yourwebsite.com/` (i.e. the value
of `:biff.static/root`). In production, `www/` is a symlink to `/var/www/` and
is served directly by Nginx (so static files will be served even if your JVM
process goes down, e.g. during deployment). In development, the JVM process
will serve files from that directory.

Biff gets static resources from two places. First, it will render HTML
files from the value of `:biff/static-pages` on startup.

<div class="file-heading"><a href="https://github.com/jacobobryant/biff/blob/master/example/src/hello/static.clj" target="_blank">
src/hello/static.clj</a></div>
```clojure
(ns hello.static
  (:require
    [rum.core :as rum]))

(def signin-form
  (rum/fragment
    [:p "Email address:"]
    [:form {:action "/api/signin-request" :method "post"}
     [:input {:name "email" :type "email" :placeholder "Email"}]
     [:button {:type "submit"} "Sign up/Sign in"]]))

(def home
  [:html
   [:head
    [:meta {:charset "utf-8"}]
    [:script {:src "/js/ensure-signed-out.js"}]]
   [:body
    signin-form]])

(def signin-sent
  [:html [:head [:meta {:charset "utf-8"}]]
   [:body
    [:p "Sign-in link sent, please check your inbox."]
    [:p "(Just kidding: click on the sign-in link that was printed to the console.)"]]])

(def signin-fail
  [:html [:head [:meta {:charset "utf-8"}]]
   [:body
    [:p "Invalid sign-in token."]
    signin-form]])

(def app
  [:html
   [:head
    [:meta {:charset "utf-8"}]
    [:script {:src "/js/ensure-signed-in.js"}]]
   [:body
    [:#app "Loading..."]
    [:script {:src "/cljs/app/main.js"}]]])

(def not-found
  [:html [:head [:meta {:charset "utf-8"}]]
   [:body
    [:p "Not found."]]])

(def pages
  {"/" home
   "/signin/sent/" signin-sent
   "/signin/fail/" signin-fail
   "/app/" app
   "/404.html" not-found})
```

Second, Biff will look for files in the resource directory specified
by `:biff.static/resource-root`.

```bash
biff/example $ tree resources/
resources/
└── www
    └── hello
        └── js
            ├── ensure-signed-in.js
            └── ensure-signed-out.js
```

I currently commit generated resources (except for HTML files, but including
CLJS compilation output) to the git repo. If you prefer, you can easily add
initialization code to your app that instead generates the resources during
deployment (or downloads them from a CI server).

I'd like to add a CDN integration eventually.

# Authentication

Relevant config:

```clojure
:biff.auth/send-email nil ; A function.
:biff.auth/on-signup nil  ; Redirect route, e.g. "/signup/success/".
:biff.auth/on-signin-request nil
:biff.auth/on-signin-fail nil
:biff.auth/on-signin nil
:biff.auth/on-signout nil
```

Biff currently provides email link authentication (i.e. the user clicks a link
in an email to sign in). Password and SSO authentication are on the roadmap.

Biff provides a set of HTTP endpoints for authentication:

## Sign up

Sends the user an email with a sign-in link. Redirects to the value of
`:biff.auth/on-signup`. If the email is sent successfully and the user
hasn't already signed up, creates a user
document in Crux with the email and a random UUID.

The token included in the link will expire after 30 minutes.

### HTTP Request

`POST /api/signup`

### Form Parameters

Parameter | Description
----------|------------
email | The user's email address.

### Example Usage

```clojure
[:p "Email address:"]
[:form {:action "/api/signup" :method "post"}
 [:input {:name "email" :type "email" :placeholder "Email"}]
 [:button {:type "submit"} "Sign up"]]
```

The `:biff.auth/send-email` function will be called with the following options:

```clojure
(send-email {:to "alice@example.com"
             :template :biff.auth/signup
             :data {:biff.auth/link "https://example.com/api/signin?token=..."}})
```

You will have to provide a `send-email` function which generates an email from
the template data and sends it. (The example app just prints the template data
to the console). If you use Mailgun, you can do something like this:

```clojure
(def templates
  {:biff.auth/signup
   (fn [{:keys [biff.auth/link to]}]
     {:subject "Thanks for signing up"
      :html (rum.core/render-static-markup
              [:div
               [:p "We received a request to sign up with Findka using this email address."]
               [:p [:a {:href link} "Click here to sign up."]]
               [:p "If you did not request this link, you can ignore this email."]])})
   :biff.auth/signin ...})

(defn send-email* [api-key opts]
  (http/post (str "https://api.mailgun.net/v3/mail.example.com/messages")
    {:basic-auth ["api" api-key]
     :form-params (assoc opts :from "Example <contact@mail.example.com>")}))

(defn send-email [{:keys [to text template data api-key] :as opts}]
  (if (some? template)
    (if-some [template-fn (get templates template)]
      (send-email* api-key
        (assoc (template-fn (assoc data :to to)) :to to))
      (println "Email template not found:" template))
    (send-email* api-key (select-keys opts [:to :subject :text :html]))))

(defn start-example [{:keys [example.mailgun/api-key] :as sys}]
  (-> sys
    (merge
      {:example.biff.auth/send-email #(send-email (assoc % :api-key api-key))
       ...})
    (biff.system/start-biff 'example.biff)))
```

## Request sign-in

Sends the user an email with a sign-in link. This is the same as [Sign up](#sign-up),
except:

 - The endpoint is `/api/signin-request`
 - The template key will be set to `:biff.auth/signin`
 - The user will be redirected to the value of `:biff.auth/on-signin-request`

## Sign in

Verifies the given JWT and adds a `:uid` key to the user's session (expires in
90 days). Redirects to the value of `:biff.auth/on-signin` (or
`:biff.auth/on-signin-fail` if the token is incorrect or expired).

Also sets a `:csrf` cookie, the value of which must be included in the
`X-CSRF-Token` header for any HTTP requests that require authentication.

### HTTP Request

`GET /api/signin`

### URL Parameters

Parameter | Description
----------|------------
token | A JWT

### Example Usage

This endpoint is used by the link generated by [Sign up](#sign-up) and [Request
sign-in](#request-sign-in), so you typically won't need to generate a link for
this endpoint yourself. However, if you'd like to use a longer expiration date for the
token or authenticate at a custom endpoint, you can do it like so:

```clojure
(biff.util/token-url {:url (str (:biff/base-url sys) "/api/unsubscribe")
                      :claims {:email "alice@example.com"
                               :uid "abc123"}
                      :jwt-secret (biff.auth/jwt-key sys)
                      :iss "example"
                      :expires-in (* 60 60 24 7 4)})

; Or for just the token:
(biff.util/mint {:secret (biff.auth/jwt-key sys)
                 :iss "example"
                 :expires-in (* 60 60 24 7 4)}
                {:email "alice@example.com"
                 :uid "abc123"})
```

After a user is signed in, you can authenticate them on the backend from an event/request
handler like so:

```clojure
(defn handler [{:keys [session/uid biff/db]}]
  (if (some? uid)
    (prn (crux.api/entity db {:user/id uid}))
    ; => {:crux.db/id {:user/id #uuid "..."}
    ;     :user/id #uuid "..." ; duplicated for query convenience
    ;     :user/email "alice@example.com"}
    (println "User not authenticated.")))
```

## Sign out

Clears the user's session and `:csrf` cookie. Redirects to the value of
`:biff.auth/on-signout`.

See <a href="https://github.com/jacobobryant/biff/issues/26" target="_blank">#26</a>.

### HTTP Request

`GET /api/signout`

### Example Usage

```clojure
[:a {:href "/api/signout"} "sign out"]
```

## Check if signed in

Returns status 200 if the user is authenticated, else 403.

See <a href="https://github.com/jacobobryant/biff/issues/26" target="_blank">#26</a>.

### HTTP Request

`GET /api/signed-in`

### Example Usage

Include this on your landing page:

<div class="file-heading"><a href="https://github.com/jacobobryant/biff/blob/master/example/resources/www/hello/js/ensure-signed-out.js" target="_blank">
resources/www/hello/js/ensure-signed-out.js</a></div>
```javascript
fetch("/api/signed-in").then(response => {
  if (response.status == 200) {
    document.location = "/app";
  }
});
```

Include this on your app page:

<div class="file-heading"><a href="https://github.com/jacobobryant/biff/blob/master/example/resources/www/hello/js/ensure-signed-in.js" target="_blank">
resources/www/hello/js/ensure-signed-in.js</a></div>
```javascript
fetch("/api/signed-in").then(response => {
  if (response.status != 200) {
    document.location = "/";
  }
});
```

# Client/server communication

Relevant config:

```clojure
:biff/routes nil        ; A vector of Reitit routes.
:biff/event-handler nil ; A Sente event handler function.
:biff.handler/not-found-path "{{value of :biff.static/root}}/404.html"
:biff.handler/secure-defaults true ; Whether to use ring.middleware.defaults/secure-site-defaults
                                   ; or just site-defaults.
:biff/dev false ; When true, sets the following config options (overriding any specified values):
                ; {:biff.handler/secure-defaults false
                ;  ...}
```

## HTTP

The value of `:biff/routes` will be wrapped with some default middleware which, among other things:

 - Applies a modified version of `ring.middleware.defaults/secure-site-defaults` (or `site-defaults`).
 - Merges the system map into the request (so you can access configuration and other things).
 - Sets `:biff/db` to a current Crux db value.
 - Flattens the `:session` and `:params` maps (so you can do e.g. `(:session/uid request)` instead
   of `(:uid (:session request))`).
 - Sets default values of `{:body "" :status 200}` for responses.
 - Nests any `:headers/*` or `:cookies/*` keys (so `:headers/Content-Type "text/plain"` expands
   to `:headers {"Content-Type" "text/plain"}`).

<div class="file-heading"><a href="https://github.com/jacobobryant/biff/blob/master/example/src/hello/routes.clj" target="_blank">
src/hello/routes.clj</a></div>
```clojure
(ns hello.routes
  (:require
    [biff.util :as bu]
    ...))

(defn echo [req]
  {:headers/Content-Type "application/edn"
   :body (prn-str
           (merge
             (select-keys req [:params :body-params])
             (bu/select-ns req 'params)))})

(def routes
  [["/echo" {:get echo
             :post echo
             :name ::echo}]
   ...])
```

```shell
$ curl -XPOST localhost:8080/echo?foo=1 -F bar=2
{:params {:foo "1", :bar "2"}, :params/bar "2", :params/foo "1"}
$ curl -XPOST localhost:8080/echo -d '{:foo 1}' -H "Content-Type: application/edn"
{:params {}, :body-params {:foo 1}}
```

For endpoints that require authentication, you must wrap anti-forgery middleware. Also,
be sure not to make any `GET` endpoints that require authentication as these bypass anti-forgery
checks.

<div class="file-heading"><a href="https://github.com/jacobobryant/biff/blob/master/example/src/hello/routes.clj" target="_blank">
src/hello/routes.clj</a></div>
```clojure
(ns hello.routes
  (:require
    [biff.util :as bu]
    [crux.api :as crux]
    [ring.middleware.anti-forgery :as anti-forgery]))

...

(defn whoami [{:keys [session/uid biff/db]}]
  (if (some? uid)
    {:body (:user/email (crux/entity db {:user/id uid}))
     :headers/Content-Type "text/plain"}
    {:status 401
     :headers/Content-Type "text/plain"
     :body "Not authorized."}))

(defn whoami2 [{:keys [session/uid biff/db]}]
  {:body (:user/email (crux/entity db {:user/id uid}))
   :headers/Content-Type "text/plain"})

(def routes
  [...
   ["/whoami" {:post whoami
               :middleware [anti-forgery/wrap-anti-forgery]
               :name ::whoami}]
   ; Same as whoami
   ["/whoami2" {:post whoami2
                :middleware [bu/wrap-authorize]
                :name ::whoami2}]])
```

When calling these endpoints, you must include the value of the `csrf` cookie in the
`X-CSRF-Token` header:

```clojure
(cljs-http.client/post "/whoami" {:headers {"X-CSRF-Token" (biff.util/csrf)}})
; => {:status 200, :body "alice@example.com", ...}
```

However, communicating over websockets is usually more convenient, in which
case this is unnecessary.

## Web sockets

Web sockets are the preferred method of communication. First, set `:biff/event-handler`:

<div class="file-heading"><a href="https://github.com/jacobobryant/biff/blob/master/example/src/hello/core.clj" target="_blank">
src/hello/core.clj</a></div>
```clojure
(defn start-hello [sys]
  (-> sys
    ...
    (merge #:hello.biff{:event-handler #(hello.handlers/api % (:?data %))
                        ...})
    (biff.system/start-biff 'hello)))
```

<div class="file-heading"><a href="https://github.com/jacobobryant/biff/blob/master/example/src/hello/handlers.clj" target="_blank">
src/hello/handlers.clj</a></div>
```clojure
(ns hello.handlers
  (:require
    [biff.util :as bu]
    ...))

(defmulti api :id)

(defmethod api :default
  [{:keys [id]} _]
  (bu/anom :not-found (str "No method for " id)))

(defmethod api :hello/move
  [{:keys [biff/db session/uid] :as sys} {:keys [game-id location]}]
  ...)

(defmethod api :hello/echo
  [{:keys [client-id biff/send-event]} arg]
  (send-event client-id [:hello/prn ":hello/echo called"])
  arg)
```

Biff provides a helper function for initializing the web socket connection on the frontend:

<div class="file-heading"><a href="https://github.com/jacobobryant/biff/blob/master/example/src/hello/client/app.cljs" target="_blank">
src/hello/client/app.cljs</a></div>
```clojure
(defn ^:export init []
  (reset! hello.client.app.system/system
    (biff.util/init-sub {:handler hello.client.app.mutations/api
                         ...}))
  ...)
```

<div class="file-heading"><a href="https://github.com/jacobobryant/biff/blob/master/example/src/hello/client/app/mutations.cljs" target="_blank">
src/hello/client/app/mutations.cljs</a></div>
```clojure
(defmulti api (comp first :?data))

(defmethod api :default
  [{[event-id] :?data} data]
  (println "unhandled event:" event-id))

(defmethod api :biff/error
  [_ anom]
  (pprint anom))

(defmethod api :hello/prn
  [_ arg]
  (prn arg))

(defn api-send [& args]
  (apply (:api-send @hello.client.app.system/system) args))

(comment
  (go
    (<! (api-send [:hello/echo {:foo "bar"}]))
    ; => {:foo "bar"}
    ; => ":hello/echo called"
    ))
```

# Transactions

You can send arbitrary transactions from the frontend. They will be submitted
only if they pass certain authorization rules which you define (see
[Rules](#rules)). Transactions look like this:

<div class="file-heading"><a href="https://github.com/jacobobryant/biff/blob/master/example/src/hello/client/app/mutations.cljs" target="_blank">
src/hello/client/app/mutations.cljs</a></div>

```clojure
(defn set-display-name [display-name]
  (api-send
    [:biff/tx
     {[:public-users {:user.public/id @db/uid}]
      {:db/merge true
       :display-name (or (not-empty display-name) :db/remove)}}]))

(defn set-game-id [game-id]
  (when (not= game-id @db/game-id)
    (api-send
      [:biff/tx
       (cond-> {}
         (not-empty @db/game-id)
         (assoc [:games {:game/id @db/game-id}]
           {:db/update true
            :users [:db/disj @db/uid]})

         (not-empty game-id)
         (assoc [:games {:game/id game-id}]
           {:db/merge true
            :users [:db/union @db/uid]}))])))
```

The transaction is a map from idents to documents. The first element of an
ident is a table, such as `:games`. Tables are defined by your rules, and they
specify which rules a document write must pass in order to be allowed.

The second element, if present, is a document ID. If omitted, it means we're
creating a new document and we want the server to set the ID to a random UUID:

```clojure
{[:messages] {:text "hello"}}
```

If you want to create multiple documents in the same table with random IDs, use
a nested vector instead of a map.

```clojure
[[[:messages] {:text "a"}]
 [[:messages] {:text "b"}]]
```

`:db/current-time` is replaced by the server with the current time.

```clojure
{[:events] {:timestamp :db/current-time
            ...}}
```

If `:db/update` is true, the given document will be merged with an existing
document, failing if the document doesn't exist. There's also `:db/merge` which
simply creates the document if it doesn't exist (i.e. upsert).

```clojure
{[:chatrooms {:chatroom/id #uuid "some-uuid"}]
 {:db/update true
  :title "Existing chatroom"}

 [:chatrooms {:chatroom/id #uuid "another-uuid"}]
 {:db/merge true
  :title "New or existing chatroom"}}
```

You can `dissoc` document keys by setting them to `:db/remove`. You can
delete whole documents by setting them to `nil`.

```clojure
{[:users {:user/id #uuid "my-id"}]
 {:db/update true
  :display-name :db/remove}

 [:orders {:order/id #uuid "some-order-id"}]
 nil}
```

You can add or remove an element to/from a set by using `:db/union` and
`:db/disj`, respectively:

```clojure
{[:games {:game/id #uuid "old-game-uuid"}]
 {:db/update true
  :users [:db/disj "my-uid"]}

 [:games {:game/id #uuid "new-game-uuid"}]
 {:db/update true
  :users [:db/union "my-uid"]}}
```

Using maps as document IDs lets you specify composite IDs. In addition, all
keys in in the document ID will be duplicated in the document itself. This
allows you to use document ID keys in your queries.

```clojure
{[:user-item {:user #uuid "some-user-id"
              :item #uuid "some-item-id"}]
 {:rating :like}}

; Expands to:
[:crux.tx/put
 {:crux.db/id {:user #uuid "some-user-id"
               :item #uuid "some-item-id"}
  :user #uuid "some-user-id"
  :item #uuid "some-item-id"
  :rating :like}]
```

# Subscriptions

Biff allows you to subscribe to Crux queries from the frontend with one major
caveat: cross-entity joins are not allowed. Basically, this means all the where
clauses in the query have to be for the same entity.

```clojure
; OK
'{:find [doc]
  :where [[doc :foo 1]
          [doc :bar "hey"]]}

; Not OK
'{:find [doc]
  :where [[user :name "Tilly"]
          [doc :user user]]}
```

So to be clear, Biff's subscribable "queries" are not datalog at all. They're
just predicates that can take advantage of Crux's indices. Biff makes this
restriction so that it can provide query updates to clients efficiently without
having to solve a hard research problem first. However, it turns out that we can
go quite far even with this restriction.

On the frontend, use `biff.util/init-sub` to initialize a websocket connection
that handles query subscriptions for you:

```clojure
(def default-subscriptions
  #{[:biff/sub '{:table :users
                 :where [[:name "Ben"]
                         [:age age]
                         [(<= 18 age)]
                         [(yourapp.core/likes-cheese? doc)]]}]})

(def subscriptions (atom default-subscriptions))
(def sub-data (atom {}))

(biff.util/init-sub
  {:subscriptions subscriptions
   :sub-data sub-data})
```


If you want to subscribe to a query, `swap!` it into `subscriptions`. If you
want to unsubscribe, `swap!` it out. Biff will populate `sub-data` with the
results of your queries and remove old data when you unsubscribe. You can then
use the contents of that atom to drive your UI. The contents of `sub-data` is a
map of the form `subscription->doc-id->doc`, for example:

```clojure
{[:biff/sub '{:table :users
              :where ...}]
 {{:user/id #uuid "some-uuid"} {:name "Sven"
                                :age 250
                                ...}}}
```

Note the subscription format again:

```clojure
[:biff/sub '{:table :users
             :where [[:name "Ben"]
                     [:age age]
                     [(<= 18 age)]
                     [(yourapp.core/likes-cheese? doc)]]}]
```

The first element is a Sente event ID. The query map (the second element) omits
the entity variable in the where clauses since it has to be the same for each
clause anyway. But it will be bound to `doc` in case you want to use it in e.g.
a predicate function. `:find` is similarly omitted.

The `:table` value is connected to authorization rules which you define on the
backend (see [Rules](#rules)). When a client subscribes to this query, it will
be rejected unless you define rules for that table which allow the query. You
also have to whitelist any predicate function calls (like
`yourapp.core/likes-cheese?`), though the comparison operators (like `<=`) are
whitelisted for you.

I haven't yet added support for `or`, `not`, etc. clauses in subscriptions. See
<a href="https://github.com/jacobobryant/biff/issues/9" target="_blank">#9</a>.

You can also subscribe to individual documents:

```clojure
[:biff/sub '{:table :users
             :id {:user/id #uuid "some-uuid"}}]
```

All this is most powerful when you make the `subscriptions` atom a derivation of
`sub-data`:

<div class="file-heading"><a href="https://github.com/jacobobryant/biff/blob/master/example/src/hello/client/app.cljs" target="_blank">
src/hello/client/app.cljs</a></div>
```clojure
(ns hello.client.app
  (:require
    [biff.util :as bu]
    [hello.client.app.db :as db]
    [hello.client.app.mutations :as m]
    [hello.client.app.system :as s]
    ...))

...

(defn ^:export init []
  (reset! s/system
    (bu/init-sub {:handler m/api
                  :sub-data db/sub-data
                  :subscriptions db/subscriptions}))
  ...)
```

<div class="file-heading"><a href="https://github.com/jacobobryant/biff/blob/master/example/src/hello/client/app/db.cljs" target="_blank">
src/hello/client/app/db.cljs</a></div>
```clojure
(ns hello.client.app.db
  (:require
    [hello.logic :as logic]
    [trident.util :as u]
    [rum.core]))

(defonce db (atom {}))

; same as (do (rum.core/cursor-in db [:sub-data]) ...)
(u/defcursors db
  sub-data [:sub-data])

; same as (do
;           (rum.core/derived-atom [sub-data] :hello.client.app.db/data
;             (fn [sub-data]
;               (apply merge-with merge (vals sub-data))))
;           ...)
(u/defderivations [sub-data] hello.client.app.db
  data (apply merge-with merge (vals sub-data))

  uid (get-in data [:uid nil :uid])
  signed-in (and (some? uid) (not= :signed-out uid))
  user-ref {:user/id uid}
  game (->> data
         :games
         vals
         (filter #(contains? (:users %) uid))
         first)

  ...

  biff-subs [; :uid is a special non-Crux query. Biff will respond
             ; with the currently authenticated user's ID.
             :uid
             (when signed-in
               [{:table :users
                 :id user-ref}
                {:table :public-users
                 :id {:user.public/id uid}}
                {:table :games
                 :where [[:users uid]]}])
             (for [u (:users game)]
               {:table :public-users
                :id {:user.public/id u}})]
  subscriptions (->> biff-subs
                  flatten
                  (filter some?)
                  (map #(vector :biff/sub %))
                  set))
```

When a user signs into the example app, the following will happen:

1. Client subscribes to `:uid` (i.e. `subscriptions` contains `#{[:biff/sub
:uid]}`).
2. `sub-data` is populated with the user's ID.
3. `signed-in` changes to `true` and `biff-subs` gets updated. The client is now
   subscribed to various information about the current user, including the current
   game (if they've joined one).
4. `sub-data` is populated with more data. The UI will display the user's
   email address, display name and current game. The client will subscribe to data
   about the other players (their display names).
5. The other players' display names will be loaded into `sub-data` and the UI will
   update again.

This is what I meant when I said that we can go pretty far without cross-entity
joins: using this method, we can declaratively load all the relevant data and
perform joins on the client. This should be sufficient for many situations.

However, it won't work if you need an aggregation of a set of documents that's
too large to send to the client (not to mention each client), or if the client
isn't allowed to see the individual documents. To handle that, I'd like to
eventually try integrating <a href="https://materialize.io"
target="_blank">Materialize</a>.

# Rules

Relevant config:

```clojure
:biff/rules nil         ; An authorization rules data structure.
:biff/fn-whitelist nil  ; Collection of fully-qualified function symbols to allow in
                        ; Crux queries sent from the frontend. Functions in clojure.core
                        ; need not be qualified. For example: '[map? example.core/frobulate]
```

Your app's rules define what transactions and subscriptions will be accepted
from the frontend (see [Transactions](#transactions) and
[Subscriptions](#subscriptions)).

The value of `:biff/rules` is a map of `table->rules`, for example:

<div class="file-heading"><a href="https://github.com/jacobobryant/biff/blob/master/example/src/hello/rules.clj" target="_blank">src/hello/rules.clj</a></div>
```clojure
(ns hello.rules
  (:require
    [biff.util :as bu]
    [clojure.spec.alpha :as s]))

; Same as (do (s/def ...) ...)
(bu/sdefs
  :user/id uuid?
  ; like s/keys, but only allows specified keys.
  ::user-ref (bu/only-keys :req [:user/id])
  ::user (bu/only-keys :req [:user/email])
  ...)

(def rules
  {:users {:spec [::user-ref ::user]
           :get (fn [{:keys [session/uid] {:keys [user/id]} :doc}]
                  (= uid id))}
   ...})
```

### Tables

The table is used in transactions and subscriptions to specify which rules should be
used. The rules above authorize us to subscribe to this:

```clojure
[:biff/sub {:table :users
            :id {:user/id #uuid "some-uuid"}}]
```

And for transactions:

```clojure
{:games {:spec [::game-ref ::game]
         :create (fn [env] ...)}}
; Authorizes:
[:biff/tx {[:games {:game/id "ABCD"}]
           {:users #{#uuid "some-uuid"}}}]
```

### Specs

For each document in the query result or transaction, authorization has two
steps. First, the document ID and the document are checked with `s/valid?`
against the two elements in `:specs`, respectively. For example, the specs for
the `:users` table above would authorize a read or write operation on the
following document:

```clojure
{:crux.db/id {:user/id #uuid "some-uuid"}
 :user/id #uuid "some-uuid"
 :user/email "email@example.com"}
```

Note that during this check, the document will not include the ID or any keys
in the ID (for map IDs). (Also recall that map ID keys are automatically
duplicated in the document when using Biff transactions).

For write operations, the document must pass the spec before and/or after the
transaction, depending on whether the document is being created, updated or
deleted.

### Operations

If the specs pass, then the document must pass a predicate specified by the
operation. There are five operations: `:create`, `:update`, `:delete`,
`:query`, `:get`.

```clojure
{:messages {:specs ...
            :create (fn [env] ...)
            :get (fn [env] ...)}}
```

You can use the same predicate for multiple operations like so:

```clojure
{:messages {:specs ...
            [:create :update] (fn [env] ...)}}
```

There are also several aliases:

Alias | Expands to
------|-----------
`:read` | `[:query :get]`
`:write` | `[:create :update :delete]`
`:rw` | `[:query :get :create :update :delete]`

For example:

```clojure
{:messages {:specs ...
            :write (fn [env] ...)}}
```

`:get` refers to subscriptions for individual documents while `:query` is for
multiple documents:

```clojure
; get
[:biff/sub {:table :users
            :id {:user/id #uuid "some-uuid"}}]
; query
[:biff/sub {:table :games
            :where [[:users #uuid "some-uuid"]]}]
```

### Predicates

Predicates receive the system map merged with some additional keys, depending
on the operation:

Key | Operations | Description
----|------------|------------
`:session/uid` | `:rw` | The ID of the user who submitted the query/transaction. `nil` if they're unauthenticated.
`:biff/db` | `:rw` | The Crux DB value before this operation occurred.
`:doc` | `:rw` | The document being operated on.
`:old-doc` | `:write` | The previous value of the document being operated on.
`:current-time` | `:write` | The inst used to replace any occurrences of `:db/current-time` (see [Transactions](#transactions)).
`:generated-id` | `:create` | `true` iff a random UUID was generated for this document's ID.

Some examples:

<div class="file-heading"><a href="https://github.com/jacobobryant/biff/blob/master/example/src/hello/rules.clj" target="_blank">src/hello/rules.clj</a></div>
```clojure
(def rules
  {:public-users {:spec [::user-public-ref ::user-public]
                  ; Returns false iff :session/uid is nil.
                  :get bu/authenticated?
                  :write (fn [{:keys [session/uid] {:keys [user.public/id]} :doc}]
                           (= uid id))}
   :users {:spec [::user-ref ::user]
           :get (fn [{:keys [session/uid] {:keys [user/id]} :doc}]
                  (= uid id))}
   :games {:spec [::game-ref ::game]
           :query (fn [{:keys [session/uid] {:keys [users]} :doc}]
                    (contains? users uid))
           [:create :update] (fn [{:keys [session/uid doc old-doc]
                                   {:keys [users]} :doc}]
                               (and
                                 (some #(contains? (:users %) uid) [doc old-doc])
                                 ; Checks that no keys other than :users have changed
                                 ; (supports varargs).
                                 (bu/only-changed-keys? doc old-doc :users)
                                 ; Checks that the value of :users (a set) hasn't changed except
                                 ; for the addition/removal of uid (supports varargs).
                                 (bu/only-changed-elements? doc old-doc :users uid)))}})
```

```clojure
{:events {:spec [uuid? ::event]
          :create (fn [{:keys [session/uid current-time generated-id]
                        {:keys [timestamp user]} :doc}]
                    (and
                      (= uid (:user/id user))
                      ; Make sure that :timestamp was set by the server, not the client.
                      (= current-time timestamp)
                      ; Make sure that the ID was set by the server, not the client.
                      generated-id))}}
```

# Triggers

Relevant config:

```clojure
:biff/triggers nil ; A database triggers data structure.
```

Triggers let you run code in response to document writes. You must define a map of
`table->operation->fn`, for example:

<div class="file-heading"><a href="https://github.com/jacobobryant/biff/blob/master/example/src/hello/triggers.clj" target="_blank">src/hello/triggers.clj</a></div>
```clojure
(defn assign-players [{:keys [biff/node doc]
                       {:keys [users x o]} :doc :as env}]
  ; When a user joins or leaves a game, re-assign users to X and O as needed.
  ; Delete the game document if everyone has left.
  (let [new-doc ... ; Same as doc but maybe with different :x and :o values
        op (cond
             (empty? users) [:crux.tx/delete (:crux.db/id doc)]
             (not= doc new-doc) [:crux.tx/put new-doc])]
    (when op
      (crux/submit-tx node
        [[:crux.tx/match (some :crux.db/id [doc new-doc]) doc]
         op]))))

(def triggers
  {:games {[:create :update] assign-players}})
```

See [Tables](#tables) and [Operations](#operations). The function will receive the system
map merged with the following keys:

Key | Description
----|------------
`:doc` | The document that was written.
`:doc-before` | The document's value before being written.
`:db` | The Crux DB value after this operation occurred.
`:db-before` | The Crux DB value before this operation occurred.
`:op` | One of `#{:create :update :delete}`.

# Production

## Installation

First, create an Ubuntu droplet on DigitalOcean. Make sure you have SSH access. If you've
added your public cert to DigitalOcean already, this may be handled automatically.

The following script includes setting up LetsEncrypt. Before running it, you'll
need to point at the droplet any domain(s) you want to serve from Biff. The
script will ask for a list of the domains and will generate a certificate for
them.

Log in as root and run this:

```bash
git clone https://github.com/jacobobryant/biff
cd biff
./install.sh
reboot
```

`install.sh` will:

1. Install dependencies
2. Install Biff as a systemd service (i.e. autostart on boot)
3. Setup Nginx
4. Install certificates
5. Setup firewall

Currently it uses the root user to run Biff, but I'll fix that soon.

If you ever want to update the list of domains served by Biff, just run
something like the following:

```bash
certbot --nginx -d 'findka.com,jacobobryant.com'
```

I've only tested the install script on DigitalOcean, but it should work on
other providers with little to no tweaking.

## Deployment

1. Update `/root/biff/prod/config.edn` if needed (e.g. `scp config.edn
   root@example.com:biff/prod/`).
2. Commit any static resources you need to your project's repo (or add some code to
   download them from a CI server or something on startup). For example:

<div class="file-heading"><a href="https://github.com/jacobobryant/biff/blob/master/example/task" target="_blank">task</a></div>
```bash
APP_NS="hello"
CLJS_APPS="app"

release-cljs () {
  npx shadow-cljs release $CLJS_APPS
  for app in $CLJS_APPS; do
    mkdir -p resources/www/$APP_NS/cljs/$app
    cp {www-dev,resources/www/$APP_NS}/cljs/$app/main.js
  done
}
```

<ol start="3">
<li>On the first deploy, add your project to <code>/root/biff/prod/deps.edn</code>. On
   future deploys, update the <code>:sha</code> values in that file.</li>
<li>Restart Biff: <code>systemctl restart biff</code></li>
<li>Watch the logs: <code>journalctl -u biff -f</code>. Your app should be live after you see <code>System started.</code>.</li>
</ol>

If you want to deploy your app from a private repo, you'll need to add a deploy
certificate and configure Biff to add it to the keychain on startup. I'll add
automation and instructions after the first person tells me they want
to do this.
