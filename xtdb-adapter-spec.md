At docs/db-adapters.md there is a spec/guide for writing Biff database adapter
libraries. There is one implementation already at libs/sqlite/. I want you to
add an implementation for XTDB v2 at libs/xtdb/. There is some old xtdb2 code I
wrote at ~/xtdb-biff/src/com/biffweb/impl/xtdb2.clj which you can reference.
Note that this code was written for "Biff 1", whereas this DB adapter spec is
written for "Biff 2", so e.g. there are some new interfaces we need to
implement.

looks like I've also got some more old code at
https://github.com/jacobobryant/yakread/blob/migrate/src/com/yakread/util/biff_staging.clj

To clarify things discussed in the adapter spec, you'll need to read the
documentation for other biff libs such as biff.core, biff.graph, and biff.fx.

Notes:

- I think we can bring use-xtdb2 over pretty much as is, except the
  xtdb-specific keys we accept and return should all have `:biff.xtdb/`
  namespaces. do `:biff.xtdb/storage-*` instead of `:biff.xtdb.storage/*`, for
  example. `:biff/stop` should be `:biff.core/stop`. and rename to use-xtdb.

- we don't need ensure-dep; we can assume the needed xtdb2 lib(s) are on the
  classpath.

- For schema enforcement, let's simply use biff.core/validate. So if users want
  schema enforcement, they'll define their application schema with
  biff.core/register. We don't need to model required vs. optional keys like the
  old xtdb 2 code does; we'll just enforce that keys which are actually set have
  valid values.

- Our implementation of the `:biff.core/on-tx` interface will be kind of like
  what use-xtdb2-listener does, except we don't need to do quite as much. i.e.
  we don't need to bring over `tx-log`; we just need to coll `on-tx` when
  `latest-system-time` changes. When that happens we may as well include a
  `:biff.xtdb/latest-system-time` key in the `ctx` map we pass to `on-tx`. We
  should preserve the behavior where submitting a transaction on the current
  node triggers an immediate poll.

- let's bring `prefix-uuid` over

- for the kv functions, we should still do nippy encoding and store the result
  on some key like `:biff-xtdb-kv/value`. Both because XTDB doesn't necessarily
  support all the types you can round-trip through pr-str (e.g. it does zoned
  date times but not instants), and we don't want the kv value's keys to get
  indexed by xtdb (i.e. if you call kv-set with a map that has a `:foo/bar` key,
  you shouldn't be able to get that value by doing a regular xtdb query for
  records with `:foo/bar` columns).

- we can implement `wrap-read-tx` by causing wrapped queries to use a particular
  snapshot token.

- for the biff.graph resolvers we will need to have a partial understanding of
  the app's schema. How about we can have user's define a columns map similar to
  the one biff.sqlite uses, except the options map will only support two keys:
  :schema and :ref (both optional). :schema is a malli schema and is actually
  unrelated to biff.graph: we'll provide a columns->schema function which takes
  the columns map and returns a map that can be passed to biff.core/register,
  skipping any columns without :schema set. For :ref, the value will be the
  target primary key, same as in biff.sqlite. If the options map is empty,
  biff.graph will include it in the :output query as a non-join key.

  the primary key is always technically :xt/id, but let's see what it looks like
  if we have the resolvers take as input table-specific keywords like :user/id
  etc. So we'll have one resolver per table.

- no need for type coercion; xtdb's native types are sufficient.

- the code I linked on github does have some higher-level operations which you
  can use as if they were actual xtdb op keywords: :biff/upsert and
  :biff/assert-unique. may as well bring those over.

- I'm not sure if authorization rules are feasible, i.e. can we generate a diff
  without committing an actual transaction. investigate. If it's not feasible we
  can skip that. However if we restrict the transaction operations enough (e.g.
  don't accept arbitrary sql; only take things like :put-docs, :biff/upsert etc)
  and include the caveat that before docs are generated from the current
  snapshot and aren't guaranteed to be the state of the DB immediately prior to
  when the transaction actaully gets indexed, then it seems like we ought to be
  able to do something here.

- don't write any documentation (e.g. readmes, docstrings, reference docs). _I
  mean it_. I'll take  care of that later after the code is done.

- follow biff.sqlite and other released libs in this repo (biff.core,
  biff.config, biff.fx, biff.graph) for coding conventions, file structure etc.

- the public namespace should be com.biffweb.xtdb

- add xtdb-api and xtdb-core to the deps.edn

- we only need to support in-process nodes

- for the config, try to maintain a similar convenience API as what use-xtdb2
  supports, but update the implementation as needed to match the latest stable
  version of xtdb2.

- provide wrappers and fx handlers for both execute-tx and submit-tx. for
  submit-tx we'll need to trigger some work on some other thread (e.g. a thread
  started by use-xtdb) which waits for the transaction to be indexed before
  calling on-tx.

- the main write functions (i.e. not authorized-write) should have as similar to
  an api as the execute-tx/submit-tx functions they wrap, except for including
  additional custom biff operations as mentioned. validation will be done on a
  best-effort basis, e.g. if they user submits an sql string we may not be able
  to validate the values they pass in since we don't know what columns they're
  for. authorized-write however, as mentioned, will need to have a more
  restricted set of inputs it allows so that we can generate a diff reliably.

- for :biff/upsert, match the old behavior.

- for validating :patch-docs ops, just check the values passed in; don't query
  existing records. i.e. do what the old implementation does.

- for the graph schema, the table is inferred from the namespaces of the column
  keys, same as in biff.sqlite. One difference: we can assume there will always
  be a :<table>/id key (really :xt/id) even if it's not included in the columns
  map. although that's perhaps a moot point because either way the primary key
  isn't included in the :output query.

- for the kv store, the table should be biff-xtdb-kv. let's try doing a derived
  namespace + key value for :xt/id.

- tests can use an in-memory node.

- authorized-write should take a sequence of operations, like submit-tx and
  execute-tx. no need for a separate authorized-write-tx function like
  biff.sqlite has.
