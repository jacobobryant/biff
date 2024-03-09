---
title: Architecture
---

Application code is stored in **modules**. A module is a map which can have any
of the following keys:

```clojure
(def module
  {:static {...}
   :routes [...]
   :api-routes [...]
   :schema {...}
   :tasks [...]
   :on-tx (fn ...)
   :queues [...]})
```

Each of these keys are discussed on subsequent pages:

 - [Static Files](/docs/reference/static-files/)
 - [Routing](/docs/reference/routing/)
 - [Schema](/docs/reference/schema/)
 - [Scheduled tasks](/docs/reference/scheduled-tasks/)
 - [Transaction Listeners](/docs/reference/transaction-listeners/)
 - [Queues](/docs/reference/queues/)

To demonstrate, a "hello world" module might look like this:

```clojure
(defn hello [ctx]
  [:html
   [:body
    [:p "Hello world"]]])

(def module
  {:routes [["/hello" {:get hello}]]})
```

Your app's modules are bundled together and stored in the **system map**:

```clojure
(def modules
  [app/module
   (biff/authentication-module {})
   home/module
   schema/module
   worker/module])

...

(def initial-system
  {:biff/modules #'modules
   ...})
```

When your app starts, this system map is passed through a sequence of **components**:

```clojure
(defonce system (atom {}))

(def components
  [biff/use-config
   biff/use-secrets
   biff/use-xtdb
   biff/use-queues
   biff/use-xtdb-tx-listener
   biff/use-jetty
   biff/use-chime
   biff/use-beholder])

(defn start []
  (let [new-system (reduce (fn [system component]
                             (log/info "starting:" (str component))
                             (component system))
                           initial-system
                           components)]
    (reset! system new-system)
    ...))
```

Each component is a function which takes the system map as a parameter and
returns a modified version. After the system map is passed through all the
components, the system will be running and your module code will be called as
appropriate.

For example, any routes you define in your modules will be compiled into a
handler function, and this handler will be passed to the `use-jetty` component.
This component starts the Jetty web server and passes requests to your handler.

```clojure
(defn use-jetty [{:keys [biff/handler] :as system}]
  (let [server (jetty/run-jetty (fn [request] (handler (merge system request)))
                                {:host "localhost"
                                 :port 8080
                                 :join? false})]
    (update system :biff/stop conj #(jetty/stop-server server))))
```

This component also merges the system map with incoming requests, so your
routes can get access to resources like the database:

```clojure
(defn hello [{:keys [biff/db] :as ctx}]
  (let [n-users (ffirst (xt/q db
                              '{:find [(count user)]
                                :where [[user :user/email]]}))]
    [:html
     [:body
      [:p "There are " n-users " users."]]]))

(def module
  {:routes [["/hello" {:get hello}]]})
```

Biff often uses `ctx` (or "context map") as a more general term, since it often
includes both the system map and other things like the Ring request.

Your project includes a `refresh` function which can be used during development
if you modify any of the components:

```clojure
(defn refresh []
  (doseq [f (:biff/stop @system)]
    (log/info "stopping:" (str f))
    (f))
  (clojure.tools.namespace.repl/refresh :after `start))
```

Calling `refresh` should be rare. Biff is designed so that as much as possible,
changes take effect immediately without needing to restart the system (through
the use of late binding).

## Modifying the framework

Biff is designed to give you strong *defaults* while still allowing you to
change just about anything without too much hassle. The goal is that Biff helps
you to launch quickly and that it doesn't get in the way later on as your needs
evolve.

You can modify your app's framework code by supplying a different set of
component functions. For example, if you need to change the way configuration
is stored, you can replace `biff/use-aero-config` with your own component:

```clojure
(defn use-custom-config [system]
  (let [config ...]
    (merge system config)))

(def components
  [use-custom-config
   biff/use-xtdb
   ...])
```

If you'd like to modify one of Biff's default components beyond what its options
allow, it's fine and recommended to copy the source code for the component into
your own project. For example, the `biff/use-xtdb` component only supports using
RocksDB, LMDB, and/or JDBC as the storage backend. If you want to use Kafka, you
could copy the [`biff/use-xtdb`](/docs/api/xtdb#use-xtdb) source into your project
and make the needed changes.

Biff also provides a single default module, `biff/authentication-module`, which
defines the backend routes needed for Biff's email-based authentication. If you
need to modify it substantially, you can similarly copy the source code into
your project or replace it altogether.

## What about the frontend?

Biff doesn't need to add much frontend architecture thanks to htmx. htmx allows
server-side frameworks like Django, Rails, and Biff to to be used for
moderately interactive apps, while still keeping most of your code on the
backend. See [Understanding htmx](https://biffweb.com/p/understanding-htmx/).
