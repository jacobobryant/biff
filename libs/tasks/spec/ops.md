# Operations spec

## Commands covered

- `deploy`
- `prod-install`
- `prod-restart`
- `prod-nrepl`
- `prod-logs`

## Naming

- Production SSH tasks MUST use the `prod-` prefix.
- `deploy` is the only production-affecting command that does not need the
  prefix.

## Deploy

### Purpose

Ship reviewed code to production in a way that is explicit, auditable, and easy
to reason about from a trusted machine.

### Behavioral contract

- `deploy` MUST deploy via git, not rsync.
- `deploy` MUST deploy from a trusted shell operated by a human.
- `deploy` MUST deploy the current `HEAD` commit of the current branch.
- `deploy` MUST fail if `HEAD` is detached.
- `deploy` MUST fail if the worktree has uncommitted changes.
- `deploy` MUST build minified CSS before deployment.
- `deploy` MUST force-push tracked files.
- `deploy` MUST push the current local branch to a same-named branch on the
  server repo.
- `deploy` MUST switch the server repo's checked-out branch to the deployed
  branch after the push.
- `deploy` MUST use git for tracked files and `scp` for configured extra files.
- `deploy` MUST support configured extra files via `:biff.tasks/deploy-untracked-files`.
- The starter app SHOULD configure deploy extras through
  `:biff.tasks/deploy-untracked-files`, including:
  - `config.prod.env` uploaded as `config.env`
  - compiled CSS uploaded with its existing path
- `deploy` MUST NOT hardcode knowledge of `config.prod.env` or compiled CSS;
  the starter app config owns that wiring.
- `deploy` SHOULD support `--soft` instead of a separate `soft-deploy` command.
- Without `--soft`, `deploy` MUST restart the production app after the push.
- With `--soft`, `deploy` SHOULD perform the remote no-downtime eval/reload flow
  instead of a restart.
- With `--soft`, `deploy` SHOULD use the same tools.namespace-based dependency
  ordering that `dev` uses locally for evaluating project source files.
- With `--soft`, `deploy` SHOULD compute that reload plan on the trusted local
  machine and send ordinary evaluation forms over nREPL, rather than requiring
  a deploy-helper namespace from `biff-tasks` to be on the production
  classpath.
- With `--soft`, the production process MUST only need nREPL access plus the
  checked-out repo tree that `deploy` has already updated.
- With `--soft`, `deploy` MUST NOT run tests on the server.
- With `--soft`, `deploy` MAY leave deleted code or removed vars resident in
  the running process; matching a fresh boot is a non-goal for soft deploys.
- The default intended operator flow is a human running `deploy` from a trusted
  machine where checking logs is convenient.
- `deploy` SHOULD provision `/home/$APP/repo` as a git repo if it does not yet
  exist.
- The server repo SHOULD be a normal checked-out repo using
  `receive.denyCurrentBranch=updateInstead`.

### Recommended default direction

- Do not generate a default GitHub Actions deploy workflow.
- Prefer manual deployment from a trusted machine over automatic deployment from
  agent-controlled branches.
- It is acceptable for the systemd service to fail until the first deploy.
- The server setup script may create `/home/$APP/repo` as an empty directory,
  leaving repo initialization to `deploy`.

## prod-restart

- `prod-restart` MUST restart the systemd-managed app service over SSH.
- `prod-restart` MUST target the systemd unit name derived from
  `:biff.tasks/deployment-name`.

## prod-install

### Purpose

Provision or refresh the shared Biff server setup on a target machine using the
canonical setup script shipped inside `biff-tasks`.

### Behavioral contract

- `prod-install` MUST be fully non-interactive.
- `prod-install` MUST read its required inputs from task config rather than
  positional CLI arguments.
- `prod-install` MUST source the canonical script from
  `resources/com/biffweb/tasks/server-setup.sh`.
- `prod-install` MUST upload that script to the server and run it remotely as
  root.
- `prod-install` MUST assume direct SSH access as `root` rather than relying on
  remote `sudo` escalation.
- `prod-install` MUST read the server-side app/deployment name from
  `:biff.tasks/deployment-name`.
- `prod-install` MUST read the target host/domain from `:biff.tasks/domain`.
- `prod-install` MUST NOT require the app repo to check in its own copy of the
  server setup script.
- `prod-install` SHOULD support repeated runs on the same server so multiple
  Biff apps can coexist.
- The canonical setup script MUST support multiple apps on one server via an app
  name from config.
- The setup script SHOULD be idempotent enough that rerunning it repairs or
  reapplies expected Biff-managed server state.
- Repeated `prod-install` runs MUST be non-destructive to other Biff apps that
  are already configured on the same server.
- `prod-install` MUST own only the shared Biff server baseline, not arbitrary
  app-specific provisioning.
- App-specific extra provisioning SHOULD live in separate user-managed scripts or
  tooling outside `biff-tasks`.
- The shared Biff server baseline MUST include reverse proxy and TLS setup.
- The first-pass baseline SHOULD provision that web front-end using Caddy.
- The setup script SHOULD create `/home/$APP/repo` as an empty directory.
- The setup script MUST NOT own git repo bootstrap beyond creating that
  directory; `deploy` owns repo initialization.
- `prod-install` MUST remain separate from `deploy`; it MUST NOT automatically
  ship or restart app code as part of provisioning.

### Recommended default direction

- The first-pass production provisioning command SHOULD be `prod-install`.
- The setup script SHOULD come from the new `biff-starter-sqlite` version and
  then evolve centrally in `biff-tasks`.
- `prod-install` SHOULD upload the setup script to a temporary remote path,
  execute it, and clean it up afterward.

## prod-nrepl

- `prod-nrepl` MUST open an SSH tunnel to the production nREPL port.
- `prod-nrepl` MUST use `:biff.tasks/nrepl-port` as the remote port.
- `prod-nrepl` MUST write the local forwarded port to `.nrepl-port`.

## prod-logs

- `prod-logs` MUST tail production logs over SSH.
- `prod-logs` MUST target the systemd unit name derived from
  `:biff.tasks/deployment-name`.
- `prod-logs` SHOULD default to `journalctl -u <service> -n 300 -f`.
