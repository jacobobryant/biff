---
title: Scheduled Tasks
---

Biff uses [chime](https://github.com/jarohen/chime) to execute functions on a
recurring schedule. For each task, you must provide a function to run and a
zero-argument schedule function which will return a list of times at which to
execute the task function. The schedule can be an infinite sequence. For example, here's
a task that prints out the number of users every 60 seconds:

```clojure
(require '[com.biffweb :as biff :refer [q]])

(defn print-usage [{:keys [biff/db]}]
  (let [n-users (first (q db
                          '{:find (count user)
                            :where [[user :user/email]]}))]
    (println "There are" n-users "users.")))

(defn every-minute []
  (iterate #(biff/add-seconds % 60) (java.util.Date.)))

(def features
  {:tasks [{:task #'print-usage
            :schedule every-minute}]})
```

See also:

 - [chime documentation](https://github.com/jarohen/chime)
 - [`use-chime`](https://github.com/jacobobryant/biff/blob/bdd1bd81d95ee36c615495a946c7c1aa92d19e2e/src/com/biffweb.clj#L297)
