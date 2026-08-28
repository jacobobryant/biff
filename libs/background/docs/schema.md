# Schema

### :biff.background/job

A map optionally containing `:biff.background/priority`.

### :biff.background/priority

Int, default 10. Jobs with lower values are processed first.

### :biff.background/queue

A `AbstractQueue`, default `PriorityBlockingQueue` with a comparator that sorts
by `:biff.background/priority`.

### :biff.background/queue-id

Qualified keyword.

### :biff.background/queue-map

Map that can contain the keys:

```clojure
:consumer    ; (fn [{:biff.background/keys [job queue] :as ctx}])
:queue       ; :biff.background/queue
:n-threads   ; Positive int, default 10. Number of threads for :executor.
:executor    ; ExecutorService (fixed thread pool)
:state       ; atom containing a :biff.background/queue-state map
```

Before the queues component starts, only `:consumer` is required while `:queue`
and `:n-threads` are optional. After startup, all keys are present.

### :biff.background/queue-state

Map containing the keys:

```clojure
:continue   ; Default true, used as a shutdown signal for queue consumers.
:processing ; A set of indices, each between 0 and :n-threads, denoting which
            ; executor threads are currently processing jobs.
```

### :biff.background/queues

Map from `:biff.background/queue-id` to `:biff.background/queue-map`.

### :biff.background/tasks

Seqeunce of `:biff.background/task`.

### :biff.background/task

Map containing the keys:

```clojure
:schedule       ; (fn []) -> possibly infinite sequence of Instant
:task           ; (fn [ctx])
:error-handler  ; option for chime.core/chime-at
:on-finished    ; option for chime.core/chime-at
```

`:schedule` and `:task` are required.

### :biff.background/stop-timeout

Int (milliseconds), default 10,000. On system shutdown, how long to wait for
queue consumers to finish processing in-flight jobs before forcefully shutting
them down.
