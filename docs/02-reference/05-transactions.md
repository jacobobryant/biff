---
title: Transactions
---

*Biff uses [XTDB](https://xtdb.com/) for the database. It's OK if you haven't used XTDB before,*
*but you may want to peruse some of the [learning resources](https://xtdb.com/learn) at least.*

The system map (and by extension, incoming requests) includes a
`:biff.xtdb/node` key which can be used to submit transactions:

```clojure
(require '[xtdb.api :as xt])

(defn send-message [{:keys [biff.xtdb/node session params] :as ctx}]
  (xt/submit-tx node
    [[::xt/put {:xt/id (java.util.UUID/randomUUID)
                :msg/user (:uid session)
                :msg/text (:text params)
                :msg/sent-at (java.util.Date.)}]])
  ...)
```

Biff also provides a higher-level wrapper over `xtdb.api/submit-tx`. It lets
you specify document types from your schema. If the document you're trying to
write doesn't match its respective schema, the transaction will fail. In
addition, Biff will call `xt/await-tx` on the result, so you can read your
writes.

```clojure
(require '[com.biffweb :as biff])

(defn send-message [{:keys [session params] :as ctx}]
  (biff/submit-tx
    ;; select-keys is for illustration. Normally you would just pass in ctx.
    (select-keys ctx [:biff.xtdb/node :biff/malli-opts])
    [{:db/doc-type :message
      :msg/user (:uid session)
      :msg/text (:text params)
      :msg/sent-at (java.util.Date.)}])
  ...)
```

If you don't set `:xt/id`, Biff will use `(java.util.UUID/randomUUID)` as the default value.
The default operation is `:xtdb.api/put`.

Biff transactions can also include regular XT operations. Any element of the transaction
that isn't a map will be assumed to be an XT operation:

```clojure
(biff/submit-tx sys
  [{:db/doc-type :user
    :db/op :merge
    :xt/id (or (biff/lookup-id db :user/email email)
               (java.util.UUID/randomUUID))
    :user/email email}
   [::xt/fn :biff/ensure-unique {:user/email email}]])
```

If there is contention (e.g. if two concurrent requests attempt to write to
update the same document, or if a transaction function fails), the transaction
will be retried up to three times. If you pass a function that returns a transaction,
then the function will be called again before each retry:

```clojure
(biff/submit-tx ctx
  (fn [{:keys [biff/db]}]
    [{:db/doc-type :user
      :db/op :merge
      :xt/id (or (biff/lookup-id db :user/email email)
                 (java.util.UUID/randomUUID))
      :user/email email}
     [::xt/fn :biff/ensure-unique {:user/email email}]]))
```

As a convenience, any occurrences of `:db/now` will be replaced with `(java.util.Date.)`:

```clojure
(defn send-message [{:keys [session params] :as ctx}]
  (biff/submit-tx ctx
    [{:db/doc-type :message
      :msg/user (:uid session)
      :msg/text (:text params)
      :msg/sent-at :db/now}])
  ...)
```

Similarly, any keywords with a namespace of `db.id` will be replaced with random UUIDs:

```clojure
(biff/submit-tx sys
  [{:db/doc-type :user
    :xt/id :db.id/bob
    :user/email "bob@example.com"}
   {:db/doc-type :msg
    :msg/user :db.id/bob
    :msg/text "I am bob"}
   {:db/doc-type :user
    :xt/id :db.id/alice
    :user/email "alice@example.com"}
   {:db/doc-type :msg
    :msg/user :db.id/alice
    :msg/text "I am not bob"}])
```

## Document operations

You can delete a document by setting `:db/op :delete`:

```clojure
(defn delete-message [{:keys [params] :as ctx}]
  (biff/submit-tx ctx
    [{:xt/id (java.util.UUID/fromString (:msg-id params))
      :db/op :delete}])
  ...)
```

If you set `:db/op :update` or `:db/op :merge`, the document will be merged
into an existing document if it exists. The difference is that `:update` will
cause the transaction to fail if the document doesn't already exist, while `:merge`
will create the document.

```clojure
(defn set-foo [{:keys [session params] :as ctx}]
  (biff/submit-tx ctx
    [{:db/op :update
      :db/doc-type :user
      :xt/id (:uid session)
      :user/foo (:foo params)}])
  ...)
```

There is also a `:db/op :create` operation which is the opposite of
`:db/op :update`: the transaction will fail if the document *does* exist.

Biff uses `:xtdb.api/match` operations to ensure that concurrent
merge/update operations don't get overwritten. If the match fails, the
transaction will be retried up to three times.

You can use `:db.op/upsert` to update a document with the given attributes,
or create a new one if doesn't exist yet:

```clojure
(biff/submit-tx sys
  [{:db/doc-type :user
    :db.op/upsert {:user/email "hello@example.com"}
    :user/joined-at :db/now}])
```

If the document is created, `:xt/id` will be set to `(random-uuid)`. A transaction function is
used to make sure the operation is atomic. Besides that, upsert operations work the same as
`:db/op :merge` operations.

**Note:** You must have installed the `:biff/ensure-unique` transaction
function for this to work. This is done by default in new projects. See
[`com.biffweb/tx-fns`](/docs/api/xtdb/#tx-fns).

## Attribute operations

Some operations can be used on a per-attribute basis. Often these operations use
the attribute's previous value, along with new values you provide, to determine
what the final value should be.

Use `:db/union` to coerce the previous value to a set and insert new values
with `clojure.set/union`:

```clojure
[{:db/op :update
  :db/doc-type :post
  :xt/id #uuid "..."
  :post/tags [:db/union "clojure" "almonds"]}]
```

Use `:db/difference` to do the opposite:

```clojure
[{:db/op :update
  :db/doc-type :post
  :xt/id #uuid "..."
  :post/tags [:db/difference "almonds"]}]
```

Add to or subtract from numbers with `:db/add`:

```clojure
[{:db/op :update
  :db/doc-type :account
  :xt/id #uuid "..."
  :account/balance [:db/add -50]}]
```

Use `:db/default` to set a value only if the existing document doesn't
already contain the attribute:

```clojure
[{:db/op :update
  :db/doc-type :user
  :xt/id #uuid "..."
  :user/favorite-color [:db/default :yellow]}]
```

Use `:db/dissoc` to remove an attribute:

```clojure
[{:db/op :update
  :db/doc-type :user
  :xt/id #uuid "..."
  :user/foo :db/dissoc}]
```

Use `:db/unique` to ensure that no other document has the same value for the given attribute:

```clojure
[{:db/doc-type :user
  :xt/id #uuid "..."
  :user/handle [:db/unique "hunter2"]}]
```

**Note:** You must have installed the `:biff/ensure-unique` transaction
function for this to work. This is done by default in new projects. See
[`com.biffweb/tx-fns`](https://biffweb.com/docs/api/xtdb/#tx-fns).

### `:db/lookup`

**Note:** `:db/lookup` is deprecated. It's recommended to use `:db.op/upsert`
instead, as is done in the starter project.

Finally, you can use `:db/lookup` to enforce uniqueness constraints on attributes
other than `:xt/id`:

```clojure
[{:db/doc-type :user
  :xt/id [:db/lookup {:user/email "hello@example.com"}]}]
```

This will use a separate "lookup document" that, if the user has been created already, will
look like this:

```clojure
{:xt/id {:user/email "hello@example.com"}
 :db/owned-by ...}
```

where `...` is a document ID. If the document doesn't exist, the ID will be `(java.util.UUID/randomUUID)`,
unless you pass in a different default ID with `:db/lookup`:

```clojure
[{:db/doc-type :user
  :xt/id [:db/lookup {:user/email "hello@example.com"} #uuid "..."]}]
```

If the first value passed along with `:db/lookup` is a map, it will get merged
in to the document. So our entire transaction would end up looking like this, assuming
the user document doesn't already exist:

```clojure
[{:db/doc-type :user
  :xt/id [:db/lookup {:user/email "hello@example.com"}]}]
;; =>
[[:xtdb.api/put {:xt/id #uuid "abc123"
                 :user/email "hello@example.com"}]
 [:xtdb.api/match {:user/email "hello@example.com"} nil]
 [:xtdb.api/put {:xt/id {:user/email "hello@example.com"}
                 :db/owned-by #uuid "abc123"}]]
```

See also:

 - [`submit-tx`](/docs/api/xtdb/#submit-tx)
 - [XTDB learning resources](https://xtdb.com/learn/)
 - [XTDB transaction reference](https://v1-docs.xtdb.com/language-reference/datalog-transactions/)
