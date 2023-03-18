---
title: Create a project
---

Requirements:

 - Java 11 or higher
 - [Babashka](https://github.com/babashka/babashka#installation)

Run these commands to create a new Biff project (and if you run into any
problems, see [Troubleshooting](/docs/reference/troubleshooting/)):

```bash
# Linux, Mac
bb -e "$(curl -s https://biffweb.com/new-project.clj)"

# Windows
iwr https://biffweb.com/new-project.clj -out new-project.clj
bb new-project.clj
```

This will create a minimal CRUD app which demonstrates most of Biff's features.
Run `bb dev` to start the app on `localhost:8080`. Whenever you save a file,
Biff will:

 - Evaluate any changed Clojure files (and any files which depend on them)
 - Regenerate static HTML and CSS files
 - Run tests

You can connect your editor to nREPL port 7888. There's also a `repl.clj` file
which you can use as a scratch space.

When you're ready to deploy, see [Production](/docs/reference/production/).

## Jacking in

`cider-jack-in` and similar commands will start up a JVM and an nREPL server
for you. However, `bb dev` already does that. Instead of running
`cider-jack-in`, you should run `cider-connect` (or the equivalent) so that you
can connect to the nREPL server started by `bb dev`. See
[Connecting to a Running nREPL Server](https://docs.cider.mx/cider/basics/up_and_running.html#connect-to-a-running-nrepl-server)
in the CIDER docs.

This does mean that CIDER will not be able to decide which version of the nREPL
server dependencies to use. If you run into problems, you'll need to set the
versions manually in `deps.edn`:

```clojure
{:deps {nrepl/nrepl       {:mvn/version "..."}
        cider/cider-nrepl {:mvn/version "..."}
...
```

## Windows

If you're on Windows, I recommend using Biff via WSL2. I do this myself. Plain
Windows will mostly work, but the `bb prod-dev` command (used for Biff's
optional develop-in-prod workflow) is unsupported, unless you manage to
install `rsync` and `fswatch` somehow. (PRs welcome if you can figure out how
to remove those dependencies.)
