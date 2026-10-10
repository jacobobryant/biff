# com.biffweb.sqlite.schema API

```
Helpers for defining columns. All functions take varargs that can include:

- flags: :unique, :required, and/or :index in any order
- followed by a map of additional options

e.g. (s/text :required :unique {:extra-schema ...})
```

### ?

[view source](../../src/com/biffweb/sqlite/schema.clj#L10)

```
Short-hand for {:optional true}. Can be used in Malli schemas.
```

### pkey

[view source](../../src/com/biffweb/sqlite/schema.clj#L24)

```
Short-hand for {:type :uuid :primary-key true}
```

### ref

[view source](../../src/com/biffweb/sqlite/schema.clj#L29)

```
(ref target & args)

Returns a map with {:ref target, :type :uuid, ...}
```

### enum

[view source](../../src/com/biffweb/sqlite/schema.clj#L34)

```
(enum enum-values & args)

Returns a map with {:type :enum, :enum-values enum-values, ...}
```

### edn

[view source](../../src/com/biffweb/sqlite/schema.clj#L39)

```
(edn extra-schema & args)

Returns a map with {:type :edn, :extra-schema extra-schema, ...}
```

### text

[view source](../../src/com/biffweb/sqlite/schema.clj#L44)

```
(text & args)

Returns a map with {:type :text, ...}
```

### inst

[view source](../../src/com/biffweb/sqlite/schema.clj#L49)

```
(inst & args)

Returns a map with {:type :inst, ...}
```

### int

[view source](../../src/com/biffweb/sqlite/schema.clj#L54)

```
(int & args)

Returns a map with {:type :int, ...}
```

### bool

[view source](../../src/com/biffweb/sqlite/schema.clj#L59)

```
(bool & args)

Returns a map with {:type :boolean, ...}
```

### blob

[view source](../../src/com/biffweb/sqlite/schema.clj#L64)

```
(blob & args)

Returns a map with {:type :blob, ...}
```

### uuid

[view source](../../src/com/biffweb/sqlite/schema.clj#L69)

```
(uuid & args)

Returns a map with {:type :uuid, ...}
```

### real

[view source](../../src/com/biffweb/sqlite/schema.clj#L74)

```
(real & args)

Returns a map with {:type :real, ...}
```
