---
title: Tasks
---

Biff provides a set of project tasks via the `:dev` alias in `deps.edn`. Run
`clj -M:dev --help` to see a list of available tasks:

```bash
$ clj -M:dev --help
Available commands:

  clean  - Deletes generated files
  css    - Generates the target/resources/public/css/main.css file.
  deploy - Pushes code to the server and restarts the app.
  dev    - Starts the app locally.
  ...
```

For convenience, you can add `alias biff='clj -M:dev'` to your `.bashrc`.

The set of available tasks is defined in `dev/tasks.clj`. If you want to create
your own tasks, you can add them here. To keep startup time as low as possible,
it's recommended to load libraries with `requiring-resolve` so that they only
get loaded if you run a task that needs them:

```clojure
(defn frobnicate []
  "Frobnicates your project."
  ((requiring-resolve 'foo.bar/frobnicate)))

(def custom-tasks
  {"frobnicate" #'frobnicate
   ...
```

If you need to modify the behavior of any of Biff's default tasks, you can copy
[the source
code](https://github.com/jacobobryant/biff/blob/release/libs/tasks/src/com/biffweb/tasks.clj)
for the task into `dev/tasks.clj` and add it to `custom-tasks`.
