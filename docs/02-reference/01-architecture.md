---
title: Architecture
---

Application code is stored in **plugins**. A plugin is a map which can have any
of the following keys:

```clojure
(def plugin
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

To demonstrate, a "hello world" plugin might look like this:

```clojure
(defn hello [ctx]
  [:html
   [:body
    [:p "Hello world"]]])

(def plugin
  {:routes [["/hello" {:get hello}]]})
```

Your app's plugins are bundled together and stored in the **system map**:

```clojure
(def plugins
  [app/plugin
   (biff/authentication-plugin {})
   home/plugin
   schema/plugin
   worker/plugin])

...

(def initial-system
  {:biff/plugins #'plugins
   ...})
```

When your app starts, this system map is passed through a sequence of **components**:

```clojure
(defonce system (atom {}))

(def components
  [biff/use-config
   biff/use-secrets
   biff/use-xt
   biff/use-queues
   biff/use-tx-listener
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
components, the system will be running and your plugin code will be called as
appropriate.

For example, any routes you define in your plugins will be compiled into a
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

(def plugin
  {:routes [["/hello" {:get hello}]]})
```

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
you to launch quickly and it doesn't get in the way later on as your needs
evolve.

You can modify your app's framework code by supplying a different set of
component functions. For example, if you need to change the way configuration
is stored, you can replace `biff/use-config` with your own component:

```clojure
(defn use-custom-config [system]
  (let [config ...]
    (merge system config)))

(def components
  [use-custom-config
   biff/use-secrets
   biff/use-xt
   ...])
```

If you only need to make a slight change to one of Biff's default components, it's recommended
to copy the source code for the component into your own project. For example, the `biff/use-xt` component
only supports using RocksDB, LMDB, and/or JDBC as the storage backend. If you want to use Kafka, you
could copy the [`biff/use-xt`](/docs/api/xtdb#use-xt) source into your project and make the needed changes.

Biff also provides a single default plugin, `biff/authentication-plugin`, which
defines the backend routes needed for Biff's email-based authentication. If you
need to modify it beyond what the configuration options allow, you can
similarly copy the source code into your project or replace it altogether.

## What about the frontend?

Biff doesn't need to add much frontend architecture thanks to htmx. htmx allows
server-side frameworks like Django, Rails, and Biff to to be used for
moderately interactive apps, while still keeping most of your code on the
backend.
