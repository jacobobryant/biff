---
title: Biff

language_tabs: # must be one of https://git.io/vQNgJ
  - Clojure

toc_footers:

includes:

search: true
---

# Introduction

Biff is designed to make web development with Clojure as easy as possible
without compromising on simplicity. The main target audience is early stage
startups and hobbyists, where speed in the beginning really matters. Biff is
also made to be easy to take apart: as your project grows and your requirements
become more complex, you can peel back Biff's layers until you have the level
of flexibility you need.

Biff is still fairly young, and there may be breaking changes. I've been using
it in production for [Findka](https://findka.com) since May 2020. To help Biff
grow and to help me discover what needs improvement, I'm giving free one-on-one
mentoring (pair programming, code review, design help, etc) to anyone who wants
to learn Clojure web dev with Biff (as my schedule allows). If you're
interested, fill out [this quick survey](https://airtable.com/shrKqm1iT3UWySuxe).

Core features (a few of these were inspired by Firebase):

- **Query subscriptions**. Specify what data the frontend needs declaratively, and
  Biff will keep it up-to-date. Same level of query power as with Firebase.
- **Authentication**. Email link for now; password and SSO coming later.
- **Read/write authorization rules**. No need to set up a bunch of endpoints
  for CRUD operations. Queries and transactions can be submitted from the
  frontend as long as they pass the rules you define.
- **Database triggers**. Run code when documents of certain types are created,
  updated or deleted.
- **Crux**, an immutable document database with Datalog queries
  (see [opencrux.com](https://opencrux.com)).
- **No-hassle deployment** using Terraform and DigitalOcean (you can add config for other
  cloud providers if needed). Biff can run on a single $5/month server. Later
  I'll add config for high availability, CI/CD, etc.
- Project templates for **SPAs and MPAs**. If you don't need high interactivity,
  you can use server-side rendering instead of React and ClojureScript.
- **Great documentation!**

<p><iframe width="560" height="315" src="https://www.youtube.com/embed/mKqjJH3WiqI" frameborder="0" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture" allowfullscreen></iframe></p>

## Resources

 - Join `#biff` on [Clojurians Slack](http://clojurians.net) for
discussion. Feel free to reach out for help, bug reports or anything else.
 - See the issues and source on [Github](https://github.com/jacobobryant/biff).
 - Watch [a presentation](https://youtu.be/mKqjJH3WiqI) I gave at re:Clojure 2020 ([slides](https://jacobobryant.com/misc/reclojure-2020-jacobobryant.pdf)).
 - Watch [a workshop](https://youtu.be/tDp1l81fYSM) I gave at re:Clojure 2020
   ([code](https://github.com/jacobobryant/biff-workshop)).
 - Watch [a presentation](https://www.youtube.com/watch?v=oYwhrq8hDFo) I gave at the Clojure Mid-Cities meetup.
 - See the [FAQ](#faq) section for comparison to other frameworks.

## Contributing

The most helpful way to contribute is to use Biff and let me know what problems
you run into. You can also write tutorials or blog about your experience. I'd
be happy to list your articles under [Resources](#resources) and promote them
myself, not that I have a large following.

PRs are welcome too, especially if you want to tackle some of the [current
issues](https://github.com/jacobobryant/biff/issues). If you're planning
something significant, you might want to bring it up in `#biff` on Clojurians
Slack.

The easiest way to hack on Biff is to start a new project (see [Getting
Started](#getting-started)) and then change the Biff dependency in `deps.edn` to
`{:local/root "/path/to/cloned/biff/repo" ...}`. Then just run `./task init;
./task dev`. Eval `(biff.core/refresh)` as needed.

### Documentation

You'll need Ruby; then run:

```shell
cd slate
gem install bundler
bundle install
cd ..
```

After that, you can run `./task docs-dev` and edit `slate/source/index.html.md`
to work on the documentation. See the [Slate
README](https://github.com/jacobobryant/biff/tree/master/slate).

# Getting started

Requirements:

 - Linux, Mac or Windows Subsystem for Linux
 - [clj](https://clojure.org/guides/getting_started)
 - rlwrap (try `which rlwrap`)
 - npm
 - node v12.0.0+
 - Note: I've had mixed reports about using JDK 15. If it doesn't work, try 8 or 11.

Run this command to create a new Biff project:

```
bash <(curl -s https://raw.githubusercontent.com/jacobobryant/biff/master/new-project.sh)
```

That script will create a minimal, working CRUD app which demonstrates most of
Biff's features. You'll be guided through the process of starting the app,
trying it out, and exploring the code. You can refer back to the documentation
here as needed. When you're ready to deploy, check out
[Deployment](#deployment).

<p><iframe width="560" height="315" src="https://www.youtube.com/embed/tDp1l81fYSM?start=96" frameborder="0" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture" allowfullscreen></iframe></p>

# Overview

## Project structure

Here's an example of running the `new-project.sh` script above:

<div class="highlight">
<pre class="highlight">
<code>$ <span style="color:cyan">bash <(curl -s https://raw.githubusercontent.com/jacobobryant/biff/master/new-project.sh)</span>
Creating a new Biff project. Available project types:

  1. SPA (single-page application). Includes ClojureScript, React, and
     Biff's subscribable queries. Good for highly interactive applications.

  2. MPA (multi-page application). Uses server-side rendering instead of
     React etc. Good for simpler applications.

Choose a project type ([spa]/mpa): <span style="color:cyan">spa</span>
Creating a SPA project.
Fetching latest Biff version...
Enter name for project directory: <span style="color:cyan">example</span>
Enter main namespace (e.g. example.core): <span style="color:cyan">example.core</span>
Enter the domain you plan to use in production (e.g. example.com),
or leave blank to choose later: <span style="color:cyan">example.com</span>

Your project is ready. Run the following commands to get started:

  cd example
  git init
  ./task init
  ./task dev

Run `./task help` for more info.
$ <span style="color:cyan">cd example/</span>
$ <span style="color:cyan">tree</span>
.
├── all-tasks
│   ├── 10-biff
│   └── 20-example
├── config
│   ├── deploy-key
│   ├── main.edn
│   ├── ssh-public-key
│   └── task.env
├── deps.edn
├── infra
│   ├── provisioners
│   │   ├── 00-config
│   │   ├── 10-wait
│   │   ├── 20-dependencies
│   │   ├── 30-users
│   │   ├── 40-app
│   │   ├── 50-systemd
│   │   ├── 60-nginx
│   │   └── 70-firewall
│   ├── run-provisioners.sh
│   ├── system.tf.json
│   └── webserver.json
├── resources
│   └── www
│       └── js
│           ├── ensure-signed-in.js
│           └── ensure-signed-out.js
├── shadow-cljs.edn
├── src
│   └── example
│       ├── client
│       │   ├── app
│       │   │   ├── components.cljs
│       │   │   ├── db.cljs
│       │   │   ├── mutations.cljs
│       │   │   └── system.cljs
│       │   └── app.cljs
│       ├── core.clj
│       ├── handlers.clj
│       ├── jobs.clj
│       ├── routes.clj
│       ├── rules.clj
│       ├── static.clj
│       └── triggers.clj
├── tailwind.config.js
├── tailwind.css
└── task
</code>
</pre>
</div>

### Tasks

`all-tasks/` contains Bash scripts which define project tasks as functions. For example:

<div class="file-heading">all-tasks/10-biff</div>
```shell
init () {
  if [ -f package.json ]; then
    npm install
  else
  ...
}
```

You can run these functions with `./task <name of function>`.
`all-tasks/10-biff` contains tasks provided by Biff. You can define new tasks
in other files, such as `all-tasks/20-example`.

### Static resources

`./task dev` starts your app on `localhost:8080`. Your app will serve files
from `www/` and `www-dev/`, which are populated from several sources:

 - The contents of `resources/www/` are copied to `www/`.
 - HTML files are generated from `src/example/static.clj` and placed in `www/`.
 - Your CLJS code (under `src/example/client/`) is compiled to `www-dev/cljs/app/main.js`.
 - `tailwind.css` is compiled to `www-dev/css/main.css`.

In production, only the first two points apply. Before deploying, you'll use `./task build-assets`
to add your production CLJS and CSS to `resources/www/` so it can be checked into your git repository
and deployed from there. (Alternatively, you can download assets from a CI server. I'll make Biff do this
by default later.)

### Configuration

`config/main.edn` is read when your app starts. It contains configuration and
secrets. The contents of `config/` are git-ignored, but Terraform will copy
`config/main.edn` to production when you deploy. (Later Biff will use Hashicorp
Vault.)

<div class="file-heading">config/main.edn</div>
```clojure
{:prod {; Standalone topology is only recommended for development.
        :biff.crux/topology :standalone
        ; Uncomment to use jdbc in production:
        ;:biff.crux/topology :jdbc
        ;:biff.crux.jdbc/dbname "..."
        ;:biff.crux.jdbc/user "..."
        ;:biff.crux.jdbc/password "..."
        ;:biff.crux.jdbc/host "..."
        ;:biff.crux.jdbc/port ...
        :biff/host "example.com"}
 :dev {:inherit [:prod]
       :biff/dev true}}
```

`config/task.env` contains configuration needed by `./task`.
`config/deploy-key` and `config/ssh-public-key` are needed for deployment.
You'll need to update all of these files before deploying.

### Infrastructure

Before deploying, you'll create a VM image (via Packer) with `./task
build-image`. That task will read from `infra/webserver.json`, and it will run
the scripts under `infra/provisioners/`. If you need to customize the image,
you can add more scripts and re-run `./task build-image`.

After that, you can create a server and deploy your app (via Terraform) with
`./task tf apply`. You'll need to commit and push first. When the server
starts, it will fetch the latest commit from your git repository and run your
app from that.

You can use `./task deploy` instead of `./task tf apply` for subsequent
deploys, as long as you haven't made any infrastructure changes. `./task
deploy` will simply restart the app process on the server, causing it to fetch
the latest commit again.

## App entrypoint

Your app is started by running the `-main` function from your app's main namespace, e.g.
`example.core/-main`.

<div class="file-heading">src/example/core.clj</div>
```clojure
(defn start [first-start]
  (let [sys (biff.core/start-system
              {:biff/first-start first-start
               ...}
              biff.core/default-spa-components)]
    (when (:biff/dev sys)
      (biff.project/update-spa-files sys))
    (println "System started.")))

(defn -main []
  (start true))
```

Some of the files discussed in the previous section are managed by Biff
(specifically, `all-tasks/10-biff` and everything under `infra/`). When your
app starts, the `biff.project/update-spa-files` will write to those files. This
means that when you update Biff (by changing the `:sha` value in `deps.edn`),
those non-Clojure files will also get updated. You shouldn't change any of
those files by hand, because your changes will get overwritten.

`biff.core/start-system` takes a system map and passes it through a number of
component functions. It's kind of like passing a Ring request through
middleware functions. The system map includes all the configuration values,
using flat, namespaced keys. It also includes any resources or values that
components choose to pass on.

Biff's default components do the following:

- Read `config/main.edn`.
- Start an nrepl server.
- (In dev) start Shadow CLJS.
- Start a Crux node.
- Start a Crux transaction listener, which notifies clients when data they've
  subscribed to has changed. It also runs database triggers.
- Listen for web socket connections (via sente).
- Set up web socket event handlers, including front-end query and transaction handlers.
- Set up HTTP routes (via Reitit), including routes for authentication.
- Start a web server (Jetty).
- Populate `www/` with static resources.
- Schedule recurring jobs.

Each component function receives the system map and then returns a modified version. For example,
here's the component which starts Jetty:

<div class="file-heading">biff.components</div>
```clojure
(defn start-web-server [{:biff.web/keys [handler host port] :as sys}]
  (let [server (jetty/run-jetty handler
                 {:host host
                  :port port
                  :join? false
                  :websockets {"/api/chsk" handler}
                  :allow-null-path-info true})]
    (update sys :biff/stop conj #(jetty/stop-server server))))
```

When all components have finished, the result is stored in `biff.core/system`
(an atom). During development, you can reload the system by calling
`(biff.core/refresh)` (I recommend binding an editor shortcut to that).
That will call all the functions in `:biff/stop`, reload Clojure files with
tools.deps.namespace.repl, and then restart your app with
`biff.core/start-system`.

## Decomposing

To a degree, you can modify the behavior of Biff by passing in certain
configuration values. When you need more flexibility, you can decompose Biff.
For example, you can replace `default-spa-components` like so:

<div class="file-heading">src/example/core.clj</div>
```clojure
(require '[biff.components :as c])

(defn start [first-start]
  (let [sys (biff.core/start-system {...}
              ; Add or remove components as needed.
              [c/init
               c/set-defaults
               c/start-crux
               c/start-sente
               c/start-tx-listener
               c/start-event-router
               c/set-auth-route
               c/set-http-handler
               c/start-web-server
               c/write-static-resources
               c/start-jobs
               c/print-spa-help])]
    ...))
```

And you can replace `biff.project/update-spa-files` with its body:

```clojure
(require '[biff.project.infra :as infra])

(defn start [first-start]
  (let [sys (biff.core/start-system {...}
              ...)]
    (when (:biff/dev sys)
      (let [opts (assoc sys ...)]
          (biff.project/copy-files "biff/project/base/{{dir}}/"
            (assoc opts
              :files #{"all-tasks/10-biff"
                       "infra/provisioners/10-wait"
                       "infra/provisioners/20-dependencies"
                       "infra/provisioners/30-users"
                       "infra/provisioners/40-app"
                       "infra/provisioners/50-systemd"
                       "infra/provisioners/60-nginx"
                       "infra/provisioners/70-firewall"
                       "infra/run-provisioners.sh"}))
          (spit "infra/webserver.json"
            (cheshire/generate-string
              infra/default-packer-config {:pretty true}))
          (spit "infra/system.tf.json"
            (cheshire/generate-string
              (infra/default-terraform-config opts) {:pretty true}))))
    (println "System started.")))
```

This should give you the flexibility you need.

<hr>

The rest of this documentation covers Biff's individual features in-depth. The
fastest way to learn Biff is probably to create a new project and then
experiment. You can refer back here when you need more information.

Here's a demonstration of adding a feature to a Biff application
([short version](https://github.com/jacobobryant/biff-workshop/commit/76a76d5f774c29785e4d22e1741ec4fb491ae819)):

<p><iframe width="560" height="315" src="https://www.youtube.com/embed/tDp1l81fYSM?start=1808" frameborder="0" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture" allowfullscreen></iframe></p>

# Configuration

Configuration can be set in code (by passing it in to `biff.core/start-system`) and
in `config/main.edn`. When Biff reads in `config/main.edn`, it will merge the
nested maps according to the current environment and the value of `:inherit`.
The result is merged into the system map.

The default environment is `:prod`. This can be overridden by setting the
`BIFF_ENV` environment variable:

```shell
BIFF_ENV=dev clj -M -m example.core
```

So this:

```clojure
{:prod {:foo 1
        :bar 2}
 :dev {:inherit [:prod]
       :foo 3}}
```

would become this:

```clojure
{:foo 3
 :bar 2}
```

Here is a complete list of configuration options and their default values. See
the following sections for a deeper explanation.

```clojure
:biff/host "localhost"  ; The hostname this app will be served on, e.g. "example.com" for prod or
                        ; "localhost" for dev.
:biff/rules nil         ; An authorization rules data structure. To allow late binding, this can
                        ; optionally be a var or a 0-arg function.
:biff/triggers nil      ; A database triggers data structure. As with :biff/rules, this can
                        ; optionally be a var or a function.
:biff/send-email nil    ; A function which receives the system map merged with the following
                        ; keys: :to, :template, :data. Used for sending sign-in emails.
:biff/static-pages nil  ; A map from paths to Rum data structures, e.g.
                        ; {"/hello/" [:html [:body [:p {:style {:color "red"}} "hello"]]]}
:biff/fn-whitelist nil  ; Collection of fully-qualified function symbols to allow in
                        ; Crux queries sent from the frontend. Functions in clojure.core
                        ; need not be qualified. For example: '[map? example.core/frobulate]
:biff/routes nil        ; A vector of Reitit routes.
:biff/event-handler nil ; A Sente event handler function.
:biff/jobs nil          ; A vector of job data structures to schedule.
:biff/after-refresh     ; A fully-qualified symbol that specifies a function for biff.core/refresh
                        ; to call.

:biff.init/start-nrepl true
:biff.init/start-shadow false

:biff.auth/on-signup "/signin-sent" ; A redirect route.
:biff.auth/on-signin-request "/signin-sent"
:biff.auth/on-signin-fail "/signin-fail"
:biff.auth/on-signin "/app"
:biff.auth/on-signout "/"

:biff.crux/topology :standalone ; One of #{:jdbc :standalone}
; Ignored if :biff.crux/topology isn't :jdbc.
:biff.crux.jdbc/dbname nil
:biff.crux.jdbc/user nil
:biff.crux.jdbc/password nil
:biff.crux.jdbc/host nil
:biff.crux.jdbc/port nil

:biff.http/not-found-path "/404.html"
:biff.http/spa-path "/app/index.html" ; If set, takes precedence over :biff.http/not-found-path and
                                      ; sets http status to 200 instead of 404, unless the
                                      ; requested file path is prefixed by one of
                                      ; :biff.http/asset-paths.
:biff.http/asset-paths #{"/cljs/" "/js/" "/css/"} ; See :biff.http/spa-path.
:biff.http/secure-defaults true ; Whether to use ring.middleware.defaults/secure-site-defaults
                                ; or just site-defaults.

:biff.web/host "localhost" ; Host that the web server will listen on. localhost is used in
                           ; production because requests are reverse-proxied through nginx.
:biff.web/port 8080        ; Port that the web server will listen on.

:biff/dev false ; When true, changes the defaults for the following keys:
                :biff.init/start-shadow true
                :biff.init/start-nrepl false ; shadow-cljs has its own nrepl server.
                ; Also overrides values for these keys:
                :biff/host "localhost"
                :biff.crux/topology :standalone
                :biff.http/secure-defaults false
                :biff.web/host "0.0.0.0"
```

The following keys are added to the system map:

 - `:biff/base-url`: e.g. `"https://example.com"` or `"http://localhost:8080"`
 - `:biff/node`: the Crux node.
 - `:biff/send-event`: the value of `:send-fn` returned by `taoensso.sente/make-channel-socket!`.
 - `:biff.sente/connected-uids`: Ditto but for `:connected-uids`.
 - `:biff.crux/subscriptions`: An atom used to keep track of which clients have subscribed
   to which queries.

Biff merges the system map into incoming Ring requests and Sente events. It
also adds `:biff/db` (a Crux DB value) on each new request/event.

# Static resources

See [Overview > Static resources](#static-resources).

Relevant config:

```clojure
:biff/static-pages nil  ; A map from paths to Rum data structures, e.g.
                        ; {"/hello/" [:html [:body [:p "hello"]]]}
```

As mentioned, Biff serves your static resources from `www/`. In production,
`www/` is a symlink to `/var/www/` and is served directly by Nginx (so static
files will be served even if your JVM process goes down, e.g. during
deployment).

Here's a larger example for `:biff/static-pages`:

```clojure
(ns example.static
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
    [:#app {:style {:font-weight "bold"}} "Loading..."]
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

# Authentication

Relevant config:

```clojure
:biff/send-email nil ; A function which receives the system map merged with the following keys:
                     ; :to, :template, :data. Used for sending sign-in emails.
:biff.auth/on-signup "/signin-sent" ; A redirect route.
:biff.auth/on-signin-request "/signin-sent"
:biff.auth/on-signin-fail "/signin-fail"
:biff.auth/on-signin "/app"
:biff.auth/on-signout "/"
```

Biff currently provides email link authentication. The user clicks a link
(which contains a JWT) in an email to sign in, and then Biff stores their user
ID in an encrypted cookie. Password and SSO authentication are on the roadmap.

After a user is signed in, you can authenticate them on the backend from an
event/request handler like so:

```clojure
(require '[ring.middleware.anti-forgery :refer [wrap-anti-forgery]])

(defn handler [{:keys [session/uid biff/db]}]
  (if (some? uid)
    (do
      (prn (crux.api/entity db {:user/id uid}))
      ; => {:crux.db/id {:user/id #uuid "..."}
      ;     :user/id #uuid "..." ; duplicated for query convenience
      ;     :user/email "alice@example.com"}
      {:body "Hello, authenticated user."
       :headers/Content-Type "text/plain"})
    (do
      (println "User not authenticated.")
      ; Redirect the user to the login page
      {:status 302
       :headers/Location "/login"}
      ; If this is an API endpoint, you can just return a 403:
      ; {:status 403
      ;  :body "Forbidden."
      ;  :headers/Content-Type "text/plain"}
      )))

(def routes
  [["/foo" {:post handler
            :name ::foo
            ; You must include this for any endpoint which uses :session/uid.
            :middleware [wrap-anti-forgery]}]
   ...])

```

Biff provides a set of HTTP endpoints for authentication:

## Sign up

Sends the user an email with a sign-in link. The token included in the link
will expire after 24 hours. Redirects to the value of `:biff.auth/on-signup`.

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
(send-email (merge
              ring-request
              {:to "alice@example.com"
               :template :biff.auth/signup
               :data {:biff.auth/link "https://example.com/api/signin?token=..."}}))
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

(defn send-email [{:keys [to text template data mailgun/api-key] :as sys}]
  (if (some? template)
    (if-some [template-fn (get templates template)]
      (send-email* api-key
        (assoc (template-fn (assoc data :to to)) :to to))
      (println "Email template not found:" template))
    (send-email* api-key (select-keys sys [:to :subject :text :html]))))
```

### Dealing with bots

The above example is susceptible to abuse from bots. An account isn't created
until the user clicks the confirmation link, but it's better not to send emails
to bots/spam victims in the first place. You'll need to use your own method for
deciding if signups come from bots (I use recaptcha v3). The map passed to
`send-email` includes the Ring request specifically so you can check the form
parameters and make that decision.

If you render the login form with JS, you may not need to deal with this for a
while. If you render it statically (like in the example app), you'll have to
deal with it sooner.

## Request sign-in

Sends the user an email with a sign-in link. This is the same as [Sign up](#sign-up),
except:

 - The endpoint is `/api/signin-request`
 - The template key will be set to `:biff.auth/signin`
 - The user will be redirected to the value of `:biff.auth/on-signin-request`

## Sign in

Verifies the given JWT and adds a `:uid` key to the user's session (expires in
90 days). Also sets a `:csrf` cookie, the value of which
must be included in the `X-CSRF-Token` header for any HTTP requests that
require authentication.

If this is the first sign in, creates a user document in Crux with the email
and a random user ID (a UUID). For example:

```clojure
{:crux.db/id {:user/id #uuid "some-uuid"}
 :user/id #uuid "some-uuid" ; duplicated for query convenience.
 :user/email "abc@example.com"}
```

Redirects to the value of `:biff.auth/on-signin` (or
`:biff.auth/on-signin-fail` if the token is incorrect or expired).

This endpoint is used by the link generated by [Sign up](#sign-up) and [Request
sign-in](#request-sign-in), so you typically won't need to generate a link for
this endpoint yourself.

### HTTP Request

`GET /api/signin`

### URL Parameters

Parameter | Description
----------|------------
token | A JWT


## Sign out

Clears the user's session and `:csrf` cookie. Redirects to the value of
`:biff.auth/on-signout`.

### HTTP Request

`GET /api/signout`

### Example Usage

```clojure
[:a {:href "/api/signout"} "sign out"]
```

## Check if signed in

Returns status 200 if the user is authenticated, else 403.

### HTTP Request

`GET /api/signed-in`

### Example Usage

Include this on your landing page:

```javascript
fetch("/api/signed-in").then(response => {
  if (response.status == 200) {
    document.location = "/app";
  }
});
```

Include this on your app page:

```javascript
fetch("/api/signed-in").then(response => {
  if (response.status != 200) {
    document.location = "/";
  }
});
```

# HTTP routes

Relevant config:

```clojure
:biff/routes nil ; A vector of Reitit routes.
:biff.http/not-found-path "/404.html"
:biff.http/spa-path "/app/index.html" ; If set, takes precedence over :biff.http/not-found-path and
                                      ; sets http status to 200 instead of 404, unless the
                                      ; requested file path is prefixed by one of
                                      ; :biff.http/asset-paths.
:biff.http/asset-paths #{"/cljs/" "/js/" "/css/"} ; See :biff.http/spa-path.
:biff.http/secure-defaults true ; Whether to use ring.middleware.defaults/secure-site-defaults
                                ; or just site-defaults.
:biff/dev false ; When true, overrides values for these keys:
                :biff.http/secure-defaults false
                ...
```

The value of `:biff/routes` will be wrapped with some default middleware which, among other things:

 - Applies a modified version of `ring.middleware.defaults/secure-site-defaults` (or `site-defaults`).
 - Does format negotiation with [Muuntaja](https://github.com/metosin/muuntaja).
 - Merges the system map into the request (so you can access configuration and other things).
 - Sets `:biff/db` to a current Crux db value.
 - Flattens the `:session` and `:params` maps (so you can do e.g. `(:session/uid request)` instead
   of `(:uid (:session request))`).
 - Sets default values of `{:body "" :status 200}` for responses.
 - Nests any `:headers/*` or `:cookies/*` keys (so `:headers/Content-Type "text/plain"` expands
   to `:headers {"Content-Type" "text/plain"}`).

```clojure
(ns example.routes
  (:require
    [biff.util :as bu]
    ...))

(defn echo [req]
  {:headers/Content-Type "application/edn"
   :body (prn-str
           (merge
             (select-keys req [:params :body-params])
             (u/select-ns req 'params)))})

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
{:params {:foo "1"}, :params/foo "1", :body-params {:foo "1"}}
```

Endpoints that require authentication must be wrapped in anti-forgery
middleware. See [Authentication](#authentication). When POSTing to such
endpoints, you must include the value of the `csrf` cookie in the
`X-CSRF-Token` header:

```clojure
(cljs-http.client/post "/foo" {:headers {"X-CSRF-Token" (biff.client/csrf)}})
```

For SPA apps, you can usually communicate over web sockets instead.

# Web sockets

Relevant config:

```clojure
:biff/event-handler nil ; A Sente event handler function.
```

Example:

```clojure
(defmulti api :id)

(defmethod api :default
  [{:keys [id]} _]
  (biff.util/anom :not-found (str "No method for " id)))

(defmethod api :example/do-something
  [{:keys [biff/db session/uid] :as sys} {:keys [foo bar]}]
  ...)

(defmethod api :example/echo
  [{:keys [client-id biff/send-event]} arg]
  (send-event client-id [:example/print ":example/echo called"])
  ; arg will be sent to the client. If you don't want to return anything,
  ; return nil explicitly.
  arg)

(def event-handler #(api % (:?data %)))
```

Biff provides a helper function for initializing the web socket connection on the frontend:

```clojure
(defonce system (atom {}))

(defmulti api (comp first :?data))

(defmethod api :default
  [{[event-id] :?data} data]
  (println "unhandled event:" event-id))

(defmethod api :biff/error
  [_ anom]
  (pprint anom))

(defmethod api :example/print
  [_ arg]
  (prn arg))

(defn api-send [& args]
  (apply (:api-send @system) args))

(defn ^:export init []
  (reset! system
    (biff.client/init-sub {:handler api
                           ...}))
  ...)

(comment
  (go
    (<! (api-send [:example/echo {:foo "bar"}]))
    ; => {:foo "bar"}
    ; => ":example/echo called"
    ))
```

# Transactions

You can send arbitrary transactions from the frontend. They will be submitted
only if they pass certain authorization rules which you define (see
[Rules](#rules)). Transactions look like this:

```clojure
(defn set-display-name [display-name]
  (api-send
    [:biff/tx
     {[:users {:user/id @db/uid}]
      {:db/update true
       :display-name display-name}}]))
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
nested vectors instead of a map.

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
keys in the document ID will be duplicated in the document itself. This
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

## Transactions on the back end

On the back end, you can use `biff.crux/submit-tx`:

```clojure
(biff.crux/submit-tx sys
  {[:users {:user/id #uuid "some-uuid"}]
   {:db/update true
    :display-name "alice"}})
```

This will bypass the write authorization functions defined in `:biff/rules`,
but it will throw an exception if any documents don't conform to the specs for
their respective tables. For example, if the value for `:user/id` above is
correct, the transaction above would succeed given these rules:

```clojure
(require '[biff.util :as bu])

(bu/sdefs
  :user/id uuid?
  :user/email string?
  ::display-name string?
  :ref/user (bu/only-keys :req [:user/id])
  ::user (bu/only-keys
           :req [:user/email]
           :opt-un [::display-name]))

(def rules
  {:users {:specs [:ref/user ::user]
           :write (constantly false)}})
```

But if the `:user/id` value was incorrect (and thus refers to a non-existent
user), the transaction would fail. It would also fail if you set `:display-name
123` or `:display-name nil` instead of `:display-name "alice"` in the transaction.

You can also use bypass Biff's transactions and use Crux's API directly:

```clojure
(let [{:keys [biff/node biff/db]} sys
      doc-id {:user/id #uuid "some-uuid"}
      user (crux.api/entity db doc-id)]
  (crux.api/submit-tx node
    [[:crux.tx/put (merge user
                     {:crux.db/id doc-id
                      :user/id #uuid "some-uuid"
                      :display-name "alice"})]]))
```

But if you do this, Biff won't be able to check the transaction against your
specs.

# Queries

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

On the frontend, use `biff.client/init-sub` to initialize a websocket connection
that handles query subscriptions for you:

```clojure
(def default-subscriptions
  #{[:biff/sub {:table :users
                :args {'name "Ben"}
                :where '[[:name name]
                         [:age age]
                         [(<= 18 age)]
                         [(yourapp.core/likes-cheese? doc)]]}]})

(def subscriptions (atom default-subscriptions))
(def sub-results (atom {}))

(biff.client/init-sub
  {:subscriptions subscriptions
   :sub-results sub-results
   ...})
```


If you want to subscribe to a query, `swap!` it into `subscriptions`. If you
want to unsubscribe, `swap!` it out. Biff will populate `sub-results` with the
results of your queries and remove old data when you unsubscribe. You can then
use the contents of that atom to drive your UI. The contents of `sub-results` is a
map of the form `subscription->table->id->doc`, for example:

```clojure
{[:biff/sub '{:table :users
              :where ...}]
 {:users
  {{:user/id #uuid "some-uuid"} {:name "Sven"
                                 :age 250
                                 ...}}}}
```

Note the subscription format again:

```clojure
[:biff/sub {:table :users
            :args {'name "Ben"}
            :where '[[:name name]
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
[#9](https://github.com/jacobobryant/biff/issues/9).

You can also subscribe to individual documents:

```clojure
[:biff/sub '{:table :users
             :id {:user/id #uuid "some-uuid"}}]
```

All this is most powerful when you make the `subscriptions` atom a derivation of
`sub-results`:


```clojure
(ns example.client.app.db
  (:require
    [biff.rum :as br]))

(br/defatoms
  sub-results {}
  message-cutoff (js/Date.)
  route {})

; defderivations lets you use rum.core/derived-atom without the boilerplate.
(br/defderivations
  ; data is an atom that contains a map of table->id->doc. It will be updated
  ; whenever sub-results changes.
  data (apply merge-with merge (vals @sub-results))

  uid (get-in @data [:uid nil :uid])
  user (get-in @data [:users {:user/id @uid}])
  email (:user/email @user)
  foo (:foo @user)
  bar (:bar @user)
  messages (->> @data
             :messages
             vals
             (sort-by :timestamp #(compare %2 %1)))

  tab (get-in @route [:data :name] :crud)

  subscriptions (disj #{[:biff/sub :uid]
                        [:biff/sub {:table :messages
                                    :args {'t0 @message-cutoff}
                                    :where '[[:timestamp t]
                                             [(< t0 t)]]}]
                        (when @uid
                          [:biff/sub {:table :users
                                      :id {:user/id @uid}}])}
                  nil))
```


When a user signs into this app, they will subscribe to their user ID
(`[:biff/sub :uid]`, a special subscription) and any messages that are sent
after the page loaded. When the user's ID is received from the back end and
loaded into `sub-results`, it will cause `subscriptions` to update. The client
will then subscribe to the document for the current user. `subscriptions`
will also be updated if `message-cutoff` changes.

This is what I meant when I said that we can go pretty far without cross-entity
joins: using this method, we can declaratively load all the relevant data and
perform joins on the client. This should be sufficient for many situations.

However, it won't work if you need an aggregation of a set of documents that's
too large to send to the client (not to mention each client), or if the client
isn't allowed to see the individual documents. There will also be increased
latency since you have to wait for a network hop between joins.

To remedy that, I was previously working on a
[Materialize](https://materialize.io) integration, though it's no longer a
priority for me at the moment.

## Queries on the back end

You can use Crux's API:

```clojure
(let [{:keys [biff/db]} sys]
  (crux.api/q db
    {:find '[user]
     :full-results? true
     :args [{'name "Ben"}]
     :where '[[user :name name]
              ...]}))
```

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

```clojure
(ns example.rules
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

If the specs pass, then the document must also pass a predicate specified by
the operation. There are five operations: `:create`, `:update`, `:delete`,
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

There are several aliases:

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
`:doc-before` | `:write` | The previous value of the document being operated on.
`:current-time` | `:write` | The inst used to replace any occurrences of `:db/current-time` (see [Transactions](#transactions)).
`:generated-id` | `:create` | `true` iff a random UUID was generated for this document's ID.

Some examples:

```clojure
(def rules
  {:public-users {:spec [::user-public-ref ::user-public]
                  ; Returns false iff :session/uid is nil.
                  :get biff.rules/authenticated?
                  :write (fn [{:keys [session/uid] {:keys [user.public/id]} :doc}]
                           (= uid id))}
   :users {:spec [::user-ref ::user]
           :get (fn [{:keys [session/uid] {:keys [user/id]} :doc}]
                  (= uid id))}
   :games {:spec [::game-ref ::game]
           :query (fn [{:keys [session/uid] {:keys [users]} :doc}]
                    (contains? users uid))
           [:create :update] (fn [{:keys [session/uid doc doc-before]
                                   {:keys [users]} :doc}]
                               (and
                                 (some #(contains? (:users %) uid) [doc doc-before])
                                 ; Checks that no keys other than :users have changed
                                 ; (supports varargs).
                                 (biff.rules/only-changed-keys? doc doc-before :users)
                                 ; Checks that the value of :users (a set) hasn't changed except
                                 ; for the addition/removal of uid (supports varargs).
                                 (biff.rules/only-changed-elements? doc doc-before :users uid)))}})
```

```clojure
(def rules
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

# Jobs

Relevant config:

```clojure
:biff/jobs []
```

Each element of `:biff/jobs` is a map with three keys. For example:

```clojure
(defn some-job [sys]
  (println "This function will run every 2 minutes,")
  (println "beginning 1 minute after your app starts."))

(def jobs
  [{:offset-minutes 1
    :period-minutes 2
    :job-fn #'some-job}])
```

# Deployment

See [Overview > Infrastructure](#infrastructure).

<p><iframe width="560" height="315" src="https://www.youtube.com/embed/tDp1l81fYSM?start=568" frameborder="0" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture" allowfullscreen></iframe></p>

**1. Set up DigitalOcean**

Biff comes with Terraform config for DigitalOcean. You can write your own
config if you want to use a different provider (see [Overview >
Decomposing](#decomposing)), but for now I'll assume you're using DigitalOcean.
If you don't already have an account, you can sign up with [my referral
link](https://m.do.co/c/141610534c91) which will give you $100 of credit for 60
days (and $25 for me if you stick with them).

You'll also need a domain that [points to DigitalOcean's
nameservers](https://www.digitalocean.com/community/tutorials/how-to-point-to-digitalocean-nameservers-from-common-domain-registrars).

**2. Update config**

In `config/main.edn`, make sure `:biff/host` is set to the domain you want to
use for your production app (e.g. `myapp.example.com`). If you've changed
this since creating your Biff project, run `./task dev` (or
`(biff.core/refresh)`) to make sure the Terraform config file
(`infra/system.tf.json`) is up-to-date.

In `config/task.env`, update the following environment variables:

 - `DIGITALOCEAN_API_KEY`
 - `HOST` (should be the same as `:biff/host`)
 - `LETSENCRYPT_EMAIL`
 - `GIT_URL`

Put your personal SSH public key in `config/ssh-public-key`. For example:
`cp ~/.ssh/id_rsa.pub config/ssh-public-key`. This will let Terraform (and you)
run commands on the server after it's provisioned.

Run `./task generate-deploy-key`. This will write a new SSH private key to
`config/deploy-key`, which will let the server download your code from git
(assuming you're using a private repo. If not, you can ignore this step). The
public key will be in `config/deploy-key.pub`. You'll need to give that key
read access to your git repo. If you're using Github, you can do this at
`https://github.com/your_username/your_repo/settings/keys` -> `Add deploy key`.

**3. Create an image**

Run `./task build-image`. It'll take 3-5 minutes. Some of the output will be
red; this is (probably) OK. If successful, the command will write the new image
ID to `config/task.env`, for example:

```shell
$ grep IMAGE_ID config/task.env
export IMAGE_ID=12345 # Managed by Biff.
```

**4. Update repo**

Build your CSS and ClojureScript for production with `./task build-assets`.
They'll be written to `resources/www/css/main.css` and
`resources/www/cljs/app/main.js`. Commit those files (and all other changes) to
your repo and push. Whenever your app starts on the server, it will fetch the
latest commit from your repo and run that.

**5. Deploy with Terraform**

If you've already added your domain to DigitalOcean (i.e. it shows up under
[Networking > Domains](https://cloud.digitalocean.com/networking/domains)),
you'll need to import it into Terraform. For example, if your app's domain is
`foo.example.com`, then you'll need to run `./task tf import
digitalocean_domain.default example.com`.

You might also need to do the following before proceeding:

 - Run `eval $(ssh-agent); ssh-add`.
 - Add your personal SSH public key to the DigitalOcean console. Go to
 [Settings > Security](https://cloud.digitalocean.com/account/security),
 click "Add SSH Key", then paste in the contents of `config/ssh-public-key`.

Run `./task tf apply`. Terraform will show you the changes to
be made, and it'll ask for confirmation before it does anything. After the
command finishes, watch the logs with `./task logs`. You should eventually see
`System started.` Once you do, your app is live!

**6. Future deploys**

For future deploys, simply push the changes to your repo and then run `./task
deploy`. This will restart your app's process on the server, which will cause
it to re-fetch the latest commit.

If you make any infrastructure changes, you can re-run `./task tf apply`.
If you made image changes, re-run `./task build-image` first.

**7. Cleanup**

You can remove the resources provisioned by Terraform with `./task tf destroy`.
However, that will also remove the domain from DigitalOcean which you may not
want. Instead, you can delete resources manually from the DigitalOcean web
console. While you're there, you can delete the image(s) you created (these
won't be deleted by `./task tf destroy`).

# FAQ

## Comparison to Firebase

Basically, if you like Firebase and you like Clojure backend dev, you might
enjoy using Biff for your next side project. Same if you like the idea of
Firebase but in practice you have issues with it. If you want something mature
or you like having a Node/ClojureScript backend, Firebase is a great choice. [Here's a non-trivial
example](https://github.com/jacobobryant/mystery-cows) of using Firebase with ClojureScript.

Some shared features:

 - Flexible data modeling
 - Basic query subscriptions (no joins)
 - Client-side transactions
 - Authorization rules
 - Triggers
 - Authentication built-in

Some differences:

 - Biff has a long-running JVM/Clojure backend instead of an ephemeral
   Node/ClojureScript backend => better library ecosystem IMO and lower response
   times/no cold start.
 - Firebase has way more features and is vastly more mature.
 - Biff is open-source + self-hosted => you have total control. If there's anything you don't like, you can fix it.
 - [Crux](https://opencrux.com/) (the database Biff uses) is immutable and has Datalog queries.
 - Authorization rules in Firebase are IMO error-prone and hard to debug.
 - Firebase supports password and SSO authentication.

## Comparison to Fulcro

Similarities:

 - Both contain some code for moving data between frontend and backend, hence
   they can both be described as "full-stack frameworks."

Differences:

 - Fulcro is primarily a front-end framework while Biff is primarily backend.
 - Biff prioritizes the low end of the "market" (early-stage startups and hobby
   projects, as mentioned).
 - Biff is much smaller and younger.
 - Biff's scope includes devops.


## Why Crux and not Datomic?

Short answer: Like Vim, Arch Linux, and Clojure itself, Crux is one of those
pieces of software that sparks joy.

I used Datomic pretty heavily in my own projects for about a year prior to
switching to Firestore and then Crux. My opinion on Datomic vs. Crux is that
Datomic is more powerful and maybe can scale better, but Crux is  easier to get
started with and has a lot less operational overhead for small projects (in
terms of developer time). I've had many headaches from my time using Datomic
([and AWS](https://jacobobryant.com/post/2019/aws-battles-ep-1/), which Datomic
Cloud is coupled to). On the other hand, using Crux has been smooth&mdash;and
you can use DigitalOcean instead of AWS (yay). Since Biff prioritizes the
solo-developer / early-stage / rapid-prototyping use-case, I think Crux is a
much better fit. Whereas if I was in a situation with many
developers/delivering an application that I knew would have scale once
released, Datomic Cloud Ions would be worth considering (but even then, I
personally would probably stick with Crux&mdash;I just love Crux).

Off the top of my head, a few more reasons:

 - The document model is easier to reason about than the datom model. Building
   Biff on Datomic would have been more complex.

 - I like that Crux doesn't enforce schema, which made it easy for Biff to use
   it's own schema (i.e. rules). I also think it's better for rapid-prototyping,
   when you're still figuring out the schema and it changes often.

 - Crux is open-source. I'm a pragmatist and I don't mind using a closed source
   DB like Datomic in an app. But for Biff, a web framework intended for other
   people to build their apps on too, I'd rather not have a hard dependency on
   something closed-source. It'd suck if a feature broke in Datomic that was
   critical for Biff but low-priority for Cognitect. (I had a small budgeting
   app on Datomic that was down for several months because of that).

 - For hobby projects, you can run Crux on DigitalOcean with filesystem
   persistence for $5/month, whereas Datomic Cloud starts at $30/month. Doesn't
   matter for a startup of course, but I wouldn't want to be shelling out
   $30/month forever just to keep that budgeting app running.
