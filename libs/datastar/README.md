# biff.datastar

A lightweight approach for writing server-side-rendered web apps with Clojure
and [Datastar](https://data-star.dev). The result is ergonomic for both simple
and complex UIs.

This library implements the same general "immediate mode" architecture as
[andersmurphy/hyperlith](https://github.com/andersmurphy/hyperlith):

- Each page in your application starts a long-lived SSE connection.
- Whenever backend state (the database) changes, the entire page is re-rendered.
- If anything in the page changed, the new HTML is compressed with Brotli and
  pushed to the client.
- UI state that needs to be used by your backend rendering logic is stored in
  server-side per-tab state.

Your application only needs to define a single render function per page. Your
POST/etc request handlers update the backend state and return an empty response,
letting biff.datastar handle the UI updates. If your app needs real-time updates
or collaborative features, those basically come for free.

### Dependency

```clojure
com.biffweb/datastar {:mvn/version "2.0.0-rc12"}
```

### Status

This library will be a release candidate until all [the other Biff 2
libraries](/README.md) have been released. Until then there could be breaking
changes.

This architecture is obviously a much less well-trodden path than a typical
request/response setup, and I haven't personally used it at scale. For side
projects, you'll be fine (probably). If you're working on a Serious Project,
you'll want to take some measurements.

Some resources:

- [Anders Murphy's blog](https://andersmurphy.com), e.g. see [Realtime
  collaborative webapps without
  Clojurescript](https://andersmurphy.com/2025/04/07/clojure-realtime-collaborative-web-apps-without-clojurescript.html).
- [A Tale of Two Web Architectures](https://m.youtube.com/watch?v=8W6Lr1hRgXo),
  a case study from Clojure Conj 2025.
- [Interview with David Nolen](https://youtu.be/2ECucq-mTGg).

## Reference

- [Schema](docs/reference/schema.md)
- [API](docs/api/com.biffweb.datastar.md)

## Example

Run `clj -M:demo` to start the demo app, which is, of course, a chat app. Open
multiple tabs to see the realtime updates in action.

View the [demo app source](demo/com/biffweb/datastar/demo.clj). Some parts to
take note of:

- `refresh` is called whenever state changes.
- We have `wrap-sse-render` in the middleware stack.
- The map returned by `new-lock` is passed to both `wrap-sse-render`
  (by merging it into incoming Ring requests) and `refresh`.
- There is only one Ring handler that returns HTML: `chat-page`.
- `chat-page` uses `sse-page-response` to conditionally render the `<html>` and
  `<body>` elements (which include `init-opts`) based on
  `:biff.datastar/sse-request`.

## Usage

### Setup

Add Datastar (JS lib) to your pages:

```clojure
;; See the latest version at https://data-star.dev/guide/getting_started
(def datastar-url
  "https://cdn.jsdelivr.net/gh/starfederation/datastar@v1.0.2/bundles/datastar.js")

[:head
 [:script {:type "module" :src datastar-url}]
 ...]
```

Besides that, there are three functions you need to integrate into your
application:

- [`wrap-sse-render`](docs/api/com.biffweb.datastar.md#wrap-sse-render): add
  this to your Ring middleware. Put it as close to the underlying handler as
  possible since it will call that handler repeatedly.
- [`refresh`](docs/api/com.biffweb.datastar.md#refresh): call this whenever a
  new database transaction is committed. Ideally your database lets you listen for
  changes, but if not you can poll for changes or submit transactions through a
  wrapper function that calls `refresh`.
- [`new-lock`](docs/api/com.biffweb.datastar.md#new-lock): call this on system
  startup to get a map with some state in it, then merge that map into incoming
  Ring requests (so that `wrap-sse-render` gets it) and also pass that map to
  `refresh`.

If you're using [biff.core](/libs/core), [biff.ring](/libs/ring), and a Biff
[database adapter](/docs/db-adapters.md) that implements
[`:biff.core/on-tx`](/libs/core/docs/reference/schema.md#biff-core-on-tx) (such
as [biff.sqlite](/libs/sqlite) or [biff.xtdb](/libs/xtdb)), you can simply add
[`(module)`](docs/api/com.biffweb.datastar.md#module) to your modules vector.

### Writing page handlers

Each page in your application should include
[`init-opts`](docs/api/com.biffweb.datastar.md#init-opts) in an element's DOM opts,
and there should be a wrapper element with an `id` set. On page load,
`init-opts` will trigger another GET request to the same URL that served the
page, with a particular query parameter set that `wrap-sse-render` recognizes.

`wrap-sse-render` will intercept that request and start a long-lived SSE
connection. Whenever `refresh` is called, `wrap-sse-render` will call the
underlying Ring handler and push the returned HTML to the client (as a
[`datastar-patch-elements`](https://data-star.dev/reference/sse_events#datastar-patch-elements)
event), replacing the wrapper element.

When the Ring handler is called in this way, `wrap-sse-render` will set
`:biff.datastar/sse-request true` on the Ring request. When this key is set,
your handler should return only the wrapper element, without `<html>`, `<body>`
etc. and also without `init-opts`. You can use a helper function like this:

```clojure
(require '[dev.onionpancakes.chassis.core :as chassis])
(require '[com.biffweb.datastar :as biff.datastar])

(defn- sse-page-response
  [{:keys [biff.datastar/sse-request]} & content]
  (let [content* [:div#biff-datastar-content content]]
    {:status  200
     :headers {"Content-Type" "text/html; charset=utf-8"}
     :body    (chassis/html
               (if sse-request
                 content*
                 [chassis/doctype-html5
                  [:html {:lang "en"}
                   [:head ...]
                   [:body (biff.datastar/init-opts)
                    content*]]]))}))

(defn my-page [request]
  (sse-page-response
   request
   [:div ...]))
```

### Signals

All your form elements should be bound to [Datastar
signals](https://data-star.dev/guide/reactive_signals), and those signals should
be initialized with `__ifmissing`:

```clojure
[:form {:data-signals__ifmissing "{\"my-signal\":\"abc123\"}"
        ...}
 [:input {:data-bind "my-signal"
          ...}
  ...]
```

This will prevent the input values from being overwritten whan `wrap-sse-render`
pushes new page HTML.

I recommend using namespaced signal names, since signals are essentially a
global key-value store that's shared by all the page's code. biff.datastar has
some helper functions, `signals-json` and `signals-name`, which convert keywords
to string representations that Datastar can handle: periods and slashes are
replaced with underscores.

```clojure
[:form {:data-signals__ifmissing
        (signals-json {::my-signal "abc123"}),
        ...}
 [:input {:data-bind (signal-name ::my-signal)
          ...}
  ...]
```

Datastar includes all the page's signals in requests to your [action
handlers](#writing-action-handlers). `wrap-sse-render` parses these signals and
includes them in a `:biff.datastar/signals` key on the Ring request, with signal
names converted to keywords (and underscores turned back into periods/slashes).

```clojure
(def my-handler [{:keys [biff.datastar/signals] :as request}]
  (let [{::keys [my-signal]} signals]
    ...))
```

Other than for form inputs, you should avoid using signals when possible. Prefer
using backend [tab state](#tab-state). Signals are basically just an
optimization for cases where a network roundtrip is too slow for a particular
interaction (like typing into a text field).

### Writing action handlers

State updates (e.g. form submissions) should be done via [Datastar
actions](https://data-star.dev/guide/backend_requests#backend-actions):

```clojure
[:form {:data-on:submit "@post(el.dataset.action)"
        :data-action    my-handler-path}
 ...]
```

As described [above](#signals), these actions will include all the signals for
the entire page (not just those bound to inputs within the current form), and
`wrap-sse-render` will insert them into the `:biff.datastar/signals` key. Your
Ring handlers can then read the signals and update the backend state (e.g.
submit a database transaction) accordingly. (Per [setup](#setup), this should
cause `refresh` to be called which will in turn trigger a push from
`wrap-sse-render`).

Your handlers can return `{:status 204}`, or they can return a
[`datastar-patch-signals`](https://data-star.dev/reference/sse_events#datastar-patch-signals)
event which will update the signals on the frontend:

```clojure
(defn- my-handler [{:biff.datastar/keys [signals] :as request}]
  (do-something request (::my-signal signals))
  (patch-signals {::my-signal ""}))
```

This can be useful for e.g. clearing an input field after a form submission.

### Tab state

`init-opts` creates a `:biff.datastar/tab-id` signal on page load which you can
use to associate backend state with a particular browser tab. As a convenience,
`wrap-sse-render` sets that key directly on the Ring request so you don't have
to get it from `:biff.datastar/signals`.


```clojure
(defn my-handler [{:biff.datastar/keys [signals tab-id] :as request}]
  (set-tab-state tab-id ...)
  ...)
```

The tab ID is a random UUID. biff.datastar does not provide any tab state
implementation; it only provides the tab ID. You can e.g. create a `tab_state`
table in your database that uses the tab ID as the primary key. You may want
include `created_at` / `updated_at` columns in that table so that you can delete
old tab state for sessions that have expired, if desired. The actual tab state
data could be stored as unstructured (e.g. json/blob) data.

If needed you can use something other than your main database as the tab state
store, such as Redis.

Backend tab state is useful for storing UI state that you want to expose to the
page rendering handler (the one that's used by `wrap-sse-render` to push events
on the SSE connection). For example, for a large virtualized table, you could
store the table's scroll position in tab state.

### CSRF protection

biff.ring's
[`wrap-csrf-protection`](/libs/ring/docs/api/com.biffweb.ring.md#wrap-csrf-protection)
middleware can be used to provide CSRF protection without needing to include a
token with incoming requests. `wrap-csrf-token` is included in biff.ring's
default middleware stack.

Alternatively, if you're using
[ring-anti-forgery](https://github.com/ring-clojure/ring-anti-forgery), you can
add [`wrap-signals`](/libs/ring/docs/api/com.biffweb.ring.md#wrap-signals) to your
middleware so it runs before `wrap-anti-forgery`, and then have your page
rendering code pass the CSRF token to `init-opts`:

```clojure
(defn my-page [request]
  (let [response-body
        [:body (biff.datastar/init-opts
                (select-keys request [:anti-forgery-token]))
         ...]]
    ...))
```

`wrap-signals` will then set the `x-csrf-token` request header to the
`:anti-forgery-token` value so that `wrap-anti-forgery` can see it.

## Tips

- For this approach to work, your page rendering function (including any
  database queries it runs) needs to be fast. If your page must run an expensive
  query (e.g. to fetch search results or recommendations), you can run a `@post`
  action on payload which runs the query and stores the results in tab state.
  Then the page rendering function only needs to read materialized results from
  tab state.

- To model forms with multiple/nested entities, you can use nested signals:

```clojure
[:form {:data-signals__ifmissing
        (signals-json {::my-form {:pets {0 {:pet/name ""}}}})}
 [:input {:data-bind (signal-name [::my-form :pets 0 :pet/name])}]]
```
