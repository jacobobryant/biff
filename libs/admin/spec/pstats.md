# biff.admin pstats behavior

This document defines the intended profiling-data behavior for `biff.admin`.

## In-memory shape

` :biff.admin/pstats ` stores a map of:

- `day-string -> tufte pstats`

Where `day-string` is the current UTC date formatted like `2026-05-22`.

Request and resolver profiling MUST merge new samples into the entry for the
current UTC day.

## Flush behavior

The scheduled flush task MUST:

1. Deref every in-memory pstats value to plain Clojure data
2. Write each day’s deref’d value to the KV store under that same day key
3. Overwrite any existing KV value for those day keys
4. Keep the current UTC day in memory
5. Remove all non-current days from the in-memory map

The flush task MUST NOT merge stored KV values back into live pstats.

## KV format

The values written to KV MUST be plain Clojure data derived from deref’ing the
day’s Tufte pstats. They MUST be suitable for portable KV implementations and
MUST NOT depend on Tufte internal types being serialized.

## Reading recent stats

When preparing recent admin metrics:

- read the last seven UTC day keys from the KV store
- discard malformed stored values by deleting them from the KV store
- overlay in-memory day values for the same keys

The admin dashboard formats these values as grouped per-day stats rather than a
single merged pstats value.
