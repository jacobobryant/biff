---
title: Transaction Listeners
---

XTDB maintains an immutable transaction log. You can register a listener
function which will get called whenever a new transaction has been appended to
the log. If you provide a function for the `:on-tx` module key, Biff will
register it for you and pass the new transaction to it. For example, here's a
transaction listener that prints a message whenever there's a new user:

```clojure
(defn alert-new-user [{:keys [biff.xtdb/node]} tx]
  (let [db-before (xt/db node {::xt/tx-id (dec (::xt/tx-id tx))})]
    (doseq [[op & args] (::xt/tx-ops tx)
            :when (= op ::xt/put)
            :let [[doc] args]
            :when (and (contains? doc :user/email)
                       (nil? (xt/entity db-before (:xt/id doc))))]
      (println "there's a new user"))))

(def module
  {:on-tx alert-new-user})
```

The value of `tx` looks like this:

```clojure
{:xtdb.api/tx-id 9,
 :xtdb.api/tx-time #inst "2022-03-13T10:24:45.432-00:00",
 :xtdb.api/tx-ops ([:xtdb.api/put
                    {:xt/id #uuid "dc4b4893-d4f1-4876-b4c5-6f87f5abcd7d",
                     :user/email "hello@example.com"}]
                   ...)}
```

See also:

 - [`use-xtdb-tx-listener`](/docs/api/xtdb/#use-xtdb-tx-listener)
 - [`xtdb.api/listen`](https://v1-docs.xtdb.com/clients/clojure/#\_listen)
