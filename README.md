# Biff

> *Why don't you make like a tree and get out of here?*<br>
> *It's* leave, *you idiot, make like a tree and leave!*
>
> &mdash;Biff Tannen in Back to the Future

Biff is a web framework and self-hosted deployment solution for Clojure,
inspired by Firebase. I'm
using Biff in production for [my own startup](https://findka.com). I'm
currently working on documentation and code cleanup so that others can use it
too.

Join `#biff` on [http://clojurians.net](clojurians.net) for discussion. By the way: I want to help people learn web development with Biff.
In particular, doing so will help me improve Biff and its documentation (I also
enjoy teaching). So if you want to use Biff,
come say hi. If you've got an idea for a web app you want to build, I'd be happy to do some 1-on-1
mentoring/pair programming to help make it happen.

## Getting Started&mdash;Work in progress!

For a fairly detailed conceptual overview, read/skim [this post](https://findka.com/blog/migrating-to-biff/) first.

See the [example project](example/). It's tic-tac-toe written in Biff. You can run it in
development if you:

1. Install dependencies (clj, npm and overmind)
2. `./task setup`
3. `./task dev` (if you get a lockfile error, hit ctrl-c and run it again)
4. Go to `localhost:9630` and start the `app` build
5. Go to `localhost:8080`

After that, you can browse through the code and start tinkering. Some good starting points are:

 - [task](/example/task)
 - [src/hello/core.clj](/example/src/hello/core.clj)
 - [src/hello/client/app.cljs](/example/src/hello/client/app.cljs)

<!--
### New project template

Make a new directory for your app. Put the following in `deps.edn`:

```clojure
{:deps
 {github-jacobobryant/biff
  {:git/url "https://github.com/jacobobryant/biff"
   :tag "HEAD"}}}
```
Then run `clj -Sresolve-tags` to add Biff's latest commit sha to deps.edn.

Now put this in `src/hello/core.clj`:

```clojure
(ns ^:biff hello.core)

(defn start-hello [sys]
  (println "Hello starting")
  (-> sys
    (assoc :foo 3)
    (update :trident.system/stop conj #(println "Hello stopping"))))

(def components
  [{:name :hello/core
    :requires [:biff/init]
    :start start-hello}])
```

Run `clj -m biff.core`. You should get output like this:
```bash
$ clj -m biff.core
23:05:19.763 [main] INFO  crux.hash.jnr - unknown
23:05:19.770 [main] INFO  crux.hash - Using libgcrypt for ID hashing.
23:05:33.716 [main] DEBUG org.jboss.logging - Logging Provider: org.jboss.logging.Slf4jLoggerProvider
Starting :biff.core/toggle-nrepl :biff/init :biff/console :biff/web-server :hello/core
23:05:35.090 INFO  [org.projectodd.wunderboss.web.Web] (main) Registered web context /
Hello starting
System started.
```

From your editor, connect to nrepl on port 7888 and evaluate the following:
```clojure
(:foo @biff.core/system)
=> 3

(biff.util/stop-system @biff.core/system)
=> Hello stopping
=> nil
```

In `src/hello/core.clj`, change `"Hello starting"` to `"Hello again"`. Save the
file and then evaluate `(biff.core/refresh)`.

See [Plugins and config](https://findka.com/blog/migrating-to-biff/#plugins-and-config).

### HTTP routes

 -->



<!--

Biff is a package manager for self-hosted Clojure web apps.

## It's a what?

Biff is a Clojure program that you install on your own virtual private server
(I use DigitalOcean). It provides a web interface with which you can install
Biff apps directly from Github (any repo tagged with the `clj-biff` topic). You
can test it out locally right now by cloning this repo and running
`./template/start-biff.sh`.  (The default admin password is `hey`).

*Biff apps?*

Precisely. Apps are installed by adding them as a git dependency to Biff's
deps.edn file. Biff includes a simple plugin system which makes the apps
discoverable once they're on the classpath. All the apps run in the same JVM
process.

In fact, the core of Biff is just that plugin system, less than 30 lines of
code. Everything else is pluggable. The package manager is itself a Biff app,
though I've bundled it with Biff core for convenience.

## Usage

WIP, come back later.

<!- -
See [How to write a Biff app](/docs/how-to-write-a-biff-app.md).

To install Biff on a DigitalOcean droplet:

1. Create an Ubuntu 18.04 droplet.
2. Point a domain at it (e.g. biff.yourwebsite.com).
3. SSH into the droplet (as root).
4. Clone this repository.
5. Run `./install-biff.sh`.

I'm also planning to see if I can set up a one-click install option.
- ->

## OK, but *why?*

In the move to web application software, we traded away **extensibility** for
**convenience**. But we should have extensibility *and* convenience.

If you store your data on a server you control instead of some company's
server, it becomes much easier to write new programs that operate on your data. No need
to go through an API (if one even exists). It's also easier for
open-source software to flourish: publishing an app is as easy as pushing to a
git repo. You don't have to worry about hosting because everyone self-hosts&mdash;even
non-technical users.

The real kicker is that those effects **compound**. The more extensible
software there is, the more opportunities there will be to extend software.

![](/img/diagram.png)

Just as the software industry shifted from desktop applications to web
applications, I believe it now needs to shift from app-centric servers to
user-centric servers. Biff is an extremely practical way to make that start
happening.

## Status

I'm currently moving [Findka](https://findka.com) from Firebase to Biff.
-->

## Self-promotion

If you want to support my work, subscribe to [my newsletter](https://findka.com/subscribe/).

## License

Distributed under the [EPL v2.0](LICENSE)

Copyright &copy; 2020 [Jacob O'Bryant](https://jacobobryant.com).
