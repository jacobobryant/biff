# biff.run

A tool&mdash;nay, a convention&mdash;for curating and running Clojure CLI tasks.

I made biff.run because I wanted to provide a bundle of default CLI tasks for
[Biff](https://biffweb.com) projects without having to copy a bunch of boilerplate into new
projects. Both the task implementations (functions) and task "bundles" (maps, see below) should be
defined in library code. As a "task bundle" maintainer, this allows me to both update task
implementations and add new tasks without requiring users to do anything besides bumping a version.

I also wanted a solution that:

- works without anything installed other than `clj`.
- is useful for curating tasks even if those tasks weren't written with biff.run in mind.

I currently only use biff.run in Biff projects, so the bundle of tasks there is largely Biff-specific.
However I also like the idea of providing a more "vanilla" bundle of default tasks that could be
useful more broadly in non-Biff projects. (e.g. there could be tasks for creating new projects,
running tests, updating deps, building jars, publishing to clojars...). Maybe I'll do that before
publicly announcing this tool.

## Demo

This repo defines a couple example tasks in `demo/src/`:

```bash
$ clj -M:run -h
Available commands:

  a - Perform task A
  b - Perform task B

$ clj -M:run a --message hello
Task A args: ("--message" "hello")
```

## Usage

The main idea is that tasks are defined as maps like so:

```clojure
(ns com.example.tasks)

(def tasks
  {"my-task" {:task 'com.example/my-task
              :doc "Run my task"}
   "nrepl"   {:task 'nrepl.cmdline/-main
              :doc "Start an nREPL server"}})
```

To ensure reasonable start-up time, this namespace holding the tasks map shouldn't require anything.
Individual tasks are required only when they're ran.

Then you add a `-main` entrypoint that invokes biff.run:

```clojure
(ns com.example.tasks
  (:require [com.biffweb.run :as biff.run]))

(def tasks
  {"my-task" {:task 'com.example/my-task
              :doc "Run my task"}
   "nrepl"   {:task 'nrepl.cmdline/-main
              :doc "Start an nREPL server"}})

(defn -main [& args]
  (apply biff.run/main* tasks args))
```

Then you add an alias to `deps.edn` that calls that namespace:

```clojure
;; :run is used as the alias by convention
:aliases {:run {:extra-deps {com.biffweb/run {:mvn/version "1.0"}
                             ;; If your tasks are defined in a library, add it here:
                             com.example/tasks {:mvn/version "1.0"}}
                ;; If your tasks are defined in the current project, make sure they're on the
                ;; classpath:
                :extra-paths ["dev"]
                :main-opts ["-m" "com.example.tasks"]}}
```

Then you can do `clj -M:run my-task` to run the task, or `clj -M:run -h` to see the available tasks.
For extra ergonomics you can put `alias biff.run='clj -M:run'` in your `.bashrc`.

I generally define the `:run` alias in my project `deps.edn` files, but you could also stick it in
`~/.clojure/deps.edn`.

### Defining your own tasks

Continuing the example above, if you want to define (or override) some additional project-specific
tasks, you can reference them in `dev/demo_tasks.clj`:

```clojure
(ns demo-tasks
  (:require [com.biffweb.run :as biff.run]
            [com.example.tasks :as example.tasks]))

(def tasks
  (merge example.tasks/tasks
         {"another-task" {:task 'demo-tasks.another-task/task
                         :doc "Run another task"}}))

(defn -main [& args]
  (apply biff.run/main* tasks args))
```

And then point `deps.edn` at `demo-tasks`:

```clojure
:main-opts ["-m" "demo-tasks"]
```

### Writing tasks that call other tasks

biff.run provides a `run-task` function for calling other tasks:

```clojure
(ns com.example.my-task
  (:require [com.biffweb.run :refer [run-task]]))

(defn my-task [& args]
  ;; Calls the "another-task" task from the task map that was passed to biff.run:
  (run-task "another-task" "--foo" "bar"))
```

This will ensure that if the user has defined a custom `"another-task"` task, you'll call it. If you
don't care about that, you can instead call the task function directly:

```clojure
(ns com.example.my-task
  (:require [com.example.another-task :refer [another-task]]))

(defn my-task [& args]
  (another-task "--foo" "bar"))
```

### Argument parsing

biff.run doesn't do any argument-parsing; all arguments are passed as strings and you can parse them
using whatever methods you like.
