# biff.background

Convenience functions or using in-memory scheduled tasks and queues.

### Dependency

```clojure
com.biffweb/background {:mvn/version "2.0.0-rc21"}
```

### Status

This library will be a release candidate until all [the other Biff 2
libraries](/README.md) have been released. Until then there could be breaking
changes, but I don't anticipate any.

## Reference

- [Schema](docs/schema.md)
- [API](docs/api/com.biffweb.background.md)

## Usage

### Setup

If using biff.core, add these to your modules/components:

```clojure
(require '[com.biffweb.background :as biff.background])

(def modules
  [biff.background/module
   ...])

(def components
  [...
   biff.background/use-queues
   biff.background/use-scheduled-tasks
   ...])
```

`use-scheduled-tasks` should come second so that scheduled tasks can submit
queue jobs. Similarly, `use-queues` should come after any other components that
need to submit jobs (like your webserver component if you want Ring handlers to
submit jobs).

If not using biff.core:

- Use `chime.core/chime-at` directly instead of `use-scheduled-tasks`.
- Call `use-queues` directly and use the returned map as input when calling
  `submit-jobs`.

### Define tasks and queues

Your biff.core modules may contain `:biff.background/tasks` and
`:biff.background/queues`:

```clojure
(defn every-5-minutes []
  (iterate #(.plusSeconds % (* 60 5)) (java.time.Instant/now)))

(defn my-task [ctx]
  ...)

(defn handle-my-queue [{:biff.background/queues [job] :as ctx}]
  ...)

(def module
  {:biff.background/tasks
   [{:schedule every-5-minutes
     :task     my-task}]

   :biff.background/queues
   {:com.example/my-queue
    {:consumer  handle-my-queue
     ;; Default is 1 thread per queue
     :n-threads 2}}})
```

If not using biff.core, pass a map containing `:biff.background/queues` to
`use-queues` directly.

### Submit queue jobs

Pass a vector of jobs to `submit-jobs`:

```clojure
(submit-jobs ctx
             :com.example/my-queue
             [{:message "hello"}
              {:message "there"}])
```

Each job map will be passed to the queue consumer as `:biff.background/job`. You
can include `:biff.background/priority` to change the order in which jobs are
processed:

```clojure
(submit-jobs ctx
             :com.example/my-queue
             [{:message                  "hello"
               :biff.background/priority 20}
              {:message                  "there"
               :biff.background/priority 0}])
```

Lower `priority` values come first. This can be useful e.g. if a queue can
accept jobs both from scheduled tasks and from user interactions (Ring
handlers), so you can give higher priority to jobs from user interactions. The
default priority is `10`.

To submit jobs with biff.fx:

```clojure
(fx/defmachine my-function
  :start
  (fn [request]
    {:biff.background.fx/submit-jobs
     [:com.example/my-queue [{:message "hello"}]]

     ...}))
```

## Tips

- biff.fx `defmachine` functions work nicely as task / queue consumer functions.

- Queue consumers that can handle multiple jobs at once can use `.drainTo`:

```clojure
(defn consumer [{:biff.background/keys [job queue] :as ctx}]
  (let [jobs (java.util.LinkedList.)
        _    (.drainTo queue jobs)
        jobs (into [job] jobs)]
    ...))
```

- I often write scheduled tasks that query the DB and then submit a batch of
  queue jobs (e.g. for sending daily emails).
