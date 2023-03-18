---
title: Queries
---

As mentioned last section, Biff uses [XTDB](https://xtdb.com/) for the
database. See the
[XTDB query reference](https://docs.xtdb.com/language-reference/datalog-queries/).

Biff provides a couple query convenience functions. `com.biffweb/q` is a *very*
light wrapper around `xtdb.api/q`. First, it will throw an exception if you
pass an incorrect number of arguments to `:in`.

```clojure
(q db
   '{:find [user]
     :in [email color]
     :where [[user :user/email email]
             [user :user/color color]]}
   "bob@example.com") ; Oops, we forgot to pass in a color--ask me sometime
                      ; how often I've made this mistake.
```

Second, if you omit the vector around
the `:find` value, the results will be scalars instead of tuples. For example,
the following queries are equivalent:

```clojure
(require '[xtdb.api :as xt])
(require '[com.biffweb :as biff])

(map first
     (xt/q db
           '{:find [email]
             :where [[user :user/email email]]}))

;; Think of all the carpal tunnel cases we're preventing by eliminating the
;; need for constant map firsts!
(biff/q db
        '{:find email
          :where [[user :user/email email]]})
```

`com.biffweb/lookup` is a bit like `xtdb.api/entity`, except you pass in an
arbitrary key-value pair instead of a document ID:

```clojure
(lookup db :user/email "bob@example.com")
;; =>
{:xt/id #uuid "..."
 :user/email "bob@example.com"
 :user/favorite-color :chartreuse}
```

There is also `lookup-id` which returns the document ID instead of the entire document.

See also:

 - [XTDB query reference](https://docs.xtdb.com/language-reference/datalog-queries/)
