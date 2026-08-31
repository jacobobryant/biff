# Changelog

**Unreleased**, **breaking** 2.0.0-rc24-SNAPSHOT:

- biff.datastar: scope `:biff.datastar/tab-id` by user ID.
- biff.sqlite, biff.xtdb: set the biff.core DB functions via the same module
  that sets the connections/node.
- biff.tasks: allow publishing snapshot versions multiple times.
- biff.fx: remove support for returning a vector of maps for ordered effects;
  introduced :biff.fx/seq instead.
- biff.fx: add pipe and defpipe.
- biff.fx: inject :biff.fx/{random-uuid7-seq,random-uuid4-seq} into ctx
- biff.sqlite: add schema-module
- biff.xtdb: add schema-module
- biff.core: accept maps for :biff.core/init values
- biff.tasks: format task adds blank lines to separate split form pairs
- biff.tasks: add :biff/impl-visibility clj-kondo rule

**Breaking** [2.0.0-rc23](RELEASES/2.0.0-rc23.md) (2026-08-29):

- biff.core: components are now module lifecycle functions
- biff.fx: state functions have additional arguments
- biff.ring: defroute is removed
- biff.ring: path only takes template strings, not route vectors
- biff.graph: defresolver uses the new biff.fx format
