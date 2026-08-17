# com.biffweb.background API

### use-scheduled-tasks

[view source](../../src/com/biffweb/background.clj#L36)

```
(use-scheduled-tasks {:keys [biff.background/tasks], :as ctx})

Calls chime.core/chime-at for each `task`.

Each task function receives ctx as its sole argument.
```

### use-queues

[view source](../../src/com/biffweb/background.clj#L44)

```
(use-queues {:keys [biff.background/queues], :as ctx})

Initializes a queue and fixed executor thread pool for each entry in
`queues`.

See :biff.background/queues. `conj`s a shutdown function on the
:biff.core/stop key.
```

### submit-jobs

[view source](../../src/com/biffweb/background.clj#L54)

```
(submit-jobs #:biff.background{:keys [queues]} queue-id jobs)

Adds `jobs` to the specified queue.

queue-id - :biff.background/queue-id
jobs     - Sequence of :biff.background/job

`conj`s a shutdown function on the :biff.core/stop key.
```

### fx-handlers

[view source](../../src/com/biffweb/background.clj#L65)

```
A biff.fx handlers map containing
`:biff.background.fx/submit-jobs submit-jobs`
```

### module

[view source](../../src/com/biffweb/background.clj#L70)

```
(module)

A biff.core module that:

- Provides :biff.fx/handlers.
- Aggregates :biff.background/tasks and :biff.background/queues from other
  modules. Tasks and queues are only aggregated on startup, not whenever
  modules change.
```
