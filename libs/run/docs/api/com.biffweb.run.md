# com.biffweb.run API

### run-task

[view source](../../src/com/biffweb/run.clj#L32)

```
(run-task task-name & args)

Runs a given task from the tasks passed to `main`.

When `main` is called, it stores its `tasks` argument globally. This function
runs one of those tasks.

Uses the help logic described in `main`.
```

### main

[view source](../../src/com/biffweb/run.clj#L77)

```
(main tasks & args)

Runs a given task.

`tasks` is a map from task names to task options, like:

  "my-task" {:task 'com.example.tasks.my-task/my-task
             :doc  "one line description of the task."}

`args` is a sequence of command line arguments (strings). If the first
argument is -h / --help / help, or if no arguments are provided, prints the
available tasks with their :doc values. Otherwise the first argument must be
a key in `tasks`, and the :task symbol will be resolved.

If the first remaining argument is -h / --help / help, prints the task
function's docstring. (This behavior can be disabled by setting `:help
:invoke` on the task options.) Otherwise, calls the task function and passes
the remaining arguments to it.

Then calls System/exit.
```
