## A. Project structure

1. `model` namespaces expose biff.core modules that mostly contain biff.graph
   resolvers. They do not include Reitit routes, scheduled tasks, or queues.
   Resolvers in `model` do not return hiccup vectors.

2. `app` namespaces contain ring handlers for rendering web pages, including
   internal endpoints needed by those web pages (`:biff.ring/routes`). They do
   not include API routes (`:biff.ring/api-routes`), biff.graph resolvers,
   scheduled tasks, or queues.

3. Each `app` namespace has at most one page-rendering handler (GET handler that
   returns HTML).

4. `app` namespaces should use a hierarchy that reflects the UI hierarchy. e.g.
   if there's a parent page with 3 child pages, the child namespaces should use
   the parent namespace with an additional segment. e.g.
   `com.example.app.parent` and `com.example.app.parent.child1`.

5. `work` namespaces contain biff.background queues and scheduled tasks. They do
   not include Reitit routes or biff.graph resolvers.

6. Each `work` namespace should have only one "workflow," i.e. a set of
   scheduled tasks / queues that trigger each other.

7. `uicomp` namespaces contain biff.graph resolvers that return hiccup vectors.
   They do not include biff.graph resolvers that don't return hiccup, Reitit
   routes, scheduled tasks, or queues.

8. `api` namespaces contain external API routes (`:biff.ring/api-routes`). They
   do not include internal API routes (`:biff.ring/routes`), biff.graph
   resolvers, scheduled tasks, or queues.

9. `model`, `app`, `work`, `uicomp` and `api` namespaces are all "module"
   namespaces and must expose their functionality via biff.core modules. They
   must not be required by any namespace other than the `modules.clj` file which
   constructs a vector of all the modules. Module namespaces must not require
   each other.

10. biff.fx handlers (`:biff.fx/handlers`) should be defined centrally in a
    single `fx.clj` file which exposes a biff.core module. `model`, `app`,
    `work`, `uicomp` and `api` namespaces must not define any fx handlers.

11. Functions used by two or more module namespaces must go under a `lib`
    namespace.

12. Functions used by only one module namespace must go in that module namespace.

13. Namespaces should not be named generically like `util` if their contents can
    be reorganized into groupings with more semantic names.

14. Keep state in the system map. e.g. if you need an atom, you can make a
    biff.core module like `{:biff.core/init {:com.example/my-atom (atom {})}}`.

15. Don't put code outside of one of the places discussed above if one of those
    places would be a good fit for the code.

## B. biff.fx

1. Effectful functions should be written as biff.fx pipelines (or plain machines
   if more advanced control flow is needed), using injected parameters as
   needed: `:biff.fx/now`, `:biff.fx/random-uuid7-seq`,
   `:biff.fx/random-uuid4-seq`, `:biff.fx/seed`. Any effect not implemented as a
   biff.fx handler is a red flag.

2. biff.fx state functions must be pure. They must not call other biff.fx
   machines/pipelines. They must use fx handlers when doing database or
   biff.graph queries. If the project uses an immutable database like XTDB or
   Datomic, database read queries may be done inside state functions.

3. Don't use `:biff.fx/return` unnecessarily: it is only needed when you want to
   return early from a pipeline or if you want to have a machine state function
   run an effect and return something other than a map.

4. Machines / pipelines should be defined with the fewest number of state
   functions possible. For example, a pipeline biff.graph resolver that does a
   one-to-many join can often be written like `{:user/pets
   [:biff.sqlite.fx/execute ...]}`.

5. Do not define biff.fx handlers if there's an existing fx handler from a biff
   library you can use. biff.sqlite and biff.graph already provide handlers for
   database and biff.graph queries/transactions, and biff.fx provides a handler
   for doing HTTP requests.

6. If you must define any fx handlers, they should be as low level as possible.
   Handlers should not involve any application logic, they should have as little
   code as possible, and they should be focused only on the IO they're
   performing. "Take some input, do the IO, return the output." Instead of
   trying to put shared logic in fx handlers, it's better to write shared logic
   as pure functions that can be used by biff.fx state functions.

7. When multiple state functions are used together in multiple pipelines, you
   should define a vector of state functions and then use the sequence form of
   `defpipeline` like `(def pipeline-fns [...]) (defpipeline my-pipeline (concat
   [...] pipeline-fns [...]))`. For machines, you should do something similar
   but with maps: `(def state-fns {...}) (defmachine my-machine (merge state-fns
   {...}))`.

8. As state functions are updated, they should not retain any parameters that
   aren't passed in by other state functions (or by callers of the
   machine/pipeline). Tests should be updated to not pass in any parameters that
   are never passed in by application code.

## C. biff.graph

1. biff.graph resolvers must be placed in a namespace whose last segment matches
   the namespace of one of the top-level keys in the resolver's input or
   output. e.g. a resolver with `:input [:foo/a] :output [:bar/b]` must go in
   either a `foo.clj` or a `bar.clj` file.

