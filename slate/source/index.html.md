---
title: Biff

language_tabs: # must be one of https://git.io/vQNgJ
  - Clojure

toc_footers:

includes:

search: true
---

# Work-in-progress notice (14 May 2021)

I am just about to finish a big release which has ended up being almost a
complete rewrite. Biff is now much simpler and easier. The code is done as far
as I'm aware; I'm just working on updating the documentation now. I'm going to
announce the release in a few days. Hopefully most of the documentation will be
done by then. Since the code is working and I have the Getting Started section
updated, I thought I might as well merge to master. In the mean time, I've
commented out all the old documentation.

By the way: application code will stay mostly the same, but there have been
breaking changes in the framework structure. I expect few breaking changes
after this release. If anyone has any Biff apps already, I'd be happy to help
migrate them.

# Introduction

Biff is designed to make web development with Clojure fast and easy [without
compromising](#design-philosophy) on simplicity. It prioritizes small-to-medium
sized projects.

As my schedule allows, I'm happy to provide free mentoring (answering
questions, reviewing code, pair programming, etc) to anyone who wants to learn
Clojure web dev with Biff. I'm also available for consulting if you'd like to
use Biff in your business. In either case, fill out [this quick
survey](https://airtable.com/shrKqm1iT3UWySuxe).

Distinctive features:

- **Query subscriptions**. Specify what data the front end needs declaratively,
  and Biff will keep it synced with the back end.
- **Authorization rules**. No need to set up a bunch of CRUD endpoints. Queries
  and transactions can be submitted from the front end as long as they pass the
  rules you define.
- Built on **Crux**, the world's best database (see
  [opencrux.com](https://opencrux.com)).
- **Biff transactions**, a layer over Crux transactions that provides schema
  enforcement and other conveniences. Patterned after Firebase transactions.
- **Authentication**. Email link for now; password and SSO coming later.
- **Push-to-deploy**. The project template comes with a script for provisioning
  an Ubuntu server. Biff is 12-factor compliant, so you can easily deploy it
  wherever else you choose, too.
- **Great documentation!**

## Status

I've been using Biff in my own projects since May 2020. I now consider it
stable/production ready, with the caveat that it hasn't yet been used seriously
by anyone other than myself as far as I'm aware (hopefully that will change
soon!). See also the [high priority
issues](https://github.com/jacobobryant/biff/issues?q=is%3Aissue+is%3Aopen+label%3A%22high+priority%22).

I've recently decided to switch from entrepreneurship to freelancing and
consulting, and I'm looking for opportunities to build things with Biff (and
train others how to). If you want to support Biff, [let me
know](mailto:contact@jacobobryant.com) if you have any leads.

Websites built with Biff (all mine so far):

- [The Sample](https://sample.findka.com), a newsletter recommender system.
- [Findka Essays](https://essays.findka.com), an essay recommender system.
- [Hallway](https://discuss.findka.com), a discussion aggregator.

If you ship something with Biff, I'll add it to the list.

## Additional resources

 - [An interview](https://soundcloud.com/user-959992602/s4-e27-biff-with-jacob-obryant/s-fpVxrTrP9ZJ) on the ClojureScript Podcast.
 - [An interview](https://console.substack.com/p/console-49) on The Console.
 - [A presentation](https://youtu.be/mKqjJH3WiqI) I gave at re:Clojure 2020 ([slides](https://jacobobryant.com/misc/reclojure-2020-jacobobryant.pdf)).
 - [A presentation](https://www.youtube.com/watch?v=oYwhrq8hDFo) I gave at the Clojure Mid-Cities meetup.

<br>

<p><iframe width="560" height="315" src="https://www.youtube.com/embed/mKqjJH3WiqI" frameborder="0" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture" allowfullscreen></iframe></p>

# Getting started

Requirements:

 - [clj](https://clojure.org/guides/getting_started), including rlwrap (try `which rlwrap`)
 - [node.js](https://nodejs.org/)
 - See also: [Crux quickstart](https://opencrux.com/howto/quickstart.html#_install_dependencies) > Install Dependencies > All System Requirements.

Run this command to create a new Biff project:

```
bash <(curl -s https://raw.githubusercontent.com/jacobobryant/biff/master/new-project.sh)
```

The template project is a minimal CRUD app which demonstrates most of Biff's
features.

NOTE: This page assumes you chose `example.core` for your project's main
namespace. So instead of writing `src/<your project>/some-file.clj`, we'll just
write `src/example/some-file.clj`.

### Develop

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

### Build

Stop the app if it's running, then run `./task build` to generate assets (HTML,
CSS, CLJS) and build an uberjar at `target/app.jar`.

If you use the server setup script below, you won't need to run this. It be will
done on the server after git push.

### Production

Production configuration is stored in `config/prod.env`. You can deploy your
uberjar anywhere as long as you set those environment variables somehow. It's
assumed that you handle SSL elsewhere (e.g. with Nginx) and then proxy requests
to the app on localhost.

Deploying to [Render](https://render.com) would be interesting, though probably
not worth the price increase over a plain VPS (3-4x more than a DigitalOcean
droplet for equivalent RAM last I checked) unless you're planning to scale
fast. You'll need to make a Dockerfile. I also don't know if they let you
expose TCP ports, which is necessary for nREPL. (nREPL can work [over
HTTP](https://blog.jakubholy.net/nrepl-over-http-with-drwabridge-in-2020/), but
I'm not aware of any editors that support it).

### Server setup

The project template includes a script for provisioning an Ubuntu server,
including push-to-deploy. I've tested it with DigitalOcean.

If using DigitalOcean: first create a droplet (Ubuntu 20.04 LTS). I usually do
Regular Intel at $10/month. Add monitoring. Make sure your SSH key is selected.
(If needed, go to Settings and add your SSH key, then start over). Set the
hostname to something distinctive. After the droplet is created, go to
Networking and point your domain to it (create an A record).

Then, update the vars in `config/prod.env` and `config/task.env`. Run the setup
script on your new server, replacing `example.com` with your domain:

```bash
scp infra/setup.sh root@example.com:
ssh root@example.com
./setup.sh
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

## Using the documentation

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

Using maps as document IDs lets you specify composite IDs. In addition, all
keys in the document ID will be duplicated in the document itself. This
allows you to use document ID keys in your queries.

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

Receiving transactions from the front end is trivial with a websockets:

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
  (biff.glue/handle-form-tx
    req
    ; This lets you coerce input field values to EDN values.
    {:coercions {:text identity
                 :checkbox #(= % "on")}}))

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

<!--

# Todo

Add code for experimenting to admin ns
 - auth rules
 - girouette
 - db (already)
 - biff-q


 - Data lifecycle
   - debugging

 - Configuration
 - Crux
    - talk about wrap-db
 - Authentication
 - Transactions
    - WS and Form
 - HTTP handlers
    - go over middleware too
 - Web sockets
 - CSS
 - ClojureScript
 - nREPL
 - Static pages
 - Recipes
    - cron
    - spec instead of malli
    - bb tasks
    - SSR only
    - replacing rum
    - using datomic
 - Contributing


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
                        ; Crux queries sent from the front end. Functions in clojure.core
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

After a user is signed in, you can authenticate them on the back end from an
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

Biff provides a helper function for initializing the web socket connection on the front end:

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

# Contributing

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

## Documentation

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

# FAQ

## Comparison to Firebase

Basically, if you like Firebase and you like Clojure back end dev, you might
enjoy using Biff for your next side project. Same if you like the idea of
Firebase but in practice you have issues with it. If you want something mature
or you like having a Node/ClojureScript back end, Firebase is a great choice. [Here's a non-trivial
example](https://github.com/jacobobryant/mystery-cows) of using Firebase with ClojureScript.

Some shared features:

 - Flexible data modeling
 - Basic query subscriptions (no joins)
 - Client-side transactions
 - Authorization rules
 - Triggers
 - Authentication built-in

Some differences:

 - Biff has a long-running JVM/Clojure back end instead of an ephemeral
   Node/ClojureScript back end => better library ecosystem IMO and lower response
   times/no cold start.
 - Firebase has way more features and is vastly more mature.
 - Biff is open-source + self-hosted => you have total control. If there's anything you don't like, you can fix it.
 - [Crux](https://opencrux.com/) (the database Biff uses) is immutable and has Datalog queries.
 - Authorization rules in Firebase are IMO error-prone and hard to debug.
 - Firebase supports password and SSO authentication.

## Comparison to Fulcro

Similarities:

 - Both contain some code for moving data between front end and back end, hence
   they can both be described as "full-stack frameworks."

Differences:

 - Fulcro is primarily a front-end framework while Biff is primarily back end.
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

-->
