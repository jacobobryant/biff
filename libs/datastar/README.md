# biff.datastar (alpha/wip/ai generated)

here's how I described this in slack:

> I've got an initial draft of a datastar integration hashed out: github.com/jacobobryant/biff.datastar. the general structure is:
>
> - you add a little boilerplate to your page rendering: add datastar to your `[:head ...]` section, then render your content like `[:body [:div biff.datastar/init-opts] [:div#biff-datastar-content ...]]`. the `init-opts` thing will open up a long-lived SSE request on the same URL for the current page. there's also some stuff in there for dealing with CSRF.
>
> - you wrap all your routes with a `wrap-datastar` middleware. It detects SSE requests from `init-opts`  and opens an SSE stream. Whenever the middleware receives a signal (via a ReentrantLock) from elsewhere in your app code that something in the database has changed, it calls your handler again and sends the result to the frontend, compressed with brotli. The middleware also sets a `:biff.datastar/sse-request` flag, so when that's true, your handler is supposed to render just the `[:div#biff-datastar-content ...]` bit, not the whole `[:html ...]` thing.
> 
> - `init-opts` also generates a unique tab ID, which `wrap-datastar` puts on a `:biff.datastar/tab-id` key in the incoming ring request (for all handlers, not just the SSE request handler). Your application code is supposed to set up some place to store backend tab-specific state, which can be keyed by the tab ID with the user ID (e.g. `(:uid session)`). I just remembered I haven't done this in the biff-starter-sqlite repo yet, but I'll have that define a `tab_state` sqlite table where you can store arbitrary data (in a BLOB column, serialized with nippy). then you can have your backend action handlers write stuff to the tab state and then your long-lived SSE handler can use that next time it renders.
> 
> - there's a `refresh` function you call that triggers a signal via the ReentrantLock thing, so you're supposed to call that from some central place e.g. wherever database transactions are submitted.
>
> - there's a `module` function that biff apps can use which wires up the middleware and the ReentrantLock and makes sure `refresh` gets called whenever a sqlite transaction is submitted. Non-biff apps can use the lib too as long as they do that wiring-up manually.
> 
> For fun I might experiment with a flag that would have wrap-datastar inject the boilerplate stuff for you / extract the `biff-datastar-content` div when appropriate, in which case your page handler would basically just be a plain database-querying, hiccup-returning ring handler, naive to the fact it's being used in an SSE stream.
>
> Oh, and of course the lib comes with a demo chat app.
