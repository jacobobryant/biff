# Runtime spec

## Commands covered

- `dev`
- `test`
- `nrepl`

## Dev

### Purpose

Start the app locally and provide development-time automation that used to live
in app-specific watcher code.

### Behavioral contract

- `dev` MUST start the application locally for interactive development.
- `dev` MUST ensure required generated files and dependencies are in place
  before app startup.
- `dev` MUST start a local nREPL server and write the port to `.nrepl-port`.
- `dev` MUST watch for changed Clojure source files and process them in this
  order: format code, evaluate source files, lint, then run tests.
- `dev` MUST derive the source roots it evaluates from `deps.edn` `:paths`
  rather than from hardcoded directory names.
- `dev` MUST run linting and the full test suite after relevant file changes.
- `dev` MUST keep CSS up to date during development.
- `dev` MUST avoid retriggering itself indefinitely from file events caused by
  its own formatting step.
- Once a debounced watcher run begins, `dev` MUST ignore subsequent watcher
  events until shortly after formatting completes.
- `dev` SHOULD watch the same broad set of files as the old Biff dev workflow
  rather than narrowly watching only one file type.
- `dev` MUST NOT require app repos to carry their own dev-only watcher logic for
  file evaluation and test running.
- `dev` MUST NOT regenerate static HTML as part of the default Biff 2.0 flow.
- `dev` MUST NOT do a hard refresh or hard restart automatically; users can do
  that manually when needed.
- Beyond the explicit minified-CSS flag, `dev` SHOULD remain opinionated and
  SHOULD NOT grow extra runtime config knobs for watcher/test behavior in the
  first pass.

### Human vs. agent use

- The default `dev` mode SHOULD optimize for fast local human feedback.
- `dev` MUST default to unminified CSS output.
- `dev` SHOULD support an explicit `--minify-css` flag for agent/service mode.
- Coding-agent sandboxes MAY run `dev` under a systemd service that uses the
  `--minify-css` flag.
- Evaluation failures and test failures MUST be surfaced loudly in the terminal.
- `dev` MUST maintain a machine-readable status file at
  `.biff-dev-status.edn`.
- `.biff-dev-status.edn` SHOULD record a single latest overall status in a form
  that agents or UI overlays can inspect easily.
- `.biff-dev-status.edn` SHOULD include at least `:started-at` and `:status`.
- `.biff-dev-status.edn` SHOULD be parseable as plain EDN without requiring a
  custom `:default` reader option.
- The status file SHOULD support at least a `:running` state while eval/test
  work is in progress.
- `dev` SHOULD update the status file in a way that avoids getting stuck in
  `:running` after failures or interruptions (for example, by using
  try/finally-style cleanup around status transitions).
- The status file MUST include structured results for evaluation, linting, and
  tests, and SHOULD also record formatting status.
- If evaluation fails, `dev` SHOULD NOT run the test suite against stale code.
- If evaluation fails, the status file SHOULD preserve the underlying evaluation
  failure data.
- If linting fails, the status file SHOULD preserve the underlying lint failure
  data.
- If tests fail, the status file SHOULD preserve the structured test failure
  data.
- `dev` MUST run the full test suite on each relevant save, matching the old
  Biff behavior.

## Test

### Purpose

Run tests with Cognitect test runner.

### Behavioral contract

- `test` MUST run the project's tests from the `test/` path with Cognitect test
  runner.
- `test` SHOULD rely on the starter app's `:run` alias to make `test/`
  available on the classpath, rather than adding extra task-specific classpath
  handling.
- `test` MUST exit nonzero when any test fails or errors.
- `test` SHOULD remain simple and explicit; task-specific watch behavior belongs
  in `dev`, not in `test`.

## nREPL

### Purpose

Start a local nREPL server without booting the app.

### Behavioral contract

- `nrepl` MUST start an nREPL server locally.
- `nrepl` MUST NOT start the app.
- If `:biff.tasks/nrepl-port` is set, `nrepl` MUST use that port.
- If `:biff.tasks/nrepl-port` is not set, `nrepl` SHOULD defer to the nREPL
  library's default port-selection behavior.
- `nrepl` MUST write the selected port to `.nrepl-port`.
