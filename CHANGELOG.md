# Changelog

## 2.0.0

- same as 2.0.0-26

## 2.0.0-26

- biff.tasks: the dev task runs the init task when needed.
- biff.tasks: the add task syncs clj-kondo configs.

## 2.0.0-rc25

Bug fixes:

- biff.tasks: create target/resources properly if needed.

## 2.0.0-rc24

**Breaking:**

- biff.fx: remove support for returning a vector of maps for ordered effects;
  introduced :biff.fx/seq instead.

Non-breaking:

- biff.core: accept maps for :biff.core/init values
- biff.fx: add pipe and defpipe.
- biff.fx: inject :biff.fx/{random-uuid7-seq,random-uuid4-seq} into ctx
- biff.sqlite, biff.xtdb: set the biff.core DB functions via the same module
  that sets the connections/node.
- biff.sqlite: `module` accepts schema options
- biff.xtdb: add schema-module
- biff.datastar: scope `:biff.datastar/tab-id` by user ID.
- biff.tasks: allow publishing snapshot versions multiple times.
- biff.tasks: `publish --local` installs artifacts into the local Maven
  repository.
- biff.tasks: format task adds blank lines to separate split form pairs
- biff.tasks: add `:biff/impl-visibility` clj-kondo rule
- biff.tasks: init task initializes git repo

## 2.0.0-rc23 (2026-08-29)

[Release notes](RELEASES/2.0.0-rc23.md).

**Breaking:**

- biff.core: components are now module lifecycle functions
- biff.fx: state functions have additional arguments
- biff.ring: defroute is removed
- biff.ring: path only takes template strings, not route vectors
- biff.graph: defresolver uses the new biff.fx format
