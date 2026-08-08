# biff.tasks

A compilation of CLI tasks for Clojure applications and libraries (tools.deps
projects), packaged for use with
[biff.run](https://github.com/jacobobryant/biff/tree/v2.x/libs/run) so that you
can run them via `clj -M:run <command>`. Some tasks are thin wrappers around
existing tools, some are new.

Includes tasks for:

- code quality: format code with
  [cljformat](https://github.com/weavejester/cljfmt), lint with
  [clj-kondo](https://github.com/clj-kondo/clj-kondo), run tests with
  [kaocha](https://github.com/lambdaisland/kaocha).

- development: run your project with eval-on-file-save, compile CSS with
  Tailwind, update dependencies with [antq](https://github.com/liquidz/antq),
  start a local nREPL server, add dependencies to deps.edn.

- production: provision an Ubuntu server, deploy to that server, tail logs from
  the server, start an nREPL connection to the server.

- libraries: generate markdown API docs from your docstrings
  ([example](docs/api/com.biffweb.tasks.md)), publish to Clojars with
  [deps-deploy](https://github.com/slipset/deps-deploy).

### Dependency

```clojure
com.biffweb/tasks {:mvn/version "2.0.0-rc13"}
```

### Status

This library will be a release candidate until all [the other Biff 2
libraries](/README.md) have been released. Until then there could be breaking
changes, but I don't anticipate any.

## Reference

- [API](docs/api/com.biffweb.tasks.md). Includes a list of all the tasks and
  help docs for each task.
- [Config](docs/config.md)

## Usage

### Installation

Add a `:run` alias to your project with either `-m com.biffweb.tasks.app` (for
application projects) or `-m com.biffweb.tasks.lib` (for library projects):

```clojure
:aliases
{:run {:extra-paths ["test"]
       :extra-deps  {com.biffweb/tasks {:mvn/version "2.0.0-rc13"}
                     ...}
       ;; Replace with com.biffweb.tasks.lib for library projects
       :main-opts   ["-m" "com.biffweb.tasks.app"]}}
```

Add `alias cljrun='clj -M:run'` to your shell config so you can run tasks with
`cljrun <command>`.

### Running tasks

Run `clj -M:run -h` to see the available commands, for example (these are the
application tasks):

```
$ clj -M:run -h
Available commands:

  add          - Add the latest release of a library to deps.edn.
  code-quality - Format, lint, and test code.
  css          - Compile CSS with Tailwind.
  deploy       - Deploy to a server provisioned with the prod-setup task.
  dev          - Start the app in dev mode.
  format       - Format code with cljfmt.
  init         - Initialize a freshly cloned project.
  lint         - Lint code with clj-kondo.
  nrepl        - Start an nREPL server.
  prod-logs    - Tail logs from the server.
  prod-nrepl   - Start an SSH tunnel to the production nREPL server.
  prod-restart - Restart the application in production.
  prod-setup   - Provision a server so the app can be deployed to it.
  test         - Run tests with Kaocha.
  uberjar      - Generate an uberjar.
  update       - Update dependencies with antq and update clj-kondo files.
```

Run `clj -M:run <command> -h` to see the help doc for an individual task:

```
$ clj -M:run dev -h
Start the app in dev mode.

Reads the following config keys:

- :biff.tasks/main-ns (required)

Ensures all :paths / :extra-paths directories from deps.edn exist. Runs the
`css --watch` task in the background. Starts another file watcher that
evaluates source files and their dependants when saved.

Then calls the `-main` function in the `main-ns` namespace.
```

See the [config reference](docs/config.md); individual tasks don't always
describe what the config keys do.

### Configuration

Put configuration in a `config.edn` file on the classpath, e.g.
`resources/config.edn`, assuming you have `:paths ["resources" ...]` in
deps.edn. This file is meant to be checked into source control. It's parsed with
[biff.config](/libs/config), so you can use `#biff/env` and `#biff/secret`
reader tags and store the values in a `config.env` file, outside of source
control:

```clojure
;; resources/config.edn
{:biff.tasks/main-ns        com.example
 :biff.tasks/domain         #biff/env DOMAIN
 :biff.tasks/clojars-secret #biff/secret CLOJARS_SECRET
 ...}
```

```
# config.env
DOMAIN=example.com
CLOJARS_SECRET=abc123
```

### Custom tasks

See [biff.run -> writing tasks](/libs/run/README.md#writing-tasks). The task
maps are exposed as `com.biffweb.tasks/app-tasks` and
`com.biffweb.tasks/lib-tasks`, so you can merge your own tasks into those:


```clojure
;; dev/tasks.clj
(ns tasks
  (:require [com.biffweb.run :as biff.run]
            [com.biffweb.tasks :as biff.tasks]))

(def tasks
  (merge biff.tasks/app-tasks
         {"my-task" {:task 'tasks.my-task/my-task
                     :doc "Do something."}}))

(defn -main [& args]
  (apply biff.run/main tasks args))


;; dev/tasks/my_task.clj
(ns my-task)

(defn my-task
  "Do something.

   Options:

     --frobulate    Enables frobulation."
  [& args]
  ...)


;; deps.edn
:aliases
{:run {:paths ["dev" "test" ...]
       :main-opts ["-m" "tasks"]
       ...}}
```

If you want to provide a collection of tasks in an external library, use a
qualified namespace like `com.example.tasks` instead of `tasks` and then publish
that namespace with both the `tasks` map and the `-main` function as part of the
public API.

### Agents

LLMs can call the `agent-refresh` function via nREPL to evaluate changed files,
lint code, and run tests. Put something like this in `AGENTS.md` or whatever
file your agent uses:

> After you edit Clojure source files, run `trench -p $(cat .nrepl-port) -e
> "((requiring-resolve 'com.biffweb.tasks/agent-refresh))"` to evaluate the
> changes and run linting/tests. If `trench` is not already available, you can
> download it from https://github.com/athos/trenchman/releases/latest.

## Recommended tool config

I use these configurations in my own projects:

`.github/workflows/code-quality.yml`:

```yaml
name: code quality

on:
  push:

jobs:
  code-quality:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
      - uses: DeLaGuardo/setup-clojure@13.2
        with:
          cli: latest
      - run: clj -M:run code-quality
      - run: git diff --quiet --exit-code
```

`tests.edn`:

```clojure
#kaocha/v1 {}
```

`.cljfmt.edn`:

```clojure
{:align-form-columns?             true
 :align-map-columns?              true
 :extra-aligned-forms             {let #{0}}
 :blank-lines-separate-alignment? true}
```

`.clj-kondo/config.edn`:

```clojure
{:linters {:line-length {:level           :warning
                         :max-line-length 80
                         :exclude-urls    true}}}
```

(I would use 100 characters except I do a lot of coding on my tablet which only
fits 80.)

I also have a [custom, vibe-coded `lint` task](/dev/tasks/lint.clj) which
ensures that form pairs on separate lines are separated with blank lines:

```clojure
;; Good:
(let [a 1
      b 2

      c
      3

      d
      4]
  ...)

;; Bad:
(let [a
      1
      b
      2]
  ...)
```

I may add support for that to biff.tasks' `lint` task at some point.
