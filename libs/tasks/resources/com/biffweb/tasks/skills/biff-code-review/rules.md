## A. Project structure

1. `model` namespaces expose biff.core modules that mostly contain biff.graph
   resolvers. They do not include Reitit routes, scheduled tasks, or queues.
   Resolvers in `model` do not return hiccup vectors.

2. `app` namespaces contain ring handlers for rendering web pages, including
   internal endpoints needed by those web pages (`:biff.ring/routes`). They do
   API routes (`:biff.ring/api-routes`), biff.graph resolvers, scheduled tasks,
   or queues.

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

7. `uicomp` namespaces biff.graph resolvers that return hiccup vectors. They do
   not include biff.graph resolvers that don't return hiccup, Reitit routes,
   scheduled tasks, or queues.

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
   functions possible.

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

## C. biff.graph

1. biff.graph resolvers must be placed in a namespace whose last segment matches
   the namespace of one of the top-level keys in the resolver's input our
   output. e.g. a resolver with `:input [:foo/a] :output [:bar/b]` must go in
   either a `foo.clj` or a `bar.clj` file.

2. Functions that compute domain data should be implemented as biff.graph
   resolvers in `model`, not as shared functions in `lib`. For example, a
   `(defn full-name [{:user/keys [first-name last-name]}] (str first-name
   last-name))` function should be turned into a resolver that returns
   `:user/full-name`.

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
   vector like `[:biff.graph.fx/query [{:request/thing [:thing/foo ...]}]]`.

6. Resolvers should not duplicate functionality provided by the database
   adapter's resolvers, which should take an entity's primary key and return its
   other attributes. e.g. a query like `{:user/id 1} [:user/email]` works
   without defining custom resolvers.

## D. biff.sqlite / other database adapters

1. If the database adapter supports authorization rules (e.g.
   `:biff.sqlite/authorize`), those rules should be up to date with the
   database's schema. Authorization rules should allow users to write records
   they "own" (e.g. their user settings) without letting them write records they
   don't own (e.g. other users' settings).

2. When authorization rules cannot infer which user owns a record by looking
   only at that record, they should query the database instead of having the
   call site pass in out-of-band information. e.g. biff.sqlite includes
   `:biff.sqlite/before-conn` and `:biff.sqlite/after-conn` in `ctx` when
   calling the `authorize` function.

3. If database adapter supports authorization rules, then an associated biff.fx
   handler (e.g. `:biff.sqlite.fx/authorized-write`) should be used for writes
   that are initiated by a user.

4. SQL queries should be written with HoneySQL whenever possible (e.g. by
   passing a map to a biff.sqlite fx handler).

## E. biff.ring

1. Always use `com.biffweb.ring/defpath` to define reitit path templates,
   whether or not they use path parameters.

2. `defpath`s referenced from only one file should be defined in that file;
   other paths should be defined in a shared `routes.clj` file.

## F. biff.datastar

1. Application pages (not marketing pages like the landing page) must use
   biff.datastar. e.g. forms should trigger Datastar actions (usually `@post`),
   forms should use Datastar signals, and action handlers (e.g. POST request
   handlers) should return a 204 response.

2. Rendering updates should be handled by letting the biff.datastar middleware
   push updates to the client when backend state changes.

3. Tab-specific state should be stored on the backend, keyed by
   `:biff.datastar/tab-id`.

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
