---
title: Create a project
---

Requirements:

 - Java 11 or higher
 - [Babashka](https://github.com/babashka/babashka#installation)

Run this command to create a new Biff project:

```bash
bb -e '(load-string (slurp "https://biffweb.com/new-project.clj"))'
```

This will create a minimal CRUD app which demonstrates most of Biff's features.
Run `bb dev` to start the app on `localhost:8080`. Whenever you save a file,
Biff will:

 - Evaluate any changed Clojure files (and any files which depend on them)
 - Regenerate static HTML and CSS files
 - Run tests

When you're ready to deploy, see [Production](/docs/reference/production/).

### Windows

If you're on Windows, I recommend using Biff via WSL2. I do this myself. Plain
Windows will mostly work, but the `bb prod-dev` command (used for Biff's
optional develop-in-prod workflow) is unsupported, unless you manage to install
`rsync` and `fswatch` somehow.

## REPL-driven development

The `bb dev` command will start an nREPL server on port 7888 which you can
connect to with your editor (see "Jacking in" below). There is a
[repl.clj](https://github.com/jacobobryant/biff/blob/master/example/src/com/example/repl.clj)
file in your project which demonstrates how to interact with the system via the
REPL.

### Refreshing

Since files are evaluated on save, you usually don't have to evaluate anything
from your editor to make changes take effect. However, if you make any changes
to `initial-system` or `components` (see
[Architecture](/docs/reference/architecture/)),
[Tasks](/docs/reference/scheduled-tasks/), [Queues](/docs/reference/queues/),
or `config.edn`, then you'll need to call the
[refresh](https://github.com/jacobobryant/biff/blob/d8c83c4cc25123b67e14751ff5d19e6b24f7317c/example/src/com/example.clj#L86)
function.

If you make any changes to `secrets.env`, you'll need to restart the JVM: hit
`ctrl-c` in the terminal, then run `bb dev` again.

### Jacking in

`cider-jack-in` and similar commands will start up a JVM and an nREPL server
for you. However, `bb dev` already does that. Instead of running
`cider-jack-in`, you should run `cider-connect` (or the equivalent) so that you
can connect to the nREPL server started by `bb dev`. See
[Connecting to a Running nREPL Server](https://docs.cider.mx/cider/basics/up_and_running.html#connect-to-a-running-nrepl-server)
in the CIDER docs.

(See also [instructions for connecting with vim-iced](https://gist.github.com/avitkauskas/88ddc3c9b297f431143e22f36a224459).)

This does mean that CIDER will not be able to decide which version of the nREPL
server dependencies to use. If you run into problems, you'll need to set the
versions manually in `deps.edn`:

```clojure
{:deps {nrepl/nrepl       {:mvn/version "..."}
        cider/cider-nrepl {:mvn/version "..."}
...
```

Optionally, you can configure Cider to start `bb dev` when you jack in by
placing the following file contents in `.dir-locals.el`:

```lisp
((nil . ((cider-preferred-build-tool . babashka)
         (cider-babashka-parameters . "dev"))))
```
