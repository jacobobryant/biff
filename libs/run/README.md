# biff.run

A lightweight `clj`-based task runner:

- Tasks are defined as a map of functions, either in your project code or in a
  separate library.

- You define a `:run` alias in your project's `deps.edn` file which includes
  biff.run in `:extra-deps` and has a `:main-opts` entrypoint that passes your
  tasks map to `com.biffweb.run/main`.

- You can then run commands like `clj -M:run my-task arg1 arg2` and `clj -M:run
  --help`.

I made biff.run because I wanted a task runner that doesn't need anything other
than `clj` to be installed and that is designed for collections of tasks to be
defined/maintained in library code, not project code.

Specifically, [biff.tasks](/libs/tasks/) uses biff.run to provide a default set
of tasks for Biff projects. It also has a collection of non-Biff-specific tasks
for library projects.

The main tradeoff is that when writing tasks, you have to be careful to avoid
unnecessary `require`s in order to keep startup times acceptable. And even then,
you obviously still won't get ~instant startup times as you would with e.g.
Babashka tasks.

### Dependency

```clojure
com.biffweb/run {:mvn/version "2.0.0-rc20"}
```

### Status

This library will be a release candidate until all [the other Biff 2
libraries](/README.md) have been released. Until then there could be breaking
changes.

## Reference

- [API](docs/api/com.biffweb.run.md)

## Example

The `demo/` project defines a couple example tasks:

```bash
$ cd demo
$ clj -M:run -h
Available commands:

  a - Perform task A
  b - Perform task B

$ clj -M:run a --message hello
Task A args: ("--message" "hello")
```

## Usage

First, define a tasks map and provide a `-main` function that passes it to
biff.run. For locally-defined tasks, I recommend putting them in
`dev/tasks.clj`:

```clojure
(ns tasks
  (:require [com.biffweb.run :as biff.run]))

(def tasks
  {"my-task" {:task 'tasks.my-task/my-task
              :doc  "Run my task"}
   ;; Since tasks are regular functions, you can easily aggregate tasks from
   ;; various libraries:
   "nrepl"   {:task 'nrepl.cmdline/-main
              :doc  "Start an nREPL server"}})

(defn -main [& args]
  (apply biff.run/main tasks args))
```

Next, include a `:run` alias in your project's `deps.edn` which adds your tasks
to the classpath and invokes your `-main` function by default:

```clojure
{:aliases
 {:run {;; As mentioned, you can define tasks in a local dev/tasks.clj file:
        :extra-paths ["dev"]
        :extra-deps {com.biffweb/run {:mvn/version "..."}
                     ;; and/or use tasks from an external library:
                     com.example/tasks {:mvn/version "..."}}
        :main-opts  ["-m" "tasks"]  ; or ["-m" "com.example/tasks"]
        }}}
```

Then you can run `clj -M:run my-task arg1 arg2` to run a task or run `clj -M:run
-h` to see the available tasks.

### Writing tasks

Tasks functions (the functions pointed to by a `:task` symbol) are plain
functions that accept unparsed command line arguments (strings). Individual
tasks can parse command line options however they want, though I typically have
my own tasks read options from a config file (like `resources/config.edn`)
instead.

#### Managing startup time

The namespace containing your tasks map (the "tasks namespace") should not
require anything other than biff.run (and an external tasks namespace if
needed). Each task should be defined in its own namespace so that its
dependencies are required only when that task runs. If a task has dependencies
that are used conditionally, you can require them at run time via
`requiring-resolve`.

#### Calling other tasks

biff.run provides a `run-task` function for calling other tasks:

```clojure
(ns com.example.my-task
  (:require [com.biffweb.run :refer [run-task]]))

(defn my-task [& args]
  ;; Calls the "another-task" task from the task map that was passed to biff.run:
  (run-task "another-task" "--foo" "bar"))
```

This will ensure that if the user has defined a custom `"another-task"` task,
you'll call it. If you don't care about that, you can always call the task
function directly instead of going through `run-task`:

```clojure
(ns com.example.my-task
  (:require [com.example.another-task :refer [another-task]]))

(defn my-task [& args]
  (another-task "--foo" "bar"))
```

#### Overriding tasks

If you want to use a default collection of tasks from an external lib, you
can still add/override tasks locally:

```clojure
(ns tasks
  (:require [com.biffweb.run :as biff.run]
            [com.example.tasks :as example-tasks))

(def tasks
  (merge example-tasks/tasks
         {"my-task" {:task '/my-task
                     :doc  "Run my task"}
          "nrepl"   {:task 'nrepl.cmdline/-main
                     :doc  "Start an nREPL server"}}))

(defn -main [& args]
  (apply biff.run/main tasks args))
```

## Tips

- I recommend using a shell alias like `alias cljrun='clj -M:run`

- You could define global tasks by putting a `:run` alias in
  `~/.clojure/deps.edn`
