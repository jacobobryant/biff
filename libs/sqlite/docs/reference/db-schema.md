# Database schema

Sqlite database schema is described as a \"columns\" map (stored under
`:biff.sqlite/columns`) where the keys are table / column names and each value
is an options map:

```clojure
{:my-table/id         {:type :uuid :primary-key true},
 :my-table/created-at {:type :inst},
 ...}
```

This format lets you specify richer types than what regular sqlite schema
(`CREATE TABLE`) allows, and biff.sqlite handles the type coercion automatically
for both reads and writes. For example, instants and  booleans are both stored
in sqlite as integers but are converted to `Instant` and `Boolean` in-process.

One table will be created for each unique namespace in your column keys,
converted to snake case (e.g. `my_table` for the example above). The key names
denote the column names, also snake case (e.g. `id`, `created_at`).

The following keys are supported in the column options. Only `:type` is
required:

```
:type
  :int, :real, :text, :boolean, :inst, :uuid, :enum, :edn, or :blob. :edn values
  may be maps, sets, vectors, or lists and are stored as nippy-encoded byte
  arrays (blobs). :blob values are byte arrays. :inst values are stored as
  milliseconds since the epoch and are returned as `java.time.Instant`s. :enum
  values are qualified keywords; see :enum-values.

:primary-key
  Boolean. Implies :required.

:unique
  Boolean. Adds a single-column UNIQUE constraint on the table.

:unique-with
  Sequence of keywords (other column keys). Creates a multi-column UNIQUE
  constraint on the table, with the current column coming first.

:required
  Boolean. Sets a NOT NULL constraint.

:ref
  Keyword (a primary column key). Adds a FOREIGN KEY constraint.

:index
  Boolean. Adds a single-column CREATE INDEX statement. (For multi-column
  indexes, see :biff.sqlite/extra-init-sql).

:enum-values
  Map of int -> qualified keyword. Defines the mapping between int values stored
  in sqlite and the semantic qualified keywords they represent. Each enum
  keyword must be globally unique across your entire schema; it's recommended to
  have the enum keyword namespace match the column keyword. Must be used with
  `:type :enum`. Example:

    :my-table/color {:type :enum,
                     :enum-values {0 :my-table.color/red,
                                   1 :my-table.color/blue}}

:extra-schema
  Malli schema for validating column values, beyond what can be inferred by the
  :type value.
```

Use [schema-sql](../api/com.biffweb.sqlite.md#schema-sql) to see exactly how the
columns map translates to sqlite statements.

## Migrations

[sqldef](https://github.com/sqldef/sqldef) is used on application startup to
infer what schema migrations need to be performed based on your current schema
as described by `:biff.sqlite/columns` (and `:biff.sqlite/extra-init-sql`).
