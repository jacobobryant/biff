# Transaction Ops

biff.xtdb accepts regular XTDB 2 transaction operations, and it adds support for
a few custom operations which expand to XTDB operations before the transaction
is submitted.

`execute-tx` and `submit-tx` expand custom ops. They also validate docs in
`:put-docs` and `:patch-docs` operations with `biff.core/validate` after
expansion.

`authorized-write` currently does not support the built-in custom ops since
they expand to SQL assertions, and `authorized-write` rejects SQL ops.

### :biff/assert-unique

```clojure
[:biff/assert-unique table kvs]
```

Expands to an XTDB SQL assertion that checks there is at most one row in
`table` matching `kvs`.

```clojure
[:biff/assert-unique :user {:user/email "ada@example.com"}]
```

### :biff/upsert

```clojure
[:biff/upsert table on & records]
```

Looks up existing records in `table` by the attributes in `on`. New records are
inserted with `:put-docs`; existing records are updated with `:patch-docs`.

```clojure
[:biff/upsert :user [:user/email]
 {:user/email     "ada@example.com"
  :user/score     1
  :biff/on-insert {:user/created-at (java.util.Date.)}
  :biff/on-update {:user/updated-at (java.util.Date.)}}]
```

If a record is inserted:

- `:xt/id` defaults to a random UUID.
- `:biff/on-insert` is merged into the inserted record.
- `:biff/on-update` is ignored.

If a record is updated:

- `:xt/id` is set to the existing record's ID.
- `:biff/on-update` is merged into the patched record.
- `:biff/on-insert` is ignored.

The expanded transaction includes assertions so the transaction will fail if the
lookup attributes stop being unique before the transaction commits.
