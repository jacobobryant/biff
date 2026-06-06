# biff.tasks spec index

## Current spec set

- `bootstrap.md` — setup-time repo generation and updates
- `assets.md` — CSS compilation plus format/lint code-quality tasks
- `runtime.md` — local development, test running, and local nREPL
- `ops.md` — deploy and production SSH tasks
- `build.md` — uberjar packaging

## Cross-cutting decisions

- All task config keys MUST use `:biff.tasks/*`.
- The new task surface does not preserve old task names by default.
- SSH-based production tasks SHOULD use a `prod-` prefix, except for `deploy`.
- Deployment is manual from a trusted shell; there is no default deploy GitHub
  Action.

## Planned task surface

- `setup`
- `css`
- `format`
- `lint`
- `dev`
- `test`
- `nrepl`
- `deploy`
- `prod-install`
- `prod-restart`
- `prod-nrepl`
- `prod-logs`
- `uberjar`
