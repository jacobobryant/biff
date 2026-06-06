# Assets and code-quality spec

## Commands covered

- `css`
- `format`
- `lint`

## CSS

### Purpose

Compile the app stylesheet from `resources/tailwind.css` into the configured
output path.

### Behavioral contract

- `css` MUST compile CSS to the configured output path.
- `css` MUST continue to support the existing Tailwind installation modes:
  local binary, global binary, npm, and bun.
- `css` MUST support `:biff.tasks/tailwind-version`.
- If `:biff.tasks/tailwind-version` is set, `css` MUST ignore npm and bun and
  MUST use managed binary resolution instead.
- When managed binary resolution is in effect, `css` MUST install
  `bin/tailwindcss` if no compatible local/system binary is available or if a
  pinned version does not match the installed version.
- If `:biff.tasks/tailwind-version` is not set, npm and bun MUST take
  precedence over managed and system binaries.
- If npm and bun are unavailable, `bin/tailwindcss` MUST take precedence over a
  system `tailwindcss` binary.
- If Tailwind is not otherwise available, `css` MUST install the default
  managed Tailwind binary.
- `css` SHOULD remain opinionated about the managed local Tailwind binary
  location; it does not need an extra config knob for that path in the first
  pass.
- `css` SHOULD continue to pass through watch/minify-style Tailwind flags rather
  than inventing a second flag layer unless a Biff-specific flag adds real
  value.

## Format

### Purpose

Apply the default Biff 2.0 formatting rules with `cljfmt`.

### Behavioral contract

- `format` MUST derive its main formatting scope from tracked repo files.
- `format` MUST ignore tracked files that live under hidden directories.
- `format` MUST enable aligned map bindings and aligned `let` bindings.
- `format` MUST be safe to run repeatedly without introducing further diffs once
  the repo is formatted.
- `format` MUST auto-install a managed `cljfmt` binary under `bin/cljfmt` when
  no compatible local or system binary is available.
- `format` MUST support `:biff.tasks/cljfmt-version` for pinning the managed
  binary version.
- `format` MUST exit nonzero if formatting fails.

## Lint

### Purpose

Lint tracked Clojure and EDN files with `clj-kondo`.

### Behavioral contract

- `lint` MUST lint tracked `.clj`, `.cljs`, `.cljc`, and `.edn` files.
- `lint` MUST ignore tracked files that live under hidden directories.
- `lint` MUST auto-install a managed `clj-kondo` binary under `bin/clj-kondo`
  when no compatible local or system binary is available.
- `lint` MUST support `:biff.tasks/clj-kondo-version` for pinning the managed
  binary version.
- `lint` MUST exit nonzero if linting finds issues or if lint execution fails.

## Non-goals

- `format` does not need to format markdown specs.
- `setup` does not need to generate `cljfmt` config for the first pass.

## Notes

- Starter apps may include their own checked-in `cljfmt` config as part of the
  app template rather than having `setup` generate it.