2. Functions that compute domain data should be implemented as biff.graph
   resolvers in `model`, not as shared functions in `lib`. For example, a
   `(defn full-name [{:user/keys [first-name last-name]}] (str first-name
   last-name))` function should be turned into a resolver that returns
   `{:user/full-name ...}`. Any function in `lib.*` whose input data comes
   entirely from attributes that can be returned by existing biff.graph
   resolvers is a red flag (including functions that return hiccup: those can be
   placed in `uicomp`).

3. Database read queries should almost always be encapsulated within biff.graph
   resolvers in `model`. Any code outside `model` that runs a database read
   query is a red flag, especially if it's in `app`.

4. Ring handlers should typically have an initial fx vector that does a
   biff.graph query, e.g. `(defpipeline my-page [:biff.graph.fx/query
   [{:session/user [...]}] ...)`.

5. Parameters from the incoming request should be extracted via biff.graph
   resolvers. e.g. `(defresolver thing {:input [{:session/user [user/id]}]
   :output [{:request/thing [:thing/id]}]} (fn [ctx _] ...) ...)` could extract a
   "thing" ID from the request's path or query params and ensure the current user
   is authorized to read that entity. Then Ring handlers can use an initial fx
   vector like `[:biff.graph.fx/query [{:request/thing [:thing/foo ...]}]]`. The
   output namespace must be `request`.

6. Resolvers should not duplicate functionality provided by the database
   adapter's resolvers, which should take an entity's primary key and return its
   other attributes. e.g. a query like `{:user/id 1} [:user/email]` works
   without defining custom resolvers.

7. When you need to query for an entity given an attribute other than the
   primary key (e.g. looking up a user based on their email), you should use a
   resolver like `(defresolver by-email {:input [:user/email] :output [:user/id]
   :batch true} ...)` that returns just the primary key, and then you can rely
   on a separate resolver that takes the primary key and returns all the fields.
   Note that biff.sqlite autogenerates resolvers that take primary keys and also
   resolvers that take unique keys.

8. `[:*]` should not be used in biff.graph queries if you are able to enumerate
   the specific attributes needed.

9. One-to-many-joins should be written like `{:input [:user/id] :output
   [{:user/pets [:pet/id]}] :batch true}`. Note that biff.sqlite autogenerates
   one-to-many join resolvers if you set `:backref-many`.

10. Each entity in the input/output query should use the same namespace (or at
    least be from the same database table/entity type, if a single database
    entity can have multiple attribute namespaces). e.g. good: `[:foo/a
    :foo/b]`, bad: `[:foo/a :bar/b]`. The domain model graph defined by the
    resolvers is meant to be a conceptual extension of the database domain
    model. The only exception is attributes starting with `:global/`; those may
    be mixed with attributes of other namespaces.

11. Resolvers that query the database should only query for attributes included
    in their `:output` query or otherwise used by the resolver. e.g. a resolver
    with `:output [{:user/pets [:pet/id]}]` should do `:select [:pet/id]`
    instead of `:select [:*]`.

12. For each resolver, the top-level keys from both the input and output queries
    must all share a single namespace, e.g. if your input keys have a `foo`
    namespace then your output keys should also have a `foo` namespace. Keys
    with `global` for the namespace are an exception.

13. When a resolver reads parameters from the request, it must return a join key
    with a `request` namespace. e.g. `:output [{:request/pet [:pet/id]}]` or
    `:output [{:request/search-posts [:post/id]}]`.

14. Resolvers that have no input query and do not use any data from the request
    should have an output query with `global` as the namespace for top-level
    keys.

15. Resolver input queries must prefix keys with `:?...` (e.g. `:?foo/bar`) to
    explicitly mark optional keys, i.e. keys that may not be present but whose
    absence shouldn't prevent the resolver from running.

16. Don't use "shortcut resolvers." e.g. if your domain graph already lets you
    query for `[{:foo/bars [{:bar/bazs [:baz/id]}]}]`, doing that is better than
    introducing a resolver that lets you do `[{:foo/bazs [:baz/id]}]`.
    Similarly, querying for `[{:user/pets [:pet/email]}]` is better than
    introducing a resolver that lets you query for `[:user/pet-emails]`.
    Resolvers that result in queries that are meaningfully more efficient
    than doing the "regular" traversal (e.g. queries that do aggregations like
    `[:count :*]` or that do `:limit`s, etc) are acceptable.

17. Avoid doing multiple biff.graph queries if you can combine them into a
    single query.

18. Resolvers that read parameters from the request must verify that the current
    user is authorized to read any entities associated with those parameters.
    (Note that `:biff.datastar/tab-id` is already scoped to the current user, so
    it is already verified.)

19. If you're using biff.sqlite or a different database adapter that allows you
    to define backrefs in your schema, do that so you get autogenerated backref
    resolvers. Ref columns/attributes that don't define a backref are a red
    flag.

20. Input/output queries should be colocated with their respective resolvers (or
    query call sites), typically inline, even if that requires duplicating query
    definitions. Having shared queries in `lib.*` is a red flag.

21. Ring handlers that query for a required `:request/...` attribute but don't
    receive it will throw an exception with `:biff.ring/status 400` which causes
    `biff.ring/wrap-internal-error` to return a 400 response. If the default
    response is sufficient (usually it is), the Ring handler should rely on that
    behavior. Otherwise, the Ring handler may query for `:?request/...` instead
    and provide its own error handling.

