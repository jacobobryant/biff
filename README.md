# Biff

> *Why don't you make like a tree and get out of here?*<br>
> *It's* leave, *you idiot, make like a tree and leave!*
>
> &mdash;Biff Tannen in Back to the Future

Biff is a package manager for self-hosted Clojure web apps.

## It's a what?

Biff is a Clojure program that you install on your own virtual private server
(I use DigitalOcean). It provides an admin web interface which you can use to
install Biff apps directly from Github. You can test it out locally right now
by cloning this repo and running `./template/start-biff.sh`.  (The default
admin password is `hey`).

*Biff apps?*

Precisely. Apps are installed by adding them as a git dependency to Biff's
deps.edn file. Biff includes a very simple plugin system which makes the apps
discoverable once they're on the classpath. All the apps run in the same JVM
process.

In fact, the core of Biff is just that plugin system, less than 30 lines of
code. Biff's package manager is itself a Biff app (I've bundled it with Biff
core simply for convenience).

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

Actually, "plugin" might be a better name for this one. It provides a web
server for other apps. Simply include a reitit route definition in the config
map, and biff.http will throw it in. For example:

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

![Screenshot](/screenshot.png?raw=true)

**Coming soon**

I have several applications which I'll soon be turning into Biff apps. In addition,
there'll be plugins that provide websockets (through Sente), a Crux database,
and utilities for providing subscribable queries.

## OK, but *why?*

The main idea behind Biff is that the internet's data should be sharded by
user, not by application.

**TODO: finish writing this**

## Self-promotion

If you want to support my work, subscribe to [my newsletter](https://findka.com/subscribe/).

## License

Distributed under the [EPL v2.0](LICENSE)

Copyright &copy; 2020 [Jacob O'Bryant](https://jacobobryant.com).
