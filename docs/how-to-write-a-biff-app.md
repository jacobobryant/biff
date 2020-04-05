# How to write a Biff app

First, an overview of how things work.

## Overview

biff.core works like so:

1. All namespaces on the classpath that have `^:biff` metadata (e.g. `(ns
   ^:biff yourapp.core ...`) are `require`d on startup.
2. After that, Biff runs `(mount.core/start)` (so Biff apps should store
   initialization code inside a mount state).
3. Apps can expose themselves to each other by including a `(def config {...})`
   configuration map in the `^:biff` namespace. A vector of all these maps will
   be available at `biff.core/config` after startup.

biff.core is bundled with three separate Biff apps.

**biff.http**

Actually, "plugin" might be a better name than "app" for this one. It provides
a web server for other apps. Simply include a reitit route definition in the
config map, and biff.http will throw it in. For example:

```clojure
(def config
  {:biff.http/route
   ["/biff/pack"
    ["/ping" {:get ping
              :name ::ping}]
    ...]})
```

biff.http wraps everything with
[ring-defaults](https://github.com/ring-clojure/ring-defaults) middleware.

Also, biff.http serves static files from the `public` resource directory. Apps
should namespace their routes and files (e.g. `/yourapp/core/index.js`).

**biff.auth**

This app stores a password hash in Biff's deps.edn file and provides an admin
login form. Authentication information is stored in the session and is
available to any other apps that receive requests through biff.http.

**biff.pack**

This app searches Github for repos that are tagged with the `clj-biff` topic.
You can install, update and uninstall packages. After making changes, Biff Pack
prompts you to restart the JVM process, after which newly installed apps will
start running.

![Screenshot](/img/screenshot.png?raw=true)

Soon, I'll be writing more Biff plugins that provide a Crux database,
subscribable queries (simplified queries, not datalog), and probably other
stuff. Basically I'm working on a lightweight, full-stack web framework for
Biff apps. I view the whole thing as a Clojurey replacement for Firebase.

## Write and publish your own app

1. Create a project with deps.edn, or make sure you create and commit a pom.xml file.
2. Add `^:biff` to your app's entry point.
3. Put initialization code in a mount state. (If you don't like mount, you can
   use it to start up integrant or something else.)
4. If needed, add configuration to `yourapp.core/config`.
5. Push to Github and add the `clj-biff` topic.
