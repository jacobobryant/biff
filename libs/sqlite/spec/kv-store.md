# KV store contract

This document defines the behavioral contract for Biff KV handlers such as
`:biff.kv/get-value` and `:biff.kv/set-value`.

## Scope

This is an API contract for callers and implementations. It is **not** specific
to `biff.sqlite`, even though `biff.sqlite` is the current implementation in
this repo.

## Contract

- `:biff.kv/get-value` has signature `(fn [ctx namespace key])`
- `:biff.kv/set-value` has signature `(fn [ctx namespace key value])`
- `namespace` **MUST** be a qualified keyword
- `key` **MUST** be a string
- passing `nil` to `set-value` **MUST** delete the entry

## Value requirements

Values passed to `set-value` **MUST** be portable plain Clojure data, so that
different KV implementations can store them without custom serializers.

Allowed shapes include combinations of:

- `nil`
- strings
- booleans
- numbers
- keywords
- symbols
- maps
- vectors
- lists
- sets

Values **MUST NOT** rely on implementation-specific serialization support for
custom runtime types such as:

- records or deftypes
- Java objects
- `delay`s
- library-specific objects like Tufte `PStats`

If a library wants to persist custom runtime values, it **MUST** transform them
to plain data before calling `set-value`, and transform the plain data back
after `get-value`.

## Implementation notes

- `biff.sqlite` currently uses Nippy internally, but callers **MUST NOT** depend
  on Nippy-specific extensions or behavior.
- Other KV implementations may use a different storage format entirely.
