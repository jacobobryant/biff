# com.biffweb.background API

### submit-jobs

[view source](../../src/com/biffweb/background.clj#L36)

```
(submit-jobs #:biff.background{:keys [queues]} queue-id jobs)

Adds `jobs` to the specified queue.

queue-id - :biff.background/queue-id
jobs     - Sequence of :biff.background/job
```

### fx-handlers

[view source](../../src/com/biffweb/background.clj#L45)

```
A biff.fx handlers map containing
`:biff.background.fx/submit-jobs submit-jobs`
```

### module

[view source](../../src/com/biffweb/background.clj#L50)

```
(module)

A biff.core module that wraps both tasks-module and queues-module. Module ID
is :biff.background/module.

- Aggregates :biff.background/tasks and :biff.background/queues from other
  modules. Tasks and queues are only aggregated on startup, not whenever
  modules change.
```

### tasks-module

[view source](../../src/com/biffweb/background.clj#L60)

```
(tasks-module)

On start, calls chime.core/chime-at for each task. Module ID is
:biff.background/tasks-module.

Aggregates :biff.background/tasks from other modules. Each task function
receives the system map as its sole argument.
```

### queues-module

[view source](../../src/com/biffweb/background.clj#L69)

```
(queues-module)

On start, initializes a queue and fixed executor thread pool for each entry
in :biff.background/queues. Module ID is :biff.background/queues-module.

Provides a :biff.fx/handlers entry (:biff.background.fx/submit-jobs)
and aggregates :biff.background/queues from other modules.
```
