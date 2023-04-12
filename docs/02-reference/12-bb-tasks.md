---
title: Babashka Tasks
---

Biff uses [Babashka tasks](https://book.babashka.org/#tasks) for command line operations. You can
run the `bb tasks` command to see a list of available tasks:

```bash
$ bb tasks
The following tasks are available:

dev     Starts the app locally.
deploy  Deploys the app via `git push`.
hello   An example of a custom task.
...
```

Biff projects start out with several files related to Babashka tasks. `bb.edn` is the first file Babashka checks
for configuration:

```clojure
{:deps {com.example/tasks {:local/root "tasks"}}
 :tasks {dev    com.biffweb.tasks/dev
         deploy com.biffweb.tasks/deploy
         hello  com.example.tasks/hello
         ...}}
```

The `tasks/deps.edn` file contains a dependency to the `com.biffweb.tasks` namespace, which includes
all of Biff's default tasks:

```clojure
{:paths ["src"]
 :deps {com.biffweb/tasks {:git/url "https://github.com/jacobobryant/biff"
                           :deps/root "tasks"
                           :sha "..."
                           :tag "..."}}}
```

If you need to define any custom tasks, you can put them in `tasks/src/com/example/tasks.clj`:

```clojure
(ns com.example.tasks)

(defn hello
  "An example of a custom task."
  []
  (println "Hello there."))
```

(You'll also need to add a line for the task to `bb.edn`.)

If you need to modify the behavior of any of Biff's default tasks, you can copy
[the source code](https://github.com/jacobobryant/biff/blob/master/tasks/src/com/biffweb/tasks.clj)
for the task into your project and then update `bb.edn` to point to, for
example, `com.example.tasks/dev` instead of `com.biffweb.tasks/dev`.
