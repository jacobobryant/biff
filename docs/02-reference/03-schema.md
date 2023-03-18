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

In our Malli schema, we'll first define schemas for each of the user document's attributes:

```clojure
(def schema
  {:user/id :uuid
   :user/email :string
   :user/favorite-color :keyword
   :user/age number?

   ...})
```

For the schema map values, Malli has a handful of
[type schemas](https://github.com/metosin/malli#mallicoretype-schemas) like `:uuid`
and `:string` above, and it also supports
[predicate schemas](https://github.com/metosin/malli#mallicorepredicate-schemas) like
`number?` above.

Once our attributes have been defined, we can combine them into a schema for
the user document itself:

```clojure
(def schema
  {...
   :user [:map {:closed true}
          [:xt/id :user/id]
          :user/email
          [:user/favorite-color {:optional true}]
          [:user/age {:optional true}]]
   ...})
```

In English, this means:

 - `:user` is a map (all of our document schemas will be maps).
 - It's a closed map: it's not allowed to have any keys that we haven't
   included in the `:user` schema.
 - `:user/email` is a required attribute.
 - `:user/favorite-color` and `:user/age` are optional attributes.
 - `:xt/id` has the same schema as `:user/id` (which happens to be `:uuid`).

A note about `:xt/id`. Every other attribute is defined globally, outside of
the document schema. e.g. `:user/email` is defined globally to be a string.
However, every document must have an `:xt/id` attribute, and different types of
documents may need different schemas for `:xt/id`. So we define the schema for
`:xt/id` locally, within the document.

See also:

 - [Malli documentation](https://github.com/metosin/malli)
 - [Malli built-in schemas](https://github.com/metosin/malli#built-in-schemas)
