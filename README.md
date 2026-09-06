# Biff: full-stack Clojure web framework

[Biff](https://biffweb.com) speeds up web development by providing as much
functionality as possible out-of-the-box while making it easy to swap out,
modify, or remove parts as your project grows. It's built with solo developers
in mind and is quite opinionated in its approach to serving that use case. Biff
has also been influenced by my experience working in enterprise SaaS and is
perfectly capable of keeping large codebases maintainable.

In short: Biff is built on the idea that web development can be both simple and
easy.

### Resources

- Subscribe to [the newsletter](https://biffweb.com/newsletter/) for
  announcements and blog posts.
- Ask questions on [Clojurians Slack](http://clojurians.net) (#biff channel).
- [Buy me some tokens](https://github.com/sponsors/jacobobryant).

## Get started

Requirements:

- Java 17 or higher
- [Clojure](https://clojure.org/guides/install_clojure)

Create a new project:

```clojure
git clone https://github.com/jacobobryant/biff-starter my-project
cd my-project
clj -M:run dev
```

The first time you run the `dev` command you'll be prompted to choose a
namespace for the new project. After the app starts, go to `localhost:8080` and
sign in. The sign-in code will be printed to the console. Changes are evaluated
whenever you save a file.

Use `clj -M:run -h` to see the available commands.

## Guide

Biff is a composition of libraries. Some of these are wrapper libraries that
make other tools seamless to integrate in a Biff project; some of these are
entirely new creations. Each library is documented individually and is meant to
be usable on its own. Read the library documentation in this order to get a
complete understanding of Biff:

- [biff.core](/libs/core/): interfaces and code that connect all the other Biff
  libs. This is the "framework" part of Biff.
- [biff.config](/libs/config/): a light wrapper around
  [Aero](https://github.com/juxt/aero).
- [biff.fx](/libs/fx/): an approach to keeping your application logic pure, with
  effects separated out. The starter app uses biff.fx to define its Ring
  handlers.
- [biff.graph](/libs/graph/): structure your data model as a queryable graph.
  Inspired by [Pathom](https://github.com/wilkerlucio/pathom3).
- [biff.sqlite](/libs/sqlite/): integrates SQLite with other parts of Biff and
  adds support for rich data types.
- [biff.ring](/libs/ring/): integrates Ring/Reitit/Jetty with other parts of
  Biff. Includes a default middleware stack.
- [biff.datastar](/libs/datastar/): lightweight server-side rendering with
  [Datastar](https://data-star.dev).
- [biff.background](/libs/background/): in-memory job queues and scheduled
  tasks.
- [biff.authenticate](/libs/authenticate): signin-via-email functionality,
  including a default signin form.
- [biff.admin](/libs/admin): admin dashboard with usage/performance metrics,
  user directory, and email alerting.
- [biff.run](/libs/run/): lightweight clj-based task runner.
- [biff.tasks](/libs/tasks/): collection of default biff.run tasks (what you see
  when you run `clj -M:run -h`).

Additional libraries:

- [biff.defaults](/libs/defaults): a wrapper/alias library that includes the
  above libraries, except for biff.run and biff.tasks since those are dev-only.
- [biff.xtdb](/libs/xtdb/): an alternative to biff.sqlite.

### Reading Biff documentation

Each library's README links to a "schema" reference. This document describes the
keywords owned by that library, which are all namespaced with the library name.
For example, the [biff.core schema
reference](libs/core/docs/reference/schema.md) specifies keys such as
`:biff.core/id`, `:biff.core/init`, `:biff.core/start`, etc. So whenever you see
a keyword, you can tell immediately what library owns it and which schema
reference document you can read to get more info.

Other documentation assumes you are familiar with the schema reference and thus
does not always describe what the keys are.

## Content library

Most of the documentation is available within the individual libraries listed
above. Additional documentation/content:

- [How to write a Biff database adapter](/docs/db-adapters.md). Read this if you
  want to use a database other than SQLite or XTDB.
- [Deploy an app with biff.tasks](https://biffweb.com/p/deploy-biff-tasks/).
  Video tutorial for deploying a Biff app.
- [Biff 2.0 sneak peak](https://biffweb.com/p/biff2/): background info on the
  design of Biff.
- [Migrating from Biff 1.0](/docs/migrating-from-biff1.md): guidance on the
  eponymous task.

## LLMs

I don't have any hard rules for how I use or don't use LLMs, but _typically_:

- I use LLMs to generate a rough draft of pretty much all the code I write,
  reading and often editing it thoroughly.

- Tests are an exception: I usually glance over them but rarely edit them.

- I write first drafts of documentation by hand. I often use LLMs to help keep
  docs up to date, with moderate editing.

In general, I'm moderately enthusiastic about using LLMs for code and pretty
skeptical about using them for documentation. See [2x, not 10x: coding with LLMs
in 2026](https://obryant.dev/p/2x-not-10x/).

Biff is intended to be a good framework for both manual and LLM-assisted
development.

---

Copyright (c) Jacob O'Bryant
