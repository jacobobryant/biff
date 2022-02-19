# Biff example project (WIP)

This is (the start of) the example/template project for Biff.

## Requirements

 - Unix environment (for running `task`). I am reluctantly open to rewriting
   `task` in Babashka if the cross-platformness makes things easier for anyone
   (people on Windows not using WSL?). At a minimum I'll try to make sure
   the bash code works on Macs (I use WSL myself).
 - [clj](https://clojure.org/guides/getting_started)

## Setup

1. Copy config.edn.TEMPLATE to config.edn. Update contents if needed.
2. Copy config.sh.TEMPLATE to config.sh. Update contents if needed.
3. (Recommended) add `alias t=./task` to your .bashrc

## Development

### `./task dev`

Starts the app locally. After running, wait for the `System started` message.
Connect your editor to nrepl port 7888. Files will be auto-eval'd whenever you
save a file. Static HTML and CSS files will also be regenerated on file save.

### `./task clean`

Deletes generated files.

## Production

### `./task deploy`

`rsync`s config files to the server, deploys code via `git push`, and restarts
the app process on the server (via git push hook).

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

### `./task prod-repl`

## Developing in production

### `./task prod-dev`

TODO stuff for local code + prod db
