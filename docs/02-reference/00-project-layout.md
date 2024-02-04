---
title: Project Layout
---

Biff has two parts: a library and a starter project. As much code as
possible is written as library code, exposed under the `com.biffweb` namespace.
This includes a lot of high-level helper functions for other libraries.

The starter project contains the top-level framework code&mdash;the stuff that glues
everything else together. When you start a new Biff project, this starter code is
copied directly into your project directory, and the Biff library is added as a regular
dependency in `deps.edn`.

A new Biff project will look like this:

(Throughout these docs, we'll assume you selected `com.example` for the main
namespace when creating your project.)

```text
├── Dockerfile
├── README.md
├── cljfmt-indents.edn
├── deps.edn
├── dev
│   ├── repl.clj
│   └── tasks.clj
├── resources
│   ├── config.edn
│   ├── config.template.env
│   ├── fixtures.edn
│   ├── public
│   │   ├── img
│   │   │   └── glider.png
│   │   └── js
│   │       └── main.js
│   ├── tailwind.config.js
│   └── tailwind.css
├── server-setup.sh
├── src
│   └── com
│       ├── example
│       │   ├── app.clj
│       │   ├── email.clj
│       │   ├── home.clj
│       │   ├── middleware.clj
│       │   ├── schema.clj
│       │   ├── settings.clj
│       │   ├── ui.clj
│       │   └── worker.clj
│       └── example.clj
├── target
│   └── resources
└── test
    └── com
        └── example_test.clj
```

- `src/com/example/app.clj` demonstrates how to create routes and request handlers.
- `dev/repl.clj` contains instructions and helper code for interacting with your app via the REPL.
- `dev/tasks.clj` contains tasks that can be ran with `clj -M:dev <task name>`. Biff provides some common tasks (see
  `clj -M:dev --help`), and you can also define your own custom tasks.
- `src/com/example/schema.clj` contains your database schema.
- `src/com/example.clj` is the entrypoint.
- `config.env` and `resources/config.edn` contain your app's configuration. `config.template.env` is used to autogenerate `config.env`.
- `server-setup.sh` is a script for provisioning an Ubuntu server (see [Production](/docs/reference/production/)).
