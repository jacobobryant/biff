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
com.biffweb/datastar {:mvn/version "..."}
```

### Status

This library will be a release candidate until all [the other Biff 2
libraries](/README.md) have been released. Until then there could be breaking
changes.

This architecture is obviously a much less well-trodden path than a typical
request/response setup, and I personally haven't used it at scale. For side
projects, you'll be fine (probably). If you're working on a Serious Project,
you'll want to take some measurements.

Some resources:

- [Anders Murphy's blog](https://andersmurphy.com), e.g. see [Realtime
  collaborative webapps without
  Clojurescript](https://andersmurphy.com/2025/04/07/clojure-realtime-collaborative-web-apps-without-clojurescript.html).
- [A Tale of Two Web Architectures](https://m.youtube.com/watch?v=8W6Lr1hRgXo),
  a case study from Clojure Conj 2025.
- [Interview with David Nolen](https://youtu.be/2ECucq-mTGg)

## Reference

- [Schema](docs/reference/schema.md)
- [API](docs/api/com.biffweb.datastar.md)

## Example

Run `clj -M:demo` to start the demo app, which is, of course, a chat app. Open
multiple tabs to see the realtime updates in action.

View the [demo app source](demo/com/biffweb/datastar/demo.clj). Some parts to
take note of:

- `refresh` is called whenever state changes.
- We have `wrap-datastar` in the middleware stack.
- The map returned by `new-lock` is passed to both `wrap-datastar`
  (by merging it into incoming Ring requests) and `refresh`.
- There is only one Ring handler that returns HTML: `chat-page`.
- `chat-page` uses `sse-page-response` to conditionally render the `<html>` and
  `<body>` elements (which include `biff.datastar/init-opts`) based on
  `:biff.datastar/sse-request`.

## Usage

Add Datastar (JS lib) to your pages:


- datastar JS dep
- new-lock, refresh, wrap-datastar (or use module)
- writing a page handler
  - use signals for all form inputs
- writing action handlers
- CSRF
- tab state
    - maybe tab IDs should be full UUIDs so they don't need to be keyed with the
      user ID

## Tips

- use actions to cache expensive page-load queries
- prefer tab state over signals

<!--
here's how I described this in slack:

> I've got an initial draft of a datastar integration hashed out:
> github.com/jacobobryant/biff.datastar. the general structure is:
>
> - you add a little boilerplate to your page rendering: add datastar to your
>   `[:head ...]` section, then render your content like `[:body [:div
>   biff.datastar/init-opts] [:div#biff-datastar-content ...]]`. the `init-opts`
>   thing will open up a long-lived SSE request on the same URL for the current
>   page. there's also some stuff in there for dealing with CSRF.
>
> - you wrap all your routes with a `wrap-datastar` middleware. It detects SSE
>   requests from `init-opts`  and opens an SSE stream. Whenever the middleware
>   receives a signal (via a ReentrantLock) from elsewhere in your app code that
>   something in the database has changed, it calls your handler again and sends
>   the result to the frontend, compressed with brotli. The middleware also sets
>   a `:biff.datastar/sse-request` flag, so when that's true, your handler is
>   supposed to render just the `[:div#biff-datastar-content ...]` bit, not the
>   whole `[:html ...]` thing.
> 
> - `init-opts` also generates a unique tab ID, which `wrap-datastar` puts on a
>   `:biff.datastar/tab-id` key in the incoming ring request (for all handlers,
>   not just the SSE request handler). Your application code is supposed to set
>   up some place to store backend tab-specific state, which can be keyed by the
>   tab ID with the user ID (e.g. `(:uid session)`). I just remembered I haven't
>   done this in the biff-starter-sqlite repo yet, but I'll have that define a
>   `tab_state` sqlite table where you can store arbitrary data (in a BLOB
>   column, serialized with nippy). then you can have your backend action
>   handlers write stuff to the tab state and then your long-lived SSE handler
>   can use that next time it renders.
> 
> - there's a `refresh` function you call that triggers a signal via the
>   ReentrantLock thing, so you're supposed to call that from some central place
>   e.g. wherever database transactions are submitted.
>
> - there's a `module` function that biff apps can use which wires up the
>   middleware and the ReentrantLock and makes sure `refresh` gets called
>   whenever a sqlite transaction is submitted. Non-biff apps can use the lib
>   too as long as they do that wiring-up manually.
> 
> For fun I might experiment with a flag that would have wrap-datastar inject
> the boilerplate stuff for you / extract the `biff-datastar-content` div when
> appropriate, in which case your page handler would basically just be a plain
> database-querying, hiccup-returning ring handler, naive to the fact it's being
> used in an SSE stream.
>
> Oh, and of course the lib comes with a demo chat app.

-->
