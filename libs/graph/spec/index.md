# biff.graph spec index

## Current spec set

- `join-descriptors.md` — explicit join descriptors for queries and resolver
  inputs/outputs

## Cross-cutting decisions

- `biff.graph` SHOULD fail fast when a key is used both as a scalar attribute
  and as a join.
- `[:*]` is an escape hatch for unknown map keys, not the default way to describe
  joins.
