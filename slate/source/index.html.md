---
title: Biff

language_tabs: # must be one of https://git.io/vQNgJ
  - Clojure

toc_footers:
  - <a href="https://github.com/jacobobryant/biff" target="_blank">View on Github</a>
  - <a href="http://clojurians.net" target="_blank">Discuss on &#35;biff</a>
  - <a href="https://findka.com/subscribe/" target="_blank">Subscribe for updates</a>

includes:

search: true
---

# Introduction

Biff is a web framework and self-hosted deployment solution for Clojure (<a
href="https://github.com/jacobobryant/biff" target="_blank">Github repo</a>).
It's the culmination of 18 months I've spent building web apps with various
technologies like Datomic, Fulcro, AWS and Firebase (and plenty of Clojure
libraries). I've taken parts I liked and added a few innovations of my own.
It's meant primarily to speed up development in pre-growth startups and hobby
projects, but over time I'd like to make it suitable for apps that need scale
as well.

It includes features for:

- Automated **installation and deploys** on DigitalOcean.
- [**Crux**](https://opencrux.com) for the database. Thus you can use
  filesystem persistence on a $5 droplet for hobby projects or managed Postgres
  (or even Kafka) for serious apps.
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

Biff is currently **alpha quality**, though I am using it in production for <a
href="https://findka.com" target="_blank">Findka</a>. Join `#biff` on <a
href="http://clojurians.net" target="_blank">Clojurians Slack</a> for
discussion. I want to help people succeed with Biff, so feel free to ask for
help and let me know what I can improve. If you'd like to support my work and
receive email updates about Biff, <a href="https://findka.com/subscribe/"
target="_blank">subscribe to my newsletter</a>.

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

You can tinker with this app and use it as a template for your own projects.

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
    (biff.system/start-biff 'hello.biff)))

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

## Configuration

App-specific configuration can go in your plugin, as shown above. For example, we set
`#:hello.biff.auth/on-signin` so that clients will be redirected to `/app/` after they
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
namespace, e.g. `(biff.system/start-biff sys 'another-app.biff)`. Keys under
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

