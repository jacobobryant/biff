---
title: Introduction
---

<blockquote>
<p>You can build "easy" on top of "simple", but it's hard to do the reverse.<br>
– <a href="https://github.com/ring-clojure/ring/issues/393#issuecomment-593005197">@weavejester</a></p>
</blockquote>

Biff speeds up web development by providing as much functionality as possible
out-of-the-box, while making it easy to swap out, modify, or remove parts as
your project grows. It prioritizes small-to-medium sized projects.

Some of Biff's most distinctive features:

- Built on [XTDB](https://xtdb.com/), the world's finest database. It has
  flexible data modeling, Datalog queries, and immutable history. You can use
  the filesystem for the storage backend in dev and switch to Postgres for
  production.
- Uses [htmx](https://htmx.org/) (and [hyperscript](https://hyperscript.org/))
  for the frontend. htmx lets you create interactive, real-time applications
  while keeping the majority of your code on the backend.
- Ready to deploy. The template project comes with a script for provisioning an
  Ubuntu server, including Git push-to-deploy, HTTPS certificates, and NGINX
  configuration.
- Develop in prod. Biff is designed so you can develop your entire application
  over an nREPL connection to your production app, with minimal restarts.
  Whenever you hit save, files get rsynced to the server and evaluated.
  (Optional.)

Other things that Biff wraps/includes:

- Passwordless, email-based authentication (via [Buddy](https://funcool.github.io/buddy-sign/latest/) JWTs).
- [Rum](https://github.com/tonsky/rum) and [Tailwind CSS](https://tailwindcss.com/) for rendering.
- [Jetty](https://github.com/sunng87/ring-jetty9-adapter) for the web server
  and [Reitit](https://github.com/metosin/reitit) for routing.
- [Malli](https://github.com/metosin/malli) for enforcing schema when submitting XTDB transactions.
- [Chime](https://github.com/jarohen/chime) for scheduling tasks.
- In-memory job queues (via Java's BlockingQueues).
- A minimalist dependency injection framework, similar in spirit to Component.
- EDN-based config with environment variables for secrets.

## About

Biff is developed by myself, [Jacob O'Bryant](https://tfos.co). I use it for all my web apps, primarily
[Yakread](https://yakread.com/) and [The Sample](https://thesample.ai/), each of which has
15k-20k lines of code.

If you'd like to support Biff, you can [sponsor me](https://github.com/sponsors/jacobobryant/).
I also do [consulting](https://biffweb.com/consulting) for businesses that are
interested in using Biff.

## Resources

- Subscribe to [the newsletter](/newsletter/) for announcements and blog posts.
- The #biff channel on [Clojurians Slack](http://clojurians.net) is a great place to ask questions.
- New to Clojure? Here's [an opinionated list of excellent Clojure learning materials](https://gist.github.com/ssrihari/0bf159afb781eef7cc552a1a0b17786f).
- Read Biff's source [on GitHub](https://github.com/jacobobryant/biff).
- Looking for an open-source Biff project to study or contribute to? Check out [Platypub](https://github.com/jacobobryant/platypub).
- For anything else, you can always email me: <hello@tfos.co>.
