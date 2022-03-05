---
title: Biff

language_tabs: # must be one of https://git.io/vQNgJ
  - Clojure

toc_footers:

includes:

search: true
---

# Work-in-progress notice

I'm in the middle of overhauling this documentation! See the [Biff sponsorship
announcement](https://biffweb.com/p/sponsorships/).

# Introduction

Biff is designed to make web development with Clojure fast and easy [without
compromising](#design-philosophy) on simplicity. It prioritizes small-to-medium
sized projects.

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
  for the frontend. htmx lets you create interactive, real-time applications by
  sending html snippets from the server instead of using
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
business that I work on full-time with my brother. It has about 13k lines of
code.

<!--

## Additional resources

 - [An interview](https://soundcloud.com/user-959992602/s4-e27-biff-with-jacob-obryant/s-fpVxrTrP9ZJ) on the ClojureScript Podcast.
 - [An interview](https://console.substack.com/p/console-49) on The Console.
 - [A presentation](https://youtu.be/mKqjJH3WiqI) I gave at re:Clojure 2020 ([slides](https://jacobobryant.com/misc/reclojure-2020-jacobobryant.pdf)).
 - [A presentation](https://www.youtube.com/watch?v=oYwhrq8hDFo) I gave at the Clojure Mid-Cities meetup.

<br>

<p><iframe width="560" height="315" src="https://www.youtube.com/embed/mKqjJH3WiqI" frameborder="0" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture" allowfullscreen></iframe></p>

# Getting started

Requirements:

 - [clj](https://clojure.org/guides/getting_started) (v1.10.1.697+)
 - [node.js](https://nodejs.org/)

See [Troubleshooting](#troubleshooting) if you run into any problems.


Run this command to create a new Biff project:

```
bash <(curl -s https://raw.githubusercontent.com/jacobobryant/biff/master/new-project.sh)
```

The template project is a minimal CRUD app which demonstrates most of Biff's
features.

NOTE: This page assumes you chose `example.core` for your project's main
namespace. So instead of writing `src/<your project>/some-file.clj`, we'll just
write `src/example/some-file.clj`.

### Development

Run `./task dev` to start an nREPL server and run the app (on `localhost:8080`
by default). See `dev/example/dev.clj` for reloaded workflow instructions.

If you prefer to start an nREPL server via your editor, you can navigate to
`dev/example/dev.clj` and eval `(start)`. You'll need to ensure that the `:dev`
alias is activated and that the environment variables in `config/dev.env` have
been sourced.

Assets (HTML, CSS, CLJS) will be regenerated whenever you save a file. CLJS is
handled with Shadow CLJS (you can view compilation output at
`localhost:9630/build/app`). HTML and CSS are handled with a separate file
watcher which requires that you eval your changes before saving. For example,
after running `./task dev`, go to `src/example/views.clj`, change `"Email
address:"` to `"Your email address:"`, eval the file, then save it. If you go
to `localhost:8080` (and log out if needed), the change should be visible.

Tests will also run whenever you save a file. Similarly, they must be eval'd
first.

### Building

Stop the app if it's running, then run `./task build` to generate assets (HTML,
CSS, CLJS) and build an uberjar at `target/app.jar`.

Production configuration is stored in `config/prod.env`. You can deploy your
uberjar anywhere as long as you set those environment variables somehow. It's
assumed that you handle SSL elsewhere (e.g. with Nginx) and then proxy requests
to the app on localhost.

If you use the server setup script below, you won't need to run `./task build`
locally since it will be done on the server after git push.

### Server setup

The project template includes a script for provisioning an Ubuntu server,
including push-to-deploy. The server should have at least 2 GB of RAM (for
building). I've tested it with DigitalOcean.

If using DigitalOcean: first create a droplet (Ubuntu 20.04 LTS). Switch to
Regular Intel at $10/month. Add monitoring. Make sure your SSH key is selected.
(If needed, go to Settings and add your SSH key, then start over). Set the
hostname to something distinctive. After the droplet is created, go to
Networking and point your domain to it (create an A record).

Then, update the vars in `config/prod.env` and `config/task.env`. Run the setup
script on your new server, replacing `example.com` with your domain:

```bash
scp infra/setup.sh root@example.com:
ssh root@example.com
bash setup.sh
reboot
```

From your local machine, add your server as a remote:

```bash
git remote add prod ssh://app@example.com/home/app/repo.git
```

### Deployment

Commit your changes locally, then run `./task deploy`. This will copy
`config/prod.env` and then do a git push to the server, which will build the
uberjar, create a release, and restart the app with the new release.

You can edit `infra/post-receive` to change what happens after a push.

### Monitoring

Run `./task logs` to tail the systemd logs. Run `./task prod-repl` to connect
to the production nREPL server. You can use `src/example/admin.clj` as an nREPL
admin console.

# Using this documentation

Biff's documentation is divided between this page, the [API
docs](https://biff.findka.com/codox/), and the template documentation (the
in-code comments included when you create a new project). This page is good
for getting an overview of Biff's features and as a reference for certain things
(like the Biff transaction format). You can get the nitty-gritty details from
exploring the template project, which also contains links to relevant sections of the
API docs.

# Design Philosophy

Libraries are simple. Frameworks are easy. Frameworks can be good if they're
both simple and easy. Biff attempts to be simple by focusing on
decomposability: it addresses not only "how should these pieces be put
together" but also "how can they be taken apart?" Your project might even
gradually reach the point where it's no longer a "Biff" project at all.

The following describes how Biff currently tries to reach that ideal, subject
to refinement.

### Code organization

Biff consists of two parts:

1. A collection of libraries. For example, biff.crux has helper functions and
   additional features for Crux, and biff.middleware has some Ring middleware.
   Each of these libraries can be used independently.

2. A project template, which is used by the [new project
   script](#getting-started) above. This is where the framework code lives. The
   project template composes the various libraries together for you.

By keeping the framework code in the project template, it is easy for you to
change (you don't have to copy the source from somewhere) and easy for Biff to
change (the project template doesn't need to be backwards compatible).

The project template also acts as a testing ground. New code can go there
first. With time, the correct abstractions will become clearer, and then the
code can be moved to one of Biff's libraries. For example, authentication is
currently handled entirely within the project template.

### System composition

Biff uses a minimalist implementation of Stuart Sierra's [component design
pattern](https://github.com/stuartsierra/component#component). The system is
represented by a single map with flat, namespaced keys. Each component is a
function that modifies the system map, kind of like Ring middleware modifying
an incoming request. For example, here's a Biff component for the web server:

```clojure
; As a convention, components are named use-*
(defn use-jetty [{:biff/keys [host port handler]
                  :biff.jetty/keys [quiet websockets]
                  :or {host "localhost"
                       port 8080}
                  :as sys}]
  (let [server (jetty/run-jetty handler
                                {:host host
                                 :port port
                                 :join? false
                                 :websockets websockets
                                 :allow-null-path-info true})]
    (when-not quiet
      (println "Jetty running on" (str "http://" host ":" port)))
    ; :biff/stop is a collection of zero-argument functions.
    (update sys :biff/stop conj #(jetty/stop-server server))))
```

Biff components are "principled" (see Systems of Modules from [Elements of
Clojure](https://elementsofclojure.com/): "We want a collection of principled
components, built to be discarded, separated by interfaces that are built to
last"). They provide a few configuration options, and if you want deeper
customization, you can just copy the source and create a new component that
does what you need.

Components don't explicitly define dependencies on other components; they just
document which keys they need and which keys they provide or modify (via the
function signature and the doc string). You define the start order manually:

```clojure
(defn -main []
  ; start-system is essentially the same as (reduce (fn [m f] (f m)) ...).
  (biff.util/start-system
    ; Initial config
    {:biff.reitit/routes [...]
     ...}
    ; Components
    [use-env
     use-nrepl
     use-crux
     ...]))
```

Since Biff components are just functions, you can easily create wrappers for use
with Stuart Sierra Component, Mount, etc. if you so desire.

Fun fact: you can even make anonymous components, like
`#(update % :biff/handler wrap-foo)`.

# Authorization rules

When using [Biff transactions](#transactions) or [subscriptions](#subscriptions), you'll
need to specify relevant "doc types." These can be defined with either Spec or Malli:

```clojure
(def registry
  {:user/id     :uuid
   :user/email  :string
   :user/foo    :string
   :user/bar    :string
   :user        [:map {:closed true}
                 [:crux.db/id :user/id]
                 :user/email
                 [:user/foo {:optional true}]
                 [:user/bar {:optional true}]]
   :msg/id      :uuid
   :msg/user    :user/id
   :msg/text    :string
   :msg/sent-at inst?
   :msg         [:map {:closed true}
                 [:crux.db/id :msg/id]
                 :msg/user
                 :msg/text
                 :msg/sent-at]})

(def schema (biff.misc/map->MalliSchema
              {:doc-types [:user :msg]
               :malli-opts {:registry (biff.misc/malli-registry registry)}}))

; For Spec: (biff.misc/map->SpecSchema {:doc-types [::user ::msg]})

(biff.util/start-system
  {:biff/schema schema
   ...
```

Queries and transactions will be rejected if the documents they affect don't
conform to the specified doc types.

Once you've defined a doc type, you can create authorization rules for it
by extending a multimethod:

```clojure
(defmethod biff.crux/authorize [:user :get]
  [{:keys [biff/uid]} {:keys [crux.db/id]}]
  (= uid id))
```

There are five operations: `#{:get :query :create :update :delete}`. There are
also three aliases: `:read` (which covers `:get` and `:query`), `:write` (which
covers `:create`, `:update`, and `:delete`) and `:rw` (which covers
everything). The `biff.crux/authorize` multimethod is dispatched on the doc
type and the operation. For example, if you try to create a new user, then Biff
will dispatch on `[:user :create]`, then `[:user :write]`, then `[:user :rw]`.
If at least one of those methods returns truthy, then the transaction will be
permitted.

The first argument to `authorize` is the system map, with some additional keys
merged in depending on the operation:

Key | Operations | Description
----|------------|------------
`:biff/uid` | All | The ID of the user who submitted the query/transaction. `nil` if they're unauthenticated.
`:doc-type` | All | e.g. `:user`
`:operation` | All | e.g. `:get`
`:doc-id` | All | The relevant document's ID.
`:doc` | Read | The document being read.
`:db` | Read | A Crux DB from the time the document is being read.
`:before` | Write | The document before the transaction took place (nil if the document is being created).
`:after` | Write | The document after the transaction took place (nil if the document is being deleted).
`:db-before` | Write | A Crux DB from before the transaction takes place.
`:db-after` | Write | A speculative Crux DB from after the transaction would take place (created with `crux.api/with-tx`).
`:server-timestamp` | Write | The value used to replace occurrences of `:db/server-timestamp` in the transaction.

As a convenience, the second argument passed to `authorize` is the relevant
document: `doc` for read operations, and either `before` or `after` for
write operations. An `:update` rule will receive two additional arguments:

```clojure
(defmethod authorize [:user :update]
  [sys before after]
  ...)
```

# Transactions

Biff has its own transaction format, patterned after Firebase write operations.
Transactions are a map from "idents" (a tuple of the doc type and the doc ID)
to "TX docs" (maps that are used to infer what the new documents should be).
For example, here's a transaction for creating a new user:

```clojure
(biff.crux/submit-tx
  sys
  {[:user #uuid "some-uuid"] {:user/email "username@example.com"}})
```

If a document with that ID doesn't exist yet, then this will
be normalized to the following Crux transaction:

```clojure
[[:crux.tx/match #uuid "some-uuid" nil]
 [:crux.tx/put {:crux.db/id #uuid "some-uuid"
                :user/email "username@example.com"}]]
```

`biff.crux/submit-tx` adds match operations for each document in the
transaction. If there's contention, `submit-tx` will retry up to three more
times (first it'll wait 1 second, then 2, then 4).

By default, doc types are enforced, but authorization rules are not. This is
suitable for transactions created by trusted code. If you're receiving a
transaction from the front end, you should enable authorization rules:

```clojure
(biff.crux/submit-tx
  (assoc sys :biff.crux/authorize true)
  tx)
```

`biff.crux/submit-tx` also calls `crux.api/await-tx` (so it can make sure the
match operations succeeded).

And of course, you can always use `crux.api/submit-tx` directly if needed (e.g.
if you need to use a transaction function or set valid time explicitly).

### TX docs

TX docs are converted to Crux documents with
[`biff.crux/normalize-tx-doc`](https://biff.findka.com/codox/biff.crux.html#var-normalize-tx-doc)
like so.

If the ident doesn't include a doc ID, then the server will generate a random
UUID:

```clojure
{[:msg] {:msg/text "hello"}}
```

If you want to create multiple documents of the same type with random IDs, use
nested vectors instead of a map.

```clojure
[[[:messages] {:text "a"}]
 [[:messages] {:text "b"}]]
```

`:db/server-timestamp` is replaced by the server with the current time.

```clojure
{[:msg] {:msg/sent-at :db/server-timestamp
         ...}}
```

If `:db/update` is true, the given document will be merged with an existing
document, failing if the document doesn't exist. There's also `:db/merge` which
simply creates the document if it doesn't exist (i.e. upsert).

```clojure
{[:chatroom #uuid "some-uuid"] {:db/update true
                                :chatroom/title "Existing chatroom"}
 [:chatroom #uuid "another-uuid"] {:db/merge true
                                   :chatroom/title "New or existing chatroom"}}
```

You can `dissoc` document keys by setting them to `:db/remove`. You can
delete whole documents by setting them to `nil`.

```clojure
{[:user #uuid "some-user-id"] {:db/update true
                               :user/display-name :db/remove}
 [:order #uuid "some-order-id"] nil}
```

You can add elements to a set with `:db/union` and remove them with
`:db/difference`:

```clojure
{[:game #uuid "old-game-uuid"]
 {:db/update true
  :game/players [:db/difference "my-uid" "your-uid"]}

 [:game #uuid "new-game-uuid"]
 {:db/update true
  :game/players [:db/union "my-uid" "your-uid"]}}
```

Similarly, you can increment numbers with `:db/add`:

```clojure
{[:store #uuid "some-uuid"] {:db/update true
                             :store/bananas [:db/add 3]
                             :store/oranges [:db/add -92]}}
```

You can use maps as composite IDs. In this case, all keys in the document ID
will be duplicated in the document itself. This allows you to use document ID
keys in your queries.

```clojure
{[:rating {:rating/user #uuid "some-user-id"
           :rating/item #uuid "some-item-id"}]
 {:rating/value :like}}

=> [:crux.tx/put
    {:crux.db/id {:rating/user #uuid "some-user-id"
                  :rating/item #uuid "some-item-id"}
     :rating/user #uuid "some-user-id"
     :rating/item #uuid "some-item-id"
     :rating/value :like}]
```

### Receiving transactions

Receiving transactions from the front end is trivial with websockets:

```clojure
; front end
(let [tx ...]
  (send-event [:example/tx tx]))

; back end
(defmethod api :example/tx
  [sys tx]
  (biff.crux/submit-tx (assoc sys :biff.crux/authorize true) tx))
```

If you're doing server-side rendering, you can also submit transactions via an
HTML form POST, but you'll need an additional helper function:

```clojure
(defn form-tx [req]
  (let [[biff-tx path] (biff.misc/parse-form-tx
                         req
                         ; This lets you coerce input field values to EDN values.
                         {:coercions {:text identity}})]
    (biff.crux/submit-tx (assoc req :biff.crux/authorize true) biff-tx)
    {:status 302
     :headers/location path}))

(defn ssr [{:keys [biff/uid biff.crux/db params/updated]}]
  ...
  (let {{:user/keys [display-name likes-cheese]} (crux.api/entity @db uid)}
    [:form {:action "/api/form-tx"
            :method "post"}
     [:input {:type "hidden"
              :name "__anti-forgery-token"
              :value ring.middleware.anti-forgery/*anti-forgery-token*}]
     [:input {:type "hidden"
              :name "tx-info"
              :value (pr-str
                       {:tx {[:user uid] {:db/update true
                                          :user/display-name 'display-name
                                          :user/likes-cheese 'likes-cheese}}
                        :fields '{display-name :text
                                  likes-cheese :checkbox}
                        :redirect ::ssr
                        :query-params {:updated true}})}]
     [:div "Display name"]
     [:input {:name "display-name"
              :type "text"
              :value display-name}]
     [:div "Like cheese?"]
     [:input {:name "likes-cheese"
              :type "checkbox"
              :checked (when likes-cheese "checked")}]
     [:button {:type "submit"} "Update"]])
  (when updated
    [:div "Transaction submitted."])
  ...)

(def routes
  [["/app/ssr" {:get #(biff.rum/render ssr %)
                :name ::ssr
                ; The client can only specify redirects to routes that set
                ; this.
                :biff/redirect true}]
   ["/api/form-tx" {:post form-tx}]])
```

# Subscriptions

Use [`biff.client/init-sub`](https://biff.findka.com/codox/biff.client.html#var-init-sub)
to manage subscriptions on the front end. For example:

```clojure
(def subscriptions
  (atom #{[:example/sub {:doc-type :user
                         :where '[[:user/name name]
                                  [:user/age age]
                                  [(<= 18 age)]]}]}))

(def sub-results (atom {}))

(biff.client/init-sub
  {:url "/api/chsk"
   :subscriptions subscriptions
   :sub-results sub-results})

; Wait for it...

@sub-results
=> {{:doc-type :user
     :where '[[:user/name name]
              [:user/age age]
              [(<= 18 age)]]}
    {:user {#uuid "some-uuid" {:crux.db/id #uuid "some-uuid"
                               :user/name "Hoid"
                               :user/age 43}}}}
```

If you want to subscribe to a query, `swap!` it into `subscriptions`. If you
want to unsubscribe, `swap!` it out. Biff will populate `sub-results` with the
results of your queries and remove old data when you unsubscribe.

You can use
[`defderivations`](https://biff.findka.com/codox/biff.rum.html#var-defderivations)
to define your subscriptions as a function of your application state, and to
derive your application state from the contents of `sub-results`.

### Subscription query format

Each element of `subscriptions` is a websocket (Sente) event. The event ID
corresponds to one of your back-end event handlers (see
[biff.crux/handle-subscribe-event!](https://biff.findka.com/codox/biff.crux.html#var-handle-subscribe-event.21)),
and the event data is a Biff query.

A Biff query is basically a Crux query without joins. Since all clauses apply
to the same document, we omit the document ID logic variable. We also include a
doc-type.

```clojure
{:doc-type :user
 :where '[[:user/name name]
          [:user/age age]
          [(<= 18 age)]]}
```

By default, the only operators allowed are `#{= not= < > <= >= == !=}`. If you want
to use other functions, you'll have to whitelist them in your system map.

```clojure
(biff.util/start-system
  {:biff.crux/fn-whitelist ['even? 'example.core/likes-cheese?]
   ...
```

You can subscribe to a specific document by providing the document ID in your
query:

```clojure
{:doc-type :user
 :id #uuid "some-uuid"}
```

If you want to load a query but you don't actually care about getting updates
when the results change, use `:static`:

```clojure
{:doc-type :user
 :id #uuid "some-uuid"
 :static true}
```

### Subscription interface

`biff.client/init-sub` is not coupled to Crux subscriptions. You can provide
other kinds of subscriptions as long as you define an appropriate event
handler. For example, if `subscriptions` is set to `#{[:example/foo :ant-info]}`,
the back end would receive an event of `[:example/foo {:query :ant-info
:action :subscribe}]`. Your event handler would need to send back an event of
the form `[:example/foo {:query :ant-info :ident->doc ...}]` where
`:ident->doc` looks something like this:

```clojure
{[:ant :harry] {:ant/id :harry
                :ant/likes "orange juice"}
 [:ant :sally] {:ant/id :sally
                :ant/likes "investing"}}
```

You'd also need to send another event whenever the query results change, and
you'd need to clean up any subscription state if the client's websocket
connection ends. `:action` could also be `:unsubscribe` or `:reconnect`, so
you'd need to handle those too (`:reconnect` means that the websocket
connection is being re-established, but the client still has previous query
results).

# Authentication

The project template comes with some routes for email link authentication. When
a user signs in/signs up, Biff will email them a link containing a JWT. If they
click the link, Biff stores their user ID (the Crux doc ID of their user
document, a UUID) in an [encrypted session
cookie](https://ring-clojure.github.io/ring/ring.middleware.session.cookie.html). You
must provide a Mailgun API key (or define your own function for sending emails),
otherwise the login links will only be printed to the console.

A nice thing about this setup is that the implementation is extremely simple
and it gets the job done. You also don't have to model unconfirmed email
addresses since user documents aren't created until they've clicked the link
you sent. However, implementing more authentication methods (especially
password) is a priority. See
[#18](https://github.com/jacobobryant/biff/issues/18).

Other notes:

 - The CSRF token is provided in another (non-http-only) cookie so that SPA pages
can include it with request headers.
 - Ring requests will include the authenticated user ID under the `:biff/uid` key.
 - The project template will use reCAPTCHA v3 for bot detection if you provide
   a secret key.

# System map

The [system map](#system-composition) is merged with incoming Ring requests and
Sente events. It's also passed to authorization methods. Thus, your application code
can access all configuration and resources via the system map. Some notable keys:

 - `:biff.crux/node`: The Crux node.
 - `:biff.crux/db`: A `delay`ed Crux DB, set by middleware. It's created with
   `biff.crux/open-db`. If you don't deref the DB, it won't be created.
 - `:biff/handler`: The Ring handler, created from your Reitit routes.
 - `:biff.reitit/router`: You can use this to look up routes by name.
 - `:biff.sente/*`: All keys returned by `sente/make-channel-socket!` are prefixed
    with `biff.sente` and merged into the system map.

# Recipes

## Scheduled tasks

See [#87](https://github.com/jacobobryant/biff/issues/87). In the mean time,
you can use [chime](https://github.com/jarohen/chime), as long as you don't
need to coordinate multiple servers:

```clojure
(defn my-task [sys]
  (println "This task will run every hour,"
           "starting 5 minutes after system start."))

(def recurring-tasks
  [{:offset-minutes 5
    :period-minutes 60
    :task-fn #'my-task}])

(defn use-chime [sys]
  (update sys :biff/stop into
          (for [{:keys [offset-minutes period-minutes task-fn]} recurring-tasks]
            (let [closeable (chime.core/chime-at
                              (->> (biff.util/add-seconds (java.util.Date.)
                                                          (* 60 offset-minutes))
                                   (iterate #(biff.util/add-seconds % (* period-minutes 60)))
                                   (map #(.toInstant %)))
                              (fn [_] (task-fn sys)))]
              #(.close closeable)))))
```

## Babashka tasks

If you need to add more complicated tasks, you may want to start using [bb
tasks](https://book.babashka.org/#tasks) instead of the `task` shell script.
See Biff's own [bb.edn file](https://github.com/jacobobryant/biff/blob/master/bb.edn)
for an example.

## Server-side rendering

If you're making a simple site, a SPA might be overkill. If so, you can omit
the `use-sente`, `use-crux-sub-notifier`, and `use-shadow-cljs` components, as
well as all the ClojureScript code and the websocket event handlers.

[As mentioned](#receiving-transactions), you can still use Biff's handy dandy,
auto-authorized transactions via HTML forms.

## Replacing Rum

You can use Reagent/Re-frame/etc instead if you like. Mainly you'll just need a
replacement for `biff.rum/defderivations`.

## Other deployment options

Deploying to [Render](https://render.com) would be interesting, though probably
not worth the price increase over a plain VPS (3-4x more than a DigitalOcean
droplet for equivalent RAM last I checked) unless you're planning to scale
fast. You'll need to make a Dockerfile. I also don't know if they let you
expose TCP ports, which is necessary for nREPL. (nREPL can work [over
HTTP](https://blog.jakubholy.net/nrepl-over-http-with-drwabridge-in-2020/), but
I'm not aware of any editors that support it).

# Contributing

There are several ways you can help out:

 - Use Biff and let me know what problems you run into.
 - Blog about using Biff (I'll list articles under [Additional
   resources](#additional-resources)).
 - Submit PRs. See the [issues](https://github.com/jacobobryant/biff/issues).

The easiest way to hack on Biff is to run `bb libs:dev`, start a new project
(see [Getting started](#getting-started)) and then change the `biff/main` and
`biff/dev` dependencies in `deps.edn` to `{:local/root
"/path/to/cloned/biff/repo/..."}`. You can also include the `biff/tests`
library.

## Documentation

You'll need Babashka and Ruby; then run:

```shell
cd slate
gem install bundler
bundle install
cd ..
```

After that, you can run `bb slate:dev` and edit `slate/source/index.html.md`
to work on the documentation. See the [Slate
README](https://github.com/jacobobryant/biff/tree/master/slate).

# Troubleshooting

### `clojure crashed, killed by SIGABRT.` on Mac

Try using AdoptOpenJDK (see [crux#894](https://github.com/juxt/crux/issues/894)).

### `UnsatisfiedLinkError` on M1 Mac

This is a RocksDB issue, see
[rocksdb#7720](https://github.com/facebook/rocksdb/issues/7720). In the mean
time you can run a [different JDK with
Rosetta](https://itnext.io/how-to-install-x86-and-arm-jdks-on-the-mac-m1-apple-silicon-using-sdkman-872a5adc050d).

Example of full error message:

`Execution error (UnsatisfiedLinkError) at java.lang.ClassLoader$NativeLibrary/load0 (ClassLoader.java:-2). /private/var/folders/ns/8_1zl3n134d5dlkdscjntbh40000gn/T/crux_rocksdb-6.12.7/librocksdbjni-osx.jnilib: dlopen(/private/var/folders/ns/8_1zl3n134d5dlkdscjntbh40000gn/T/crux_rocksdb-6.12.7/librocksdbjni-osx.jnilib, 1): no suitable image found. Did find: /private/var/folders/ns/8_1zl3n134d5dlkdscjntbh40000gn/T/crux_rocksdb-6.12.7/librocksdbjni-osx.jnilib: mach-o, but wrong architecture /private/var/folders/ns/8_1zl3n134d5dlkdscjntbh40000gn/T/crux_rocksdb-6.12.7/librocksdbjni-osx.jnilib: mach-o, but wrong architecture`

-->