## D. Authorization rules

1. If the database adapter supports authorization rules (e.g.
   `:biff.sqlite/authorize`), those rules should be up to date with the
   database's schema.

2. Authorization rules should not use any "out of band" information passed in by
   particular call sites: they should only consider the information passed in by
   the database adapter (the before/after records for the transaction itself,
   the state of the database before/after the transaction, etc) and the current
   user ID. Authorization rules are meant to answer the question, "if the user
   could submit arbitrary transactions from the frontend, what would we allow
   them to do?" without respect to any particular code path.

3. When authorization rules cannot infer which user owns a record by looking
   only at that record, they should query the database instead of having the
   call site pass in out-of-band information. e.g. biff.sqlite includes
   `:biff.sqlite/before-conn` and `:biff.sqlite/after-conn` in `ctx` when
   calling the `authorize` function.

4. All transactions that are associated with a particular user (e.g. submitted
   in response to an HTTP request from that user) that can be validated by
   authorization rules should go through authorization rules. If a particular
   transaction cannot be validated properly by authorization rules that are
   following the points above, it's better to not use authorization rules for
   that transaction than to violate the points above.

5. If the project uses biff.fx, an fx handler should be used for transactions
   that are subject to authorization rules (e.g.
   `:biff.sqlite.fx/authorized-write`).

## E. biff.ring

1. Always use `com.biffweb.ring/defpath` to define reitit path templates,
   whether or not they use path parameters.

2. `defpath`s referenced from only one file should be defined in that file;
   other paths should be defined in a shared `routes.clj` file.

3. A path must not have multiple params unless none of the params can be
   inferred from the others. e.g. if there is a one-to-many relationship between
   a `user` and a `pet`, then `/pet/:pet-id` is sufficient to infer the user ID.

4. Don't use `:id` as the name of a path parameter. Always use the entity name
   too, like `:pet-id`.

## F. biff.datastar

1. Application pages (not marketing pages like the landing page) must use
   biff.datastar. e.g. forms should trigger Datastar actions (usually `@post`),
   forms should use Datastar signals, and action handlers (e.g. POST request
   handlers) should return a 204 response.

2. Rendering updates should be handled by letting the biff.datastar middleware
   push updates to the client when backend state changes. action handlers (i.e.
   typically POST request handlers) do not return html; they update backend
   state and let the SSE thread rerender the frontend.

3. Tab-specific state should be stored on the backend, keyed by
   `:biff.datastar/tab-id`.

4. Don't write javascript for things that can be done declaratively with
   Datastar. Especially don't write javascript that mutates the DOM.

## G. Tests

1. Tests for a namespace should go in a namespace of the same name with the
   `-test` suffix, e.g. tests for `com.example.lib.foo` go in
   `com.example.lib.foo-test`.

2. Every biff.fx machine or pipeline should have one or more unit tests for each
   of its state functions. Call the machine/pipeline with zero arguments to get
   the state functions for testing. The tests should cover any conditional
   branches in each state function. The only exception is for state functions
   that return hiccup/html; these do not need unit tests.

3. Every biff.graph resolver in `model` must have at least one unit test.
   Resolvers that use biff.fx should follow G2.

## H. Code style

1. When database schema is defined in a single map (e.g. biff.sqlite's `columns`
   map), the map should separate schema entries for different tables/entity
   types with a blank line. Schema should only be broken on multiple lines if
   it's necessary to avoid lint warnings about line length. If you must use a
   line break but the entry value can fit on a single line, do that:

```
:user/example-attribute
{:type ..., :required ..., ...}
```

2. Agents should not make changes to clj-kondo or cljfmt config unless directed
   by the user. If the max line length has been increased or cljfmt rules have
   been relaxed, that's a red flag.

3. Vector elements should be all on one line or all on different lines. Same for
   map entries.

4. biff.fx pipelines should have blank lines between the state functions.
   machines should have blank lines between the state keyword/function pairs.
   biff.graph resolvers that use biff.fx should also observe this rule.

5. `tick.core` should be used for date/time operations. It should be aliased as
   `tick`.

6. A hiccup-rendering library like `dev.onionpancakes.chassis.core` should be
   used for generating HTML.

7. For keywords you introduce, use the project's main namespace as the keyword
   namespace.

8. SQL queries should be written with HoneySQL whenever possible (e.g. by
   passing a map to a biff.sqlite fx handler).

9. Don't use `?` as a suffix for keywords or local bindings; only as a suffix
   for predicate functions.

10. Don't shadow clojure.core names; e.g. don't call things `name`.

11. Avoid lazy operations when they aren't needed; e.g. prefer `mapv` and
    `filterv` over `map` and `filter`.

12. Don't break a form pair (let bindings, map entries, cond pairs, etc) on to
    separate lines if it can fit on one line without exceeding the max line
    length (as set in clj-kondo config).

13. Don't separate form pairs with a blank line unless it's needed to prevent
    exceeding the max line length.

14. For biff.graph attributes, use `:?foo/bar` syntax instead of `[:? :foo/bar]`
    syntax.
