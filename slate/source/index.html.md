---
title: Biff

language_tabs: # must be one of https://git.io/vQNgJ
  - Clojure

toc_footers:

includes:

search: true
---

# Introduction

Biff is designed to make web development with Clojure fast and easy without
compromising on simplicity. It prioritizes small-to-medium sized projects.

Biff has two parts: a library and a template project. As much code as
possible is written as library code, exposed under the `com.biffweb` namespace.
This includes a lot of high-level helper functions for other libraries.

The template project contains the framework code&mdash;the stuff that glues all
the libraries together. When you start a new Biff project, the template project code is
copied directly into your project directory, and the library is added as a regular
dependency.

Some of Biff's most distinctive features:

- Built on [XTDB](https://xtdb.com/), the world's finest database. It has
  flexible data modeling, Datalog queries, and immutable history. You can use
  the filesystem for the storage backend in dev and switch to Postgres for
  production.
- Uses [htmx](https://htmx.org/) (and [hyperscript](https://hyperscript.org/))
  for the frontend. Htmx lets you create interactive, real-time applications by
  sending HTML snippets from the server instead of using
  JavaScript/ClojureScript/React.
- Ready to deploy. The template project comes with a script for provisioning an
  Ubuntu server, including Git push-to-deploy, HTTPS certificates, and NGINX
  configuration.
- Develop in prod. If you choose to enable this, you can develop your entire
  application without ever starting up a JVM on your local machine. Whenever
  you hit save, files get rsynced to the server and evaluated.

Other things that Biff wraps/includes:

- [Rum](https://github.com/tonsky/rum) and [Tailwind CSS](https://tailwindcss.com/) for rendering.
- [Jetty](https://github.com/sunng87/ring-jetty9-adapter) for the web server
  and [Reitit](https://github.com/metosin/reitit) for routing.
- [Malli](https://github.com/metosin/malli) for enforcing schema when submitting XTDB transactions.
- [Buddy](https://funcool.github.io/buddy-sign/latest/) for email link authentication (JWTs).
- [Chime](https://github.com/jarohen/chime) for scheduling tasks.
- A minimalist, 15-line dependency injection framework, similar in spirit to Component.

We use Biff over at [The Sample](https://thesample.ai/), a relatively young
two-person business. It has about 13k lines of code.

# Getting Started

Requirements:

 - JDK 11 or higher
 - [clj](https://clojure.org/guides/getting_started)

Run this command to create a new Biff project:

```
bash <(curl -s https://biffweb.com/new-project.sh)
```

This will create a minimal CRUD app which demonstrates most of Biff's features.
Run `./task dev` to start the app on localhost:8080. Clojure files will be
evaluated whenever you save them, and static HTML and CSS files will also be
regenerated.

You can connect your editor to nREPL port 7888. There's also a `repl.clj` file
which you can use as a scratch space.

When you're ready to deploy, see [Production](#production).

# Project Structure

A new Biff project will look like this:

(Throughout these docs, we'll assume you selected `com.example` for the main
namespace when creating your project.)

```
├── README.md
├── config.edn
├── config.sh
├── deps.edn
├── resources
│   ├── public
│   │   └── img
│   │       └── glider.png
│   ├── tailwind.config.js
│   └── tailwind.css
├── setup.sh
├── src
│   └── com
│       ├── example
│       │   ├── feat
│       │   │   ├── app.clj
│       │   │   ├── auth.clj
│       │   │   ├── home.clj
│       │   │   └── worker.clj
│       │   ├── repl.clj
│       │   ├── schema.clj
│       │   └── ui.clj
│       └── example.clj
└── task
```

`task` is a shell script that contains project commands. For example, `./task
dev` starts the app locally, and `./task deploy` pushes your most recent commit
to the server. See `README.md` for a list of all the commands.

`config.sh` contains configuration for `task`, such as the project's main
namespace (`com.example` in this case) and the domain name of the server to
deploy to. `config.edn` contains configuration for the application.

`deps.edn` by default defines a single dependency: `com.biffweb/biff`. This
library is aliased as `biff` in most namespaces.

`setup.sh` is a script for provisioning an Ubuntu server. See [Production](#production).

## Code organization

The example project is separated into three layers.

![code structure](/images/code-structure.svg)

We'll start with the middle layer. A feature namespace contains the routes,
static pages, scheduled tasks, and/or transaction listeners that pertain to a
particular feature. Each namespace exposes these things via a `features` map:

```clojure
(ns com.example.feat.some-feature
  ...)

(def features
  {:routes [...]
   :api-routes [...]
   :static {...}
   :tasks [...]
   :on-tx (fn ...)})
```

For example, the `com.example.feat.home` namespace defines a single route for
the landing page:

```clojure
(ns com.example.feat.home
  (:require [com.biffweb :as biff]
            [com.example.ui :as ui]))

(defn signin-form []
  ...)

(defn home [_]
  (ui/page
    {}
    (signin-form)))

(def features
  {:routes [["/" {:get home}]]})
```

The schema namespace defines the types of documents that are allowed to be
written to the database. Whenever you submit a transaction, it will
be checked against your schema first.

Here we define a `:user` document type which includes an email field and a
couple other string fields:

```clojure
(def schema
  {:user/id :uuid
   :user/email :string
   :user/foo :string
   :user/bar :string
   :user [:map {:closed true}
          [:xt/id :user/id]
          :user/email
          [:user/foo {:optional true}]
          [:user/bar {:optional true}]]
  ...})
```

The main namespace is the app's entry point. It bundles your schema and
features together. For example, here we combine all the routes and apply some
middleware:

```clojure
(def features
  [app/features
   auth/features
   home/features
   worker/features])

(def routes [["" {:middleware [anti-forgery/wrap-anti-forgery
                               biff/wrap-anti-forgery-websockets
                               biff/wrap-render-rum]}
              (map :routes features)]
             (map :api-routes features)])
```

Finally, "shared" namespaces contain code that's needed by multiple feature namespaces. The example
app has a single shared namespace, `com.example.ui`, which contains helper functions for
rendering HTML.

# Static Files

You can create static HTML files by supplying a map from paths to
[Rum](https://github.com/tonsky/rum) data structures. In
`com.example.feat.auth`, we define two static pages, either of which is shown
after you request a sign-in link:


```clojure
(def signin-sent
  (ui/page
    {}
    [:div
     "The sign-in link was printed to the console. If you add an API "
     "key for MailerSend, the link will be emailed to you instead."]))

(def signin-fail
  (ui/page
    {}
    [:div
     "Your sign-in request failed. There are several possible reasons:"]
    [:ul
     [:li "You opened the sign-in link on a different device or browser than the one you requested it on."]
     [:li "We're not sure you're a human."]
     [:li "We think your email address is invalid or high risk."]
     [:li "We tried to email the link to you, but it didn't work."]]))

(def features
  {:routes ...
   :static {"/auth/sent/" signin-sent
            "/auth/fail/" signin-fail}})
```

The map values (`signin-sent` and `signin-fail` in this case) are passed to
`rum.core/render-static-markup` and written to the path you specify. If the
path ends in a `/`, then `index.html` will be appended to it.

You can use Tailwind CSS to style your HTML:

```clojure
[:button.bg-blue-500.text-white.text-center.py-2.px-4
 {:type "submit"}
 "Sign in"]
```

The HTML and Tailwind CSS files will be regenerated whenever you save a file.
In addition, any files you put in `resources/public/` will be served.

See also:

 - [Rum documentation](https://github.com/tonsky/rum)
 - [Tailwind documentation](https://tailwindcss.com/)
 - [`export-rum`](https://github.com/jacobobryant/biff/blob/bdd1bd81d95ee36c615495a946c7c1aa92d19e2e/src/com/biffweb/impl/rum.clj#L105)

# Routing

Biff uses [Ring](https://github.com/ring-clojure/ring) and
[Reitit](https://github.com/metosin/reitit) for handling HTTP requests. Reitit
has a lot of features, but you can go far with just a few basics.

Multiple routes:

```clojure
(defn foo [request]
  {:status 200
   :headers {"content-type" "text/plain"}
   :body "foo response"})

(defn bar ...)

(def features
  {:routes [["/foo" {:get foo}]
            ["/bar" {:post bar}]]})
```

Path parameters:

```clojure
(defn click [{:keys [path-params] :as request}]
  (println (:token path-params))
  ...)

(def features
  {:routes [["/click/:token" {:get click}]]})
```

Nested routes:

```clojure
(def features
  {:routes [["/auth/"
             ["send" {:post send-token}]
             ["verify/:token" {:get verify-token}]]]})
```

With middleware:

```clojure
(defn wrap-signed-in [handler]
  (fn [{:keys [session] :as req}]
    (if (some? (:uid session))
      (handler req)
      {:status 303
       :headers {"location" "/"}})))

(def features
  {:routes [["/app" {:middleware [wrap-signed-in]}
             ["" {:get app}]
             ["/set-foo" {:post set-foo}]]]})
```

If you need to provide a public API, you can use `:api-routes` to disable
CSRF protection (this is a Biff feature, not a Reitit one):

```clojure
(defn echo [{:keys [params]}]
  {:status 200
   :headers {"content-type" "application/json"}
   :body params})

(def features
  {:api-routes [["/echo" {:post echo}]]})
```

Biff includes some middleware (`wrap-render-rum`) which will treat vector responses
as Rum. The following handlers are equivalent:

```clojure
(require '[rum.core :as rum])

(defn my-handler [request]
  {:status 200
   :headers {"content-type" "text/html"}
   :body (rum/render-static-markup
           [:html
            [:body
             [:p "I'll gladly pay you Tuesday for a hamburger on Tuesday"]]])})

(defn my-handler [request]
  [:html
   [:body
    [:p "I'll gladly pay you Tuesday for a hamburger on Tuesday"]]])
```

See also:

 - [Reitit documentation](https://cljdoc.org/d/metosin/reitit/CURRENT/doc/introduction)
 - [`reitit-handler`](https://github.com/jacobobryant/biff/blob/bdd1bd81d95ee36c615495a946c7c1aa92d19e2e/src/com/biffweb.clj#L84)

# Schema

XTDB (the database Biff uses) does not enforce schema on its own. Biff provides schema enforcement
with [Malli](https://github.com/metosin/malli). Here's a Malli crash course.

Say we want to save a user document like this:

```clojure
{:xt/id #uuid "..."
 :user/email "bob@example.com"
 :user/favorite-color :blue
 :user/age 132}
```

In our Malli schema, we'll first define schemas for each of the user document's attributes:

```clojure
(def schema
  {:user/id :uuid
   :user/email :string
   :user/favorite-color :keyword
   :user/age number?

   ...})
```

For the schema map values, Malli has a handful of [type
schemas](https://github.com/metosin/malli#mallicoretype-schemas) like `:uuid`
and `:string` above, and it also supports [predicate
schemas](https://github.com/metosin/malli#mallicorepredicate-schemas) like
`number?` above.

Once our attributes have been defined, we can combine them into a schema for
the user document itself:

```clojure
(def schema
  {...
   :user [:map {:closed true}
          [:xt/id :user/id]
          :user/email
          [:user/favorite-color {:optional true}]
          [:user/age {:optional true}]]
   ...})
```

In English, this means:

 - `:user` is a map (all of our document schemas will be maps).
 - It's a closed map: it's not allowed to have any keys that we haven't
   included in the `:user` schema.
 - `:user/email` is a required attribute.
 - `:user/favorite-color` and `:user/age` are optional attributes.
 - `:xt/id` has the same schema as `:user/id` (which happens to be `:uuid`).

A note about `:xt/id`. Every other attribute is defined globally, outside of
the document schema. e.g. `:user/email` is defined globally to be a string.
However, every document must have an `:xt/id` attribute, and different types of
documents may need different schemas for `:xt/id`. So we define the schema for
`:xt/id` locally, within the document.

See also:

 - [Malli documentation](https://github.com/metosin/malli)
 - [Malli built-in schemas](https://github.com/metosin/malli#built-in-schemas)

# Transactions

*Biff uses [XTDB](https://xtdb.com/) for the database. It's OK if you haven't used XTDB before,
but you may want to peruse some of the [learning resources](https://xtdb.com/learn/) at least.*

The request map passed to HTTP handlers (and the scheduled tasks and
transaction listeners) includes a `:biff.xtdb/node` key which can be used to
submit transactions:

```clojure
(require '[xtdb.api :as xt])

(defn send-message [{:keys [biff.xtdb/node session params] :as req}]
  (xt/submit-tx node
    [[::xt/put {:xt/id (java.util.UUID/randomUUID)
                :msg/user (:uid session)
                :msg/text (:text params)
                :msg/sent-at (java.util.Date.)}]])
  ...)
```

Biff also provides a higher-level wrapper over `xtdb.api/submit-tx`. It lets
you specify document types from your schema. If the document you're trying to
write doesn't match its respective schema, the transaction will fail. In
addition, Biff will call `xt/await-tx` on the result, so you can read your
writes.

```clojure
(require '[com.biffweb :as biff])

(defn send-message [{:keys [session params] :as req}]
  (biff/submit-tx
    ;; select-keys is for illustration. Normally you would just pass in req.
    (select-keys req [:biff.xtdb/node :biff/malli-opts])
    [{:db/doc-type :message
      :msg/user (:uid session)
      :msg/text (:text params)
      :msg/sent-at (java.util.Date.)}])
  ...)
```

If you don't set `:xt/id`, Biff will use `(java.util.UUID/randomUUID)` as the default value.
The default operation is `:xtdb.api/put`.

You can delete a document by setting `:db/op :delete`:

```clojure
(defn delete-message [{:keys [params] :as req}]
  (biff/submit-tx req
    [{:xt/id (java.util.UUID/fromString (:msg-id params))
      :db/op :delete}])
  ...)
```

As a convenience, any occurrences of `:db/now` will be replaced with `(java.util.Date.)`:

```clojure
(defn send-message [{:keys [session params] :as req}]
  (biff/submit-tx req
    [{:db/doc-type :message
      :msg/user (:uid session)
      :msg/text (:text params)
      :msg/sent-at :db/now}])
  ...)
```

If you set `:db/op :update` or `:db/op :merge`, the document will be merged
into an existing document if it exists. The difference is that `:db/op :update` will
cause the transaction to fail if the document doesn't already exist.

```clojure
(defn set-foo [{:keys [session params] :as req}]
  (biff/submit-tx req
    [{:db/op :update
      :db/doc-type :user
      :xt/id (:uid session)
      :user/foo (:foo params)}])
  ...)
```

Biff uses `:xtdb.api/match` operations to ensure that concurrent
merge/update operations don't get overwritten. If the match fails, the
transaction will be retried up to three times.

When `:db/op` is set to `:merge` or `:update`, you can use special operations
on a per-attribute basis. These operations can use the attribute's previous
value, along with new values you provide, to determine what the final value
should be.

Use `:db/union` to coerce the previous value to a set and insert new values
with `clojure.set/union`:

```clojure
[{:db/op :update
  :db/doc-type :post
  :xt/id #uuid "..."
  :post/tags [:db/union "clojure" "almonds"]}]
```

Use `:db/difference` to do the opposite:

```clojure
[{:db/op :update
  :db/doc-type :post
  :xt/id #uuid "..."
  :post/tags [:db/difference "almonds"]}]
```

Add to or subtract from numbers with `:db/add`:

```clojure
[{:db/op :update
  :db/doc-type :account
  :xt/id #uuid "..."
  :account/balance [:db/add -50]}]
```

Use `:db/default` to set a value only if the existing document doesn't
already contain the attribute:

```clojure
[{:db/op :update
  :db/doc-type :user
  :xt/id #uuid "..."
  :user/favorite-color [:db/default :yellow]}]
```

Use `:db/dissoc` to remove an attribute:

```clojure
[{:db/op :update
  :db/doc-type :user
  :xt/id #uuid "..."
  :user/foo :db/dissoc}]
```

Finally, you can use `:db/lookup` to enforce uniqueness constraints on attributes
other than `:xt/id`:

```clojure
[{:db/doc-type :user
  :xt/id [:db/lookup {:user/email "hello@example.com"}]}]
```

This will use a separate "lookup document" that, if the user has been created already, will
look like this:

```clojure
{:xt/id {:user/email "hello@example.com"}
 :db/owned-by ...}
```

where `...` is a document ID. If the document doesn't exist, the ID will be `(java.util.UUID/randomUUID)`,
unless you pass in a different default ID with `:db/lookup`:

```clojure
[{:db/doc-type :user
  :xt/id [:db/lookup {:user/email "hello@example.com"} #uuid "..."]}]
```

If the first value passed along with `:db/lookup` is a map, it will get merged
in to the document. So our entire transaction would end up looking like this, assuming
the user document doesn't already exist:

```clojure
[{:db/doc-type :user
  :xt/id [:db/lookup {:user/email "hello@example.com"}]}]
;; =>
[[:xtdb.api/put {:xt/id #uuid "abc123"
                 :user/email "hello@example.com"}]
 [:xtdb.api/match {:user/email "hello@example.com"} nil]
 [:xtdb.api/put {:xt/id {:user/email "hello@example.com"}
                 :db/owned-by #uuid "abc123"}]]
```

If you need to do something that `biff/submit-tx` doesn't support (like setting
a custom valid time or using transaction functions), you can always drop down
to `xt/submit-tx`.

See also:

 - [XTDB learning resources](https://xtdb.com/learn/)
 - [XTDB transaction reference](https://docs.xtdb.com/language-reference/datalog-transactions/)
 - [`submit-tx`](https://github.com/jacobobryant/biff/blob/bdd1bd81d95ee36c615495a946c7c1aa92d19e2e/src/com/biffweb/impl/xtdb.clj#L247)

# Queries

As mentioned last section, Biff uses [XTDB](https://xtdb.com/) for the
database. See the [XTDB query
reference](https://docs.xtdb.com/language-reference/datalog-queries/).

Biff provides a couple query convenience functions. `com.biffweb/q` is a *very*
light wrapper around `xtdb.api/q`. First, it will throw an exception if you
pass an incorrect number of arguments to `:in`.

```clojure
(q db
   '{:find [user]
     :in [email color]
     :where [[user :user/email email]
             [user :user/color color]]}
   "bob@example.com") ; Oops, we forgot to pass in a color--ask me sometime
                      ; how often I've made this mistake.
```

Second, if you omit the vector around
the `:find` value, the results will be scalars instead of tuples. For example,
the following queries are equivalent:

```clojure
(require '[xtdb.api :as xt])
(require '[com.biffweb :as biff])

(map first
     (xt/q db
           '{:find [email]
             :where [[user :user/email email]]}))

;; Think of all the carpal tunnel cases we're preventing by eliminating the
;; need for constant map firsts!
(biff/q db
        '{:find email
          :where [[user :user/email email]]})
```

`com.biffweb/lookup` is a bit like `xtdb.api/entity`, except you pass in an
arbitrary key-value pair instead of a document ID:

```clojure
(lookup db :user/email "bob@example.com")
;; =>
{:xt/id #uuid "..."
 :user/email "bob@example.com"
 :user/favorite-color :chartreuse}
```

There is also `lookup-id` which returns the document ID instead of the entire document.

See also:

 - [XTDB query reference](https://docs.xtdb.com/language-reference/datalog-queries/)

# Htmx

[Htmx](https://htmx.org/) allows us to create interactive user interfaces
without JavaScript (or ClojureScript). It works by returning snippets of HTML
from the server in response to user actions. For example, the following code will cause
the button to be replaced with some text after it's clicked:

```clojure
(defn page [request]
  [:html
   [:head
    [:script {:src "https://unpkg.com/htmx.org@1.6.1"}]]
   ...
   [:form {:hx-post "/click" :hx-swap "outerHTML"}
    [:button {:type "submit"} "Don't click this button"]
    ...]])

(defn click [request]
  [:div "What the hell, I told you not to click that!"])

(def features
  {:routes [["/page" {:get page}]
            ["/click" {:post click}]]})
```

(You use htmx by setting `:hx-*` attributes on your HTML elements.)

You can also use htmx to establish websocket connections:

```clojure
(require '[ring.adapter.jetty9 :as jetty])
(require '[rum.core :as rum])

(defn chat-page [request]
  [:html
   ...
   [:div {:hx-ws "connect:/chat-ws"}
    [:div#messages]
    [:form {:hx-ws "send"}
     [:textarea {:name "text"}]
     [:button {:type "submit"} "Send message"]]]])

(defn chat-ws [{:keys [example/chat-clients] :as req}]
  ;; chat-clients is initialized to (atom #{})
  {:status 101
   :headers {"upgrade" "websocket"
             "connection" "upgrade"}
   :ws {:on-connect (fn [ws]
                      (swap! chat-clients conj ws))
        :on-text (fn [ws text]
                   (doseq [ws @chat-clients]
                     (jetty/send! ws (rum/render-static-markup
                                       [:div#messages {:hx-swap-oob "beforeend"}
                                        [:p "new message: " text]]))))
        :on-close (fn [ws status-code reason]
                    (swap! chat-clients disj ws))}})

(def features
  {:routes [["/chat-page" {:get chat-page}]
            ["/chat-ws" {:get chat-ws}]]})
```

(Note that this chat room will only work if all the participants are connected
to the same web server. For that reason it's better to call `jetty/send!` from
a transaction listener&mdash;see the next section.)

You can also use htmx's companion library
[hyperscript](https://hyperscript.org/) to do lightweight frontend scripting.
Htmx is good when you need to contact the server anyway; hyperscript is good
when you don't. Our previous button example could be done with hyperscript
instead of htmx:

```clojure
(defn page [request]
  [:html
   [:head
    [:script {:src "https://unpkg.com/hyperscript.org@0.9.3"}]]
   ...
   [:div#message]
   [:button {:_ "on click put 'tsk tsk' into #message then remove me"}
    "Don't click this button"]])
```

See also:

 - [Htmx documentation](https://htmx.org/docs/)
 - [Hyperscript documentation](https://hyperscript.org/docs/)

# Transaction Listeners

XTDB maintains an immutable transaction log. You can register a listener
function which will get called whenever a new transaction has been appended to
the log. If you provide a function for the `:on-tx` feature key, Biff will
register it for you and pass the new transaction to it. For example, here's a
transaction listener that prints a message whenever there's a new user:

```clojure
(defn alert-new-user [{:keys [biff.xtdb/node]} tx]
  (let [db-before (xt/db node {::xt/tx-id (dec (::xt/tx-id tx))})]
    (doseq [[op & args] (::xt/tx-ops tx)
            :when (= op ::xt/put)
            :let [[doc] args]
            :when (and (contains? doc :user/email)
                       (nil? (xt/entity db-before (:xt/id doc))))]
      (println "there's a new user"))))

(def features
  {:on-tx alert-new-user})
```

The value of `tx` looks like this:

```clojure
{:xtdb.api/tx-id 9,
 :xtdb.api/tx-time #inst "2022-03-13T10:24:45.432-00:00",
 :xtdb.api/tx-ops ([:xtdb.api/put
                    {:xt/id #uuid "dc4b4893-d4f1-4876-b4c5-6f87f5abcd7d",
                     :user/email "hello@example.com"}]
                   ...)}
```

See also:

 - [`use-tx-listener`](https://github.com/jacobobryant/biff/blob/bdd1bd81d95ee36c615495a946c7c1aa92d19e2e/src/com/biffweb/impl/xtdb.clj#L78)
 - [`xtdb.api/listen`](https://docs.xtdb.com/clients/clojure/#\_listen)

# Scheduled Tasks

Biff uses [chime](https://github.com/jarohen/chime) to execute functions on a
recurring schedule. For each task, you must provide a function to run and a
zero-argument schedule function which will return a list of times at which to
execute the task function. The schedule can be an infinite sequence. For example, here's
a task that prints out the number of users every 60 seconds:

```clojure
(require '[com.biffweb :as biff :refer [q]])

(defn print-usage [{:keys [biff/db]}]
  (let [n-users (first (q db
                          '{:find (count user)
                            :where [[user :user/email]]}))]
    (println "There are" n-users "users.")))

(defn every-minute []
  (iterate #(biff/add-seconds % 60) (java.util.Date.)))

(def features
  {:tasks [{:task #'print-usage
            :schedule every-minute}]})
```

See also:

 - [chime documentation](https://github.com/jarohen/chime)
 - [`use-chime`](https://github.com/jacobobryant/biff/blob/bdd1bd81d95ee36c615495a946c7c1aa92d19e2e/src/com/biffweb.clj#L297)

# Authentication

The authentication code is kept entirely within the template project at
`com.example.feat.auth`. Biff uses email sign-in links instead of passwords.
When you create a new project, a secret token is generated and stored in
`config.edn`, under the `:biff/jwt-secret` key. When a user wants to
authenticate, they enter their email address, and then your secret token is used
to sign a JWT which is then embedded in a link and sent to the user's email
address. When they click on the link, their user ID is added to their session
cookie. By default the link is valid for one hour and the session lasts for 60
days.

You can get the user's ID from the session like so:

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

In a new Biff project, the sign-in link will be printed to the console. To have
it get sent by email, you'll need to include an API key for
[MailerSend](https://www.mailersend.com/) under the `:mailersend/api-key` key
in `config.edn`. It's also pretty easy to use a different service like
[Mailgun](https://www.mailgun.com/) if you prefer.

Some applications that use email sign-in links are vulnerable to login CSRF,
wherein an attacker requests a sign-in link for their own account and then
sends it to the victim. If the victim clicks the link and doesn't notice
they've been signed into someone else's account, they might reveal private
information. Biff prevents login CSRF by checking that the link is clicked on
the same device it was requested from.

It is likely you will need to protect your sign-in form against bots. The
template project includes backend code for reCAPTCHA v3, which does invisible
bot detection (i.e. no need to click on pictures of cars; instead Google just
analyzes your mouse movements etc). See [this
page](https://developers.google.com/recaptcha/docs/v3) for instructions on
adding the necessary code to the frontend. You can enable the backend
verification code by setting `:recaptcha/secret-key` in `config.edn`.

For added protection (and to help catch incorrect user input), you can also use
an email verification API like
[Mailgun's](https://documentation.mailgun.com/en/latest/api-email-validation.html).

See also:

 - [`com.example.feat.auth`](https://github.com/jacobobryant/biff/blob/bdd1bd81d95ee36c615495a946c7c1aa92d19e2e/example/src/com/example/feat/auth.clj)
 - [`com.biffweb/mailersend`](https://github.com/jacobobryant/biff/blob/bdd1bd81d95ee36c615495a946c7c1aa92d19e2e/src/com/biffweb.clj#L213)
 - [Mailersend](https://www.mailersend.com/)
 - [Mailgun](https://www.mailgun.com/)

# System Composition

All the pieces of a Biff project are combined using the
`com.biffweb/start-system` function. This function takes a *system map* and then
passes it through a list of *component functions*. For example, here we start a
very simple app that includes Jetty (as a convention, component functions start
with `use-`):

```clojure
(require '[com.biffweb :as biff])
(require '[ring.adapter.jetty9 :as jetty])

(defn use-jetty [{:keys [biff/handler] :as system}]
  (let [server (jetty/run-jetty handler
                                {:host "localhost"
                                 :port 8080
                                 :join? false})]
    (update system :biff/stop conj #(jetty/stop-server server))))

(defn handler [request]
  {:status 200
   :headers {"content-type" "text/plain"}
   :body "hello"})

(defn -main [& args]
  (biff/start-system
    {:biff/handler #'handler
     :biff/components [use-jetty]}))
```

After calling `start-system`, the system map will be stored in
`com.biffweb/system` (an atom). You can inspect it from the repl with e.g.
`(sort (keys @com.biffweb/system))`.

The system map uses flat, namespaced keys rather
than nested maps. For example, after starting up a new project, here are a few of the
keys that the system map will include:

```clojure
{;; These were included in our call to start-system
 :biff/config "config.edn"
 :biff/handler ...
 :example/chat-clients ...

 ;; These were read from config.edn and merged in by use-config
 :biff.xtdb/topology :standalone,
 :biff.xtdb/dir "storage/xtdb",
 :biff/base-url "http://localhost:8080",

 ;; This was added by use-xt
 :biff.xtdb/node ...

 ...}
```

Several of the component functions pass the system map to their children. For
example, Biff includes some middleware that will merge the system map with the
request map for all incoming requests. `use-chime` will similarly pass the
system map to scheduled task functions. Application code should not touch
`com.biffweb/system` directly; instead, always take it as a parameter from the
parent component function.

If you need to modify some code that runs at startup, you can call
`com.biffweb/refresh`. This will call all the functions stored in `:biff/stop`,
then it will reload all the code with `clojure.tools.namespace.repl/refresh`,
after which `start-system` will be called with the new code.

However, you shouldn't need to call `refresh` regularly. Whenever possible, Biff uses
late binding so that code can be updated without restarting the system. For
example, since we pass the Ring handler function as a var&mdash;`(biff/start-system
{:biff/handler #'handler ...})`&mdash;we can redefine `handler` from the repl
(which will happen automatically whenever you modify any routes and save a
file) and the new handler will be used for new HTTP requests immediately.

`start-system` and `refresh` are only a few lines of code, so it'd be worth your time
to just read the source:

```clojure
(defonce system (atom nil))

(defn start-system [system*]
  (reset! system (merge {:biff/stop '()} system*))
  (loop [{[f & components] :biff/components :as sys} system*]
    (when (some? f)
      (println "starting:" (str f))
      (recur (reset! system (f (assoc sys :biff/components components))))))
  (println "System started."))

(defn refresh []
  (let [{:keys [biff/after-refresh biff/stop]} @system]
    (doseq [f stop]
      (println "stopping:" (str f))
      (f))
    (clojure.tools.namespace.repl/refresh :after after-refresh)))
```

(In general, reading [Biff's source
code](https://github.com/jacobobryant/biff/tree/dev/src/com/biffweb.clj) is
a great way to learn more about how it works under the hood. The whole thing
isn't very large anyway.)

See also:

 - [Stuart Sierra's Component](https://github.com/stuartsierra/component).
   `start-system` is similar in spirit but is geared for small applications.

## Taking Biff apart

As your application grows, you will inevitably need more and/or different
behaviour than what Biff gives you out of the box. You can modify any part of
Biff by supplying a different list of component functions to `start-system`.
You can add or remove components, and you can modify existing components by
copying their source into your project. For example, if you want to read in
your configuration differently, you can change this:

```clojure
(require '[com.biffweb :as biff])

(defn start []
  (biff/start-system
    {:biff/config "config.edn"
     :biff/components [biff/use-config
                       ...]
     ...}))
```

to this:

```clojure
(defn read-config [path]
  (let [env (keyword (or (System/getenv "BIFF_ENV") "prod"))
        env->config (clojure.edn/read-string (slurp path))
        config-keys (concat (get-in env->config [env :merge]) [env])
        config (apply merge (map env->config config-keys))]
    config))

(defn use-config [sys]
  (merge sys (read-config (:biff/config sys))))

(defn start []
  (biff/start-system
    {:biff/config "config.edn"
     :biff/components [use-config
                       ...]
     ...}))
```

and then make your desired modifications. If you want you could even replace
`start-system` with e.g. Integrant or Component, adding the appropriate
wrappers for Biff's component functions.

# Production

Biff comes with a script (`setup.sh`) for setting up an Ubuntu server. It's
been tested on DigitalOcean. You can of course deploy Biff anywhere that can
run a JVM&mdash;but if you're happy with the defaults then you can simply
follow these steps:

1. Create an Ubuntu VPS in e.g. DigitalOcean.
2. (Optional) If this is an important application, you may want to set up a
   managed Postgres instance and edit `config.edn` to use that for XTDB's
   storage backend instead of the filesystem. With the default standalone
   topology, you'll need to handle backups yourself, and you can't use more
   than one server.
3. Edit `config.sh` and set `SERVER` to the domain you'd like to use for your
   app. For now we'll assume you're using `example.com`. Also update `DEPLOY_FROM` if
   you use `main` instead of `master` as your default branch.
4. Edit `config.edn` and update `:biff/base-url`.
5. Set an A record on `example.com` that points to your Ubuntu server.
6. Make sure you can ssh into the server, then run `scp setup.sh root@example.com:`.
7. Run `ssh root@example.com`, then `bash setup.sh`. After it finishes, run `reboot`.
8. On your local machine, run `git remote add prod ssh://app@example.com/home/app/repo.git`.

Now you can deploy your application anytime by committing your code and then
running `./task deploy`. This will copy your config files (which aren't checked
into Git) to the server, then it'll deploy the latest commit via git push. You can run
`./task logs` to make sure the deploy was successful.

Some notes:

 - See `README.md` for a list of other commands that `./task` accepts.
 - If you need to make changes to the server (e.g. perhaps you need to install
   an additional package), be sure to update `setup.sh` so you can always
   easily provision a new server from scratch.
 - Biff is organized so you can easily split up your application into a web
   server(s) and a worker(s), although there are no step-by-step instructions
   for how to do this yet.
 - [Papertrail](https://www.papertrail.com/) is cheap and easy to set up and is
   useful for alerts. For example, it can send you an email whenever your
   application logs include the text `Exception`.
