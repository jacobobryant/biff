---
title: Scheduled Tasks
---

Biff uses [chime](https://github.com/jarohen/chime) to execute functions on a
recurring schedule. For each task, you must provide a function to run, a
zero-argument schedule function which will return a list of times at which to
execute the task function, and an (optional) [error
handler](https://github.com/jarohen/chime#error-handling). The schedule can be
an infinite sequence. For example, here's a task that prints out the number of
users every 60 seconds:

```clojure
(require '[com.biffweb :as biff :refer [q]])

(defn print-usage [{:keys [biff/db]}]
  (let [n-users (first (q db
                          '{:find (count user)
                            :where [[user :user/email]]}))]
    (println "There are" n-users "users.")))

(defn every-minute []
  (iterate #(biff/add-seconds % 60) (java.util.Date.)))

(defn error-handler [error]
  (println "Uh oh!"))

(def module
  {:tasks [{:task #'print-usage
            :schedule every-minute
            :error-handler error-handler}]})
```

See also:

- [`use-chime`](/docs/api/misc/#use-chime)
- [chime documentation](https://github.com/jarohen/chime)
