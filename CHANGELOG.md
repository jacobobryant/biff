# Changelog

Unreleased:

- biff.datastar: scope `:biff.datastar/tab-id` by user ID.
- biff.sqlite, biff.xtdb: set the biff.core DB functions via the same module
  that sets the connections/node.
- biff.tasks: allow publishing snapshot versions multiple times.

**Breaking** [2.0.0-rc23](RELEASES/2.0.0-rc23.md) (2026-08-29):

- biff.core: components are now module lifecycle functions
- biff.fx: state functions have additional arguments
- biff.ring: defroute is removed
- biff.ring: path only takes template strings, not route vectors
- biff.graph: defresolver uses the new biff.fx format
