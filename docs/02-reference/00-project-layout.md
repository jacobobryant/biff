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
├── README.md
├── bb.edn
├── cljfmt-indents.edn
├── config.edn
├── deps.edn
├── resources
│   ├── fixtures.edn
│   ├── public
│   │   ├── img
│   │   │   └── glider.png
│   │   └── js
│   │       └── main.js
│   ├── tailwind.config.js
│   └── tailwind.css
├── secrets.env
├── server-setup.sh
├── src
│   └── com
│       ├── example
│       │   ├── app.clj
│       │   ├── email.clj
│       │   ├── home.clj
│       │   ├── middleware.clj
│       │   ├── repl.clj
│       │   ├── schema.clj
│       │   ├── settings.clj
│       │   ├── test.clj
│       │   ├── ui.clj
│       │   └── worker.clj
│       └── example.clj
└── bb
    ├── deps.edn
    └── src
        └── com
            └── example
                └── tasks.clj
```

`config.edn` and `secrets.env` contain your app's configuration and secrets,
respectively, and are not checked into git. `server-setup.sh` is a script for
provisioning an Ubuntu server (see [Production](/docs/reference/production/)).
`bb.edn` defines project tasks—run `bb tasks` to see the available
commands.
