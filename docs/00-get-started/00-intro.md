---
title: Introduction
---

Biff is designed to make web development with Clojure fast and easy without
compromising on simplicity. Biff aims to provide as much functionality as
possible out-of-the-box, while making it easy to swap out, modify, or remove
parts in cases where you need something different. It prioritizes
small-to-medium sized projects.

Some of Biff's most distinctive features:

- Built on [XTDB](https://xtdb.com/), the world's finest database. It has
  flexible data modeling, Datalog queries, and immutable history. You can use
  the filesystem for the storage backend in dev and switch to Postgres for
  production.
- Uses [htmx](https://htmx.org/) (and [hyperscript](https://hyperscript.org/))
  for the frontend. Htmx lets you create interactive, real-time applications by
  sending HTML snippets from the server instead of using
  JavaScript/ClojureScript/React.
- Ready to deploy. The template project comes with a script for provisioning an
  Ubuntu server, including Git push-to-deploy, HTTPS certificates, and NGINX
  configuration.
- Develop in prod. If you choose to enable this, you can develop your entire
  application without ever starting up a JVM on your local machine. Whenever
  you hit save, files get rsynced to the server and evaluated.
- Passwordless, email-based authentication.

Other things that Biff wraps/includes:

- [Rum](https://github.com/tonsky/rum) and [Tailwind CSS](https://tailwindcss.com/) for rendering.
- [Jetty](https://github.com/sunng87/ring-jetty9-adapter) for the web server
  and [Reitit](https://github.com/metosin/reitit) for routing.
- [Malli](https://github.com/metosin/malli) for enforcing schema when submitting XTDB transactions.
- [Buddy](https://funcool.github.io/buddy-sign/latest/) for email link authentication (JWTs).
- [Chime](https://github.com/jarohen/chime) for scheduling tasks.
- In-memory job queues (via Java's BlockingQueues).
- A minimalist, 15-line dependency injection framework, similar in spirit to Component.

Projects built with Biff:

- [Yakread](https://yakread.com/), an ML-driven reader app.
- [The Sample](https://thesample.ai/), a newsletter recommender system.
- [Platypub](https://github.com/jacobobryant/platypub), a blogging + newsletter platform.

[About the author](https://tfos.co).
