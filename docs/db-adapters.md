# How to write a Biff database adapter

If you want to make it easy to use a particular database in a Biff application,
you'll probably want to write an adapter library. So far I've released [an
adapter library for SQLite](/libs/sqlite/), and I plan to write at least one
more adapter library for XTDB 2.

## Core functionality

To streamline integration with other Biff libraries, database adapter libraries
should provide at least these things:

- A biff.core component that handles any startup logic needed, such as starting
  a connection pool, running migrations, etc. The component should provide
  relatively high-level options for configuring the database, with defaults
  wherever possible. The component doesn't need to support every possible way
  the database can be configured: users can always use their own component if
  needed.

- A biff.core module with a `:biff.core/init` function that returns
  implementations for `:biff.core/kv-set`, `:biff.core/kv-get`, and
  `:biff.core/kv-list`. If the database has a way to ensure that multiple
  queries see a consistent view of the database, then the `:biff.core/init`
  function should also return `:biff.core/wrap-read-tx`. See [biff.core's schema
  reference](/libs/core/docs/reference/schema.md).

- The module should also include a
  [`:biff.fx/handlers`](/libs/fx/docs/reference-schema.md#biff-fx-handlers) map
  containing function(s) for reading/writing to the database. The value of
  `:biff.fx/handlers` can also be exposed as a standalone `fx-handlers` var for
  biff.fx users who aren't using biff.core.

- There should be some kind of mechanism for calling the `:biff.core/on-tx`
  function if it is set on the system map. The function should be called after a
  transaction occurs. Read queries which run after `on-tx` is called should be
  able to see data from the transaction. `on-tx` does not have to receive any
  particular information about the transaction that ran; it's strictly a
  notification function. You _may_ pass additional information to `on-tx`, and
  applications (which know the concrete database they're using) can take
  advantage of that information. Other libraries however shouldn't make
  assumptions about what database is being used.

  If the database supports multi-node deployments, this should be done in a way
  so that the function is called on each node. If the database does not have a
  built-in way to notify subscribers about new transactions, polling is
  potentially an acceptable option, ideally with the ability for transactions
  submitted from the current node to trigger a notification immediately.

  If the database only supports a single node deployment and there's no way to
  subscribe to transactions, you can provide a write function that calls
  `on-tx`, with the expectation that users will use that function for all their
  writes.

- There should be a function that generates biff.graph resolvers based on an
  application's database schema, as [described
  here](/docs/graph/README.md#defining-resolvers).

If you provide wrapper functions for reading/writing, these should generally
take a `ctx` map as the first parameter which can be expected to contain any
keys returned by the biff.core component.

## Extra functionality

Besides that, you can include whatever additional functionality you think may be
helpful for users. Some examples that I've implemented for various databases:

- Schema enforcement: if the database doesn't enforce schema sufficiently
  strictly, you can provide a write function that adds schema enforcement (e.g.
  via Malli).

- Rich types: if the database can't natively store a sufficient number of types,
  you can provide read/write functions that handle converting between rich types
  and lower-level types that the database supports.

- High-level write operations: if the database only supports relatively
  low-level write operations, you can provide helper functions and/or a custom
  transaction format that support higher-level operations.

- Authorization rules: you can provide a write function which generates a "diff"
  for a given transaction (the set of entities affected by the transaction,
  including their values before and after the transaction) and passes the diff
  the a user-supplied authorization function. If the authorization function
  doesn't return truthy, the transaction is aborted.

- HoneySQL: for SQL databases, if you're providing a write function anyway, you
  can have it accept HoneySQL maps in addition to SQL strings.

## Database schema

Some of these features will require your adapter library to understand the
application's schema:

- biff.graph resolvers will need to know (1) the set of all fields an entity has
  so that those fields can be declared in the `:output` query; (2) which of
  those fields are foreign key references (and which primary key fields they
  reference) so that they can be declared as join attributes. thouBesides foreign
  key fields, resolvers don't need to know the types of the fields.

- Schema enforcement and rich type conversion will likely require a complete
  understanding of the application's schema, if your adapter includes those
  features.

If the database's native format for defining schema is sufficient for your
adapter's needs, that should be used instead of introducing a new layer for
defining schema. If not, you can define your own schema format and then convert
it into the format the database needs. The database schema should be received by
your library as a key in the system map. Users should be encouraged to provide
it via a biff.core module's `:biff.core/init` function, if they use biff.core.

Biff intentionally does not define a generic schema format. Knowledge of an
adapter library's schema format is encapsulated within that library.
