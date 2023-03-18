---
title: System Composition
---

All the pieces of a Biff project are combined using the
`com.biffweb/start-system` function. This function takes a *system map* and then
passes it through a list of *component functions*. For example, here we start a
very simple app that includes Jetty (as a convention, component functions start
with `use-`):

```clojure
(require '[com.biffweb :as biff])
(require '[ring.adapter.jetty9 :as jetty])

(defn use-jetty [{:keys [biff/handler] :as system}]
  (let [server (jetty/run-jetty handler
                                {:host "localhost"
                                 :port 8080
                                 :join? false})]
    (update system :biff/stop conj #(jetty/stop-server server))))

(defn handler [request]
  {:status 200
   :headers {"content-type" "text/plain"}
   :body "hello"})

(defn -main [& args]
  (biff/start-system
    {:biff/handler #'handler
     :biff/components [use-jetty]}))
```

After calling `start-system`, the system map will be stored in
`com.biffweb/system` (an atom). You can inspect it from the repl with e.g.
`(sort (keys @com.biffweb/system))`.

The system map uses flat, namespaced keys rather
than nested maps. For example, after starting up a new project, here are a few of the
keys that the system map will include:

```clojure
{;; These were included in our call to start-system
 :biff/config "config.edn"
 :biff/handler ...
 :example/chat-clients ...

 ;; These were read from config.edn and merged in by use-config
 :biff.xtdb/topology :standalone,
 :biff.xtdb/dir "storage/xtdb",
 :biff/base-url "http://localhost:8080",

 ;; This was added by use-xt
 :biff.xtdb/node ...

 ...}
```

Several of the component functions pass the system map to their children. For
example, Biff includes some middleware that will merge the system map with the
request map for all incoming requests. `use-chime` will similarly pass the
system map to scheduled task functions. Application code should not touch
`com.biffweb/system` directly; instead, always take it as a parameter from the
parent component function.

If you need to modify some code that runs at startup, you can call
`com.biffweb/refresh`. This will call all the functions stored in `:biff/stop`,
then it will reload all the code with `clojure.tools.namespace.repl/refresh`,
after which `start-system` will be called with the new code.

However, you shouldn't need to call `refresh` regularly. Whenever possible, Biff uses
late binding so that code can be updated without restarting the system. For
example, since we pass the Ring handler function as a
var&mdash;`(biff/start-system {:biff/handler #'handler ...})`&mdash;we can
redefine `handler` from the repl (which will happen automatically whenever you
modify any routes and save a file) and the new handler will be used for new
HTTP requests immediately.

`start-system` and `refresh` are only a few lines of code, so it'd be worth your time
to just read the source:

```clojure
(defonce system (atom nil))

(defn start-system [system*]
  (reset! system (merge {:biff/stop '()} system*))
  (loop [{[f & components] :biff/components :as sys} system*]
    (when (some? f)
      (println "starting:" (str f))
      (recur (reset! system (f (assoc sys :biff/components components))))))
  (println "System started."))

(defn refresh []
  (let [{:keys [biff/after-refresh biff/stop]} @system]
    (doseq [f stop]
      (println "stopping:" (str f))
      (f))
    (clojure.tools.namespace.repl/refresh :after after-refresh)))
```

(In general, reading
[Biff's source code](https://github.com/jacobobryant/biff/tree/dev/src/com/biffweb.clj) is
a great way to learn more about how it works under the hood. The whole thing
isn't very large anyway.)

See also:

 - [Stuart Sierra's Component](https://github.com/stuartsierra/component).
   `start-system` is similar in spirit but is geared for small applications.

## Taking Biff apart

As your application grows, you will inevitably need more and/or different
behaviour than what Biff gives you out of the box. You can modify any part of
Biff by supplying a different list of component functions to `start-system`.
You can add or remove components, and you can modify existing components by
copying their source into your project. For example, if you want to read in
your configuration differently, you can change this:

```clojure
(require '[com.biffweb :as biff])

(defn start []
  (biff/start-system
    {:biff/config "config.edn"
     :biff/components [biff/use-config
                       ...]
     ...}))
```

to this:

```clojure
(defn read-config [path]
  (let [env (keyword (or (System/getenv "BIFF_ENV") "prod"))
        env->config (clojure.edn/read-string (slurp path))
        config-keys (concat (get-in env->config [env :merge]) [env])
        config (apply merge (map env->config config-keys))]
    config))

(defn use-config [sys]
  (merge sys (read-config (:biff/config sys))))

(defn start []
  (biff/start-system
    {:biff/config "config.edn"
     :biff/components [use-config
                       ...]
     ...}))
```

and then make your desired modifications. If you want you could even replace
`start-system` with e.g. Integrant or Component, adding the appropriate
wrappers for Biff's component functions.
