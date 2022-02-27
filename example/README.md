# Biff example project

This is the example/template project for Biff.

## Requirements

 - Unix environment
 - [clj](https://clojure.org/guides/getting_started)

## Commands

### `./task dev`

Starts the app locally. After running, wait for the `System started` message.
Connect your editor to nrepl port 7888. Files will be auto-eval'd whenever you
save a file. Static HTML and CSS files will also be regenerated on file save.

### `./task clean`

Deletes generated files.

### `./task deploy`

`rsync`s config files to the server, deploys code via `git push`, and restarts
the app process on the server (via git push hook).

Before running this (and all subsequent commands), you must set up a server and
set the domain in `config.sh`. For example:

1. Create an Ubuntu VPS in DigitalOcean
2. Point your domain at it
3. Run `scp setup.sh root@$YOUR_DOMAIN:`
4. Run `ssh root@$YOUR_DOMAIN`, then `bash setup.sh`

### `./task soft-deploy`

`rsync`s config and code to the server, then `eval`s any changed files and
regenerates HTML and CSS files. Does not restart the app process and does not
restart the system.

### `./task refresh`

Reloads code and restarts the system via
`clojure.tools.namespace.repl/refresh` (on the server).

### `./task restart`

Restarts the app process via `systemctl restart app` (on the server).

### `./task logs`

Tail the server's application logs.

### `./task prod-repl`

Open an SSH tunnel so you can connect to the server via nREPL.

### `./task prod-dev`

Runs `./task logs` and `./task prod-repl`. In addition, whenever you save a
file, it will be copied to the server (via rsync) and eval'd, after which HTML
and CSS will be regenerated.
