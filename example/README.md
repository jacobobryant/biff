# Biff example project (WIP)

This is (the start of) the example/template project for Biff.

## Requirements

 - Unix environment (for running `task`). I'm possibly open to rewriting `task` in
Babashka if that makes things easier for anyone (people on Windows not using
WSL?).
 - [clj](https://clojure.org/guides/getting_started)

## Setup

1. Copy config.edn.TEMPLATE to config.edn.
2. Edit `task` and update ARCH if needed.
3. Run `./task install-tailwind`
4. (Recommended) add `alias t=./task` to your .bashrc

## Development

Run `./task dev`. Wait for the `System started` message. Connect your editor to
nrepl port 7888. Files will be auto-eval'd whenever you save a file. Static
HTML and CSS files will also be regenerated on file save.
