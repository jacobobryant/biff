# biff.tasks specs

These documents describe the intended behavior of `biff-tasks` for the Biff 2.0
refresh. They are behavioral contracts, not user-facing reference docs.

## Repo-wide assumptions

- The tasks are primarily for Biff application repos, not as a general-purpose
  task runner for arbitrary Clojure projects.
- The task set should work well for both:
  - humans doing local interactive development
  - coding agents working in sandboxes or remote environments
- Task-owned config keys MUST use the `:biff.tasks/*` namespace.
- This is a clean break. Old task names and old config keys do not need to be
  preserved unless a spec says otherwise.
- Prefer explicit commands and flags over hidden behavior inferred from the
  environment.

## How to read these specs

Each spec tries to answer:

- what the command is for
- what it MUST or SHOULD do
- what config it depends on
- what is intentionally out of scope
- what questions are still open

The specs should stay useful after the current implementation changes. They
should be updated in the same PR as any behavior change.
