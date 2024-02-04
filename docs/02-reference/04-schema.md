---
title: Schema
---

XTDB (the database Biff uses) does not enforce schema on its own. Biff provides schema enforcement
with [Malli](https://github.com/metosin/malli). Here's a Malli crash course.

Say we want to save a user document like this:

```clojure
{:xt/id #uuid "..."
 :user/email "bob@example.com"
 :user/favorite-color :blue
 :user/age 132}
```

We could model it in our Malli schema like so:

```clojure
(def schema
  {:user/id :uuid
   :user [:map {:closed true}
          [:xt/id                     :user/id]
          [:user/email                :string]
          [:user/joined-at            inst?]
          [:user/foo {:optional true} :string]
          [:user/bar {:optional true} :string]]
   ...})
```

For the attribute types, Malli has a handful of
[type schemas](https://github.com/metosin/malli#mallicoretype-schemas) like `:uuid`
and `:string` above, and it also supports
[predicate schemas](https://github.com/metosin/malli#mallicorepredicate-schemas) like
`inst?` above.

For compactness, most of the attributes (like `:user/email` and `:user/joined-at`) are
defined inside the document schema. But for the `:xt/id` key, it often makes sense to create
an alias (`:user/id` in this example) so that it can be used in other documents. For example, the
`:msg` document below includes a `:msg/user` key which has a value of type `:uuid`, and the `:user/id`
key makes it clear that this key is meant to refer to a `:user` document:

```clojure
(def schema
  {:user/id :uuid
   ...

   :msg/id :uuid
   :msg [:map {:closed true}
         [:xt/id       :msg/id]
         [:msg/user    :user/id]
         ...]})
```

Note however that Biff doesn't enforce that the `:msg/user` key actually points
to a user document. As long as the key is a UUID, it will pass the schema
check.

For mild convenience, your schema can be defined with the `biff/doc-schema` helper function:

```clojure
(ns com.example.schema
  (:require [com.biffweb :refer [doc-schema] :rename {doc-schema doc}]))

(def schema
  {:user/id :uuid
   :user (doc {:required [[:xt/id          :user/id]
                          [:user/email     :string]
                          [:user/joined-at inst?]]
               :optional [[:user/foo       :string]
                          [:user/bar       :string]]})
   ...})
```

See also:

 - [`doc-schema`](/docs/api/misc/#doc-schema)
 - [Malli documentation](https://github.com/metosin/malli)
 - [Malli built-in schemas](https://github.com/metosin/malli#built-in-schemas)
