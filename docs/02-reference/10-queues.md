---
title: Queues
---

Biff can create [concurrent queues](https://docs.oracle.com/javase/7/docs/api/java/util/concurrent/PriorityBlockingQueue.html)
and [thread pools](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ExecutorService.html) for you.
For example:

```clojure
(defn echo-consumer [{:keys [biff/job] :as ctx}]
  (prn :echo job)
  (when-some [callback (:biff/callback job)]
    (callback job)))

(def module
  {:queues [{:id :echo
             :consumer #'echo-consumer
             :n-threads 1}]})

(biff/submit-job ctx :echo {:foo "bar"})
=>
(out) :echo {:foo "bar"}
true

@(biff/submit-job-for-result ctx :echo {:foo "bar"})
=>
(out) :echo {:foo "bar", :biff/callback #function[...]}
{:foo "bar", :biff/callback #function[...]}"
```

This can be useful for limiting the concurrency of certain operations. Note
that these queues are in-memory only; they are not persisted to the database.
There is also no retry logic for jobs that throw an exception.

See also:

 - [Queue API docs](/docs/api/queues/)
