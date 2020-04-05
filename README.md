# Biff

> *Why don't you make like a tree and get out of here?*<br>
> *It's* leave, *you idiot, make like a tree and leave!*
>
> &mdash;Biff Tannen in Back to the Future

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
applications, I believe it now needs to shift from company-controlled data to
user-controlled data. Biff is an extremely practical way to make that start
happening.

## Status

Now that Biff core is released, I'm going to start converting some old
semi-abandoned applications I've written into Biff apps. There are also plenty
of new apps I'd like to start writing for Biff.

## Self-promotion

If you want to support my work, subscribe to [my newsletter](https://findka.com/subscribe/).

## License

Distributed under the [EPL v2.0](LICENSE)

Copyright &copy; 2020 [Jacob O'Bryant](https://jacobobryant.com).
