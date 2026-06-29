# biff.graph (almost released but not quite)

Structure your data model as a queryable graph.

biff.graph allows you to query both your database and your business
logic/derived data as a unified, extendable graph. Data model code can be
split up into small, independent chunks ("resolvers"), and application code
can declare the shape of the data it needs without having to know how to get
that data. This makes your codebase easier to understand and test, especially
as it grows larger.

biff.graph is basically a lightweight version of
[Pathom](https://pathom3.wsscode.com/). It implements only a subset of
Pathom's functionality with the intention of being easier to understand. The
biggest difference is that biff.graph has no query planning step, so it may
not execute some queries as efficiently as Pathom. (It does support batch
resolvers and caching, though.) On the flip side, biff.graph's codebase is
only about 600 lines, with the [query execution
code](src/com/biffweb/graph/impl/query.clj) being about 200 lines.

I made biff.graph because I've loved using Pathom both at work and for side
projects, but I was concerned about including it as part of Biff by default.
For people working on small projects who haven't even heard of Pathom yet, I'm
not extremely confident that the code structure benefits would outweigh the
extra learning effort required. So biff.graph is an attempt to see how
approachable I can make this graph data modeling pattern. Biff users who want
to stick with biff.graph will still have to learn the same conceptual ideas as
they would for Pathom about how to structure code; however, the next
step&mdash;understanding what biff.graph is actually doing so you can debug
your code when things go wrong&mdash;should hopefully be easier.

### Dependency

```clojure
com.biffweb/graph {:mvn/version "..."}
```

### Status

This library will be a release candidate until all [the other Biff 2
libraries](/README.md) have been released. Until then there could be breaking
changes, but I don't anticipate any.

## API Reference

[com.biffweb.graph](/docs/api/com.biffweb.graph.md)

## Concepts

- biff.graph uses a slightly modified subset of
  [EQL](https://github.com/edn-query-language/eql) / [Datomic pull
  patterns](https://docs.datomic.com/query/query-pull.html) to describe the
  shape of data.

- "Resolvers" are functions with associated input and output queries. The
  function takes data in the shape of the input query and returns data in the
  shape of the output query.

- After you define a bunch of resolvers, biff.graph's query engine uses them
  to return data in whatever shape you query for.

## Example

In this snippet:

1. We define a couple simulated database-access resolvers (`rss-feed` and
   `post`) which return an entity for a given primary key.

2. We define a derived-data resolver (`clean-post-title`) which takes a
   `:post/title` attribute and returns a version with emojis filtered out.

3. We query our data model graph via `com.biffweb.graph/query`, without needing to
   know which attributes come from the database and which are derived.

```clojure
(require '[com.biffweb.graph :as biff.graph :refer [defresolver]])
(require '[clojure.string :as str])

(defresolver rss-feed
  {:input  [:rss-feed/id]
   :output [:rss-feed/title]}
  [_ctx {:rss-feed/keys [id]}]
  (get {1 {:rss-feed/title "My Blog"}}
       id))

(defresolver post
  {:input  [:post/id]
   :output [:post/title
            :post/url
            {:post/rss-feed [:rss-feed/id]}]}
  [_ctx {:post/keys [id]}]
  (get {2 {:post/url      "https://example.com/my-post"
           :post/title    "My Post 🎅"
           :post/rss-feed {:rss-feed/id 1}}
        3 {:post/title    "My Other Post 🎅"
           :post/rss-feed {:rss-feed/id 1}}}
       id))

(defn remove-emojis [s]
  (str/replace s #"🎅" ""))

(defresolver clean-post-title
  {:input  [:post/title]
   :output [:post/clean-title]}
  [_ctx {:post/keys [title]}]
  {:post/clean-title (-> title
                         remove-emojis
                         str/trim)})

(def resolvers [rss-feed
                post
                clean-post-title])

(def ctx (biff.graph/new-ctx resolvers))

(biff.graph/query ctx
                  [{:post/id 2}
                   {:post/id 3}]
                  [:post/id
                   :post/clean-title
                   ;; Optional attributes are denoted with [:? ...]
                   [:? :post/url]
                   ;; Join attributes are denoted with nested maps
                   {:post/rss-feed [:rss-feed/title]}
                   ;; An optional join attribute looks like this:
                   ;; {[:? :post/rss-feed] [:rss-feed/title]}
                   ])
;; =>
[{:post/id           2,
  :post/clean-title "My Post",
  :post/url         "https://example.com/my-post",
  :post/rss-feed    {:rss-feed/title "My Blog"}}
 {:post/id          3,
  :post/clean-title "My Other Post",
  :post/rss-feed    {:rss-feed/title "My Blog"}}]
```

## Usage

### Defining resolvers

First, you'll typically want to have some kind of function that can
autogenerate resolvers (using `com.biffweb.graph/resolver` rather than
`defresolver`) based on your database schema (e.g. `post` and `rss-feed` from
the example above would be autogenerated). Guidelines:

- There should be one resolver per table / entity type.

- The input query should be just the primary key (e.g. `[:person/id]`).

- The output query should include all the other columns / attributes (e.g.
  `[:person/age, :person/favorite-color, ...]`).

- The output query should also include a join key for each foreign key / ref
  attribute in the entity, and the join subquery should be the primary key for
  that entity (e.g. `[{:person/pet [:pet/id]}, ...]`).

- The resolver options should include `:batch true` so that your database
  query can fetch multiple entities at once. Resolvers with this setting
  receive a vector of input maps and must return a vector of output maps in
  the same order.

- The resolver function then needs to basically do a `SELECT *` for each of
  the input primary keys and ensure that join keys are present and formatted
  as nested maps.

The not-yet-released biff.sqlite library includes [such a
function](https://github.com/jacobobryant/biff/blob/9da3a06d0207cc7c60b8fd8ec5d49bc46894f07a/libs/sqlite/src/com/biffweb/sqlite.clj#L253)
for sqlite (content warning: unedited AI code). If you're using Datomic, you
basically just need to have the resolvers do a `(d/pull-many db '[*] ...)`.

Then you can define whatever additional resolvers you think would be helpful
with `com.biffweb.graph/defresolver`. You can move logic from helper functions
to resolvers gradually as needed. See [the API
docs](/docs/api/com.biffweb.graph.md) for more details about writing
resolvers.

Finally you pass your resolvers to `com.biffweb.graph/new-ctx` which does some
simple indexing needed by the query engine. `new-ctx` also wraps your
resolvers with some middleware that handles things like caching and
validation.

### Running queries

After your resolvers are defined, you can run queries from wherever needed,
such as at the start of a Ring request handler:

```clojure
(defn settings-page [{:keys [session] :as ctx}]
  (let [user (biff.graph/query ctx
                               {:user/id (:uid session)}
                               [:user/email
                                :user/display-name
                                :user/subscribed])]
    ...))
```

The example above assumes you have middleware that merges the output of
`com.biffweb.graph/new-ctx` into incoming Ring requests.

### Debugging

Exceptions thrown from inside `com.biffweb.graph/query` include a
`:biff.graph/trace` key in the exception data which tells you what the query
engine's location in the graph traversal was when the exception occurred
(which part of your query did the exception come from, which resolvers were we
trying to resolve the input for, ...). You can use this information to produce
a minimal repro if needed by focusing your query on just the path that failed.

### Testing

Resolver functions are stored under the `:biff.graph/resolve-fn` key. They
take a single argument (the `ctx` map) and expect resolver input to be stored
under `:biff.graph/input`.

```clojure
(defresolver my-resolver
  {:input [:user/id]
   :output [...]}
   [ctx input]
  ...)

(deftest test-my-resolver
  (is (= ((:biff.graph/resolve-fn my-resolver)
          {:biff.graph/input {:user/id 1}})
         ...)))
```

### biff.fx integration

If you use [biff.fx](../fx/), you can merge `com.biffweb.graph/fx-handlers` in
to your handlers map (or include `com.biffweb. It exposes
`com.biffweb.graph/query` under the `:biff.graph.fx/query` key:

```clojure
(require '[com.biffweb.fx :refer [defmachine]])

(defmachine do-something
  :start
  (fn [{:keys [session]}]
    {:user [:biff.graph.fx/query
            {:user/id (:uid session)}
            [:user/email
             ...]]
     :biff.fx/next :next})
  ...)
```

If you want to use biff.fx to handle effects inside your resolvers (such as
database queries or external service calls), `defresolver` also supports a
form where you define the resolver body as a biff.fx machine:

```clojure
(defresolver my-resolver
  {:input [...]
   :output [...]}

  :start
  (fn [ctx input]
    ...)

  :next
  (fn [ctx input]
    ...))
```

### biff.core integration

The `com.biffweb.graph/module` function returns a [biff.core](../core/) module
which (1) provides a `:biff.fx/handlers` map so you don't have to merge
`fx-handlers` in explicitly, (2) collects `:biff.graph/resolvers` (a vector of
resolvers) from the other modules and merges the result of
`com.biffweb.graph/new-ctx` into the system map.

## Tips

- It can make sense sometimes to write "global" resolvers that don't have an
  input query. For example, a background job might want to query for all the
  users in the database who meet certain criteria. It can also be convenient
  to write resolvers that return data from `ctx`, like a `:output {:session/user
  [:user/id]}` resolver so you don't have to get the user ID from the session
  explicitly.

- In that vein, for authorization I've been using "params" resolvers that take
  entity IDs from path / query params, ensure the current user is authorized
  to read the given entity, and then return it as a join (like `:output
  [{:params/widget [:widget/id]}]`. If the user isn't authorized, you can
  either throw an exception or simply omit the entity from the resolver
  output. There are ways to convert either approach into a 4XX HTTP response;
  I'm still experimenting myself.
