# Build spec

## Commands covered

- `uberjar`

## uberjar

- `uberjar` MUST remain available as an optional packaging path.
- `uberjar` MUST delete `target/` before building.
- `uberjar` MUST generate minified CSS before building.
- `uberjar` MUST compile the configured main namespace and package app resources.
- `uberjar` MUST NOT run the test suite.
- `uberjar` MUST keep the current default output filename: `target/jar/app.jar`.
- `uberjar` SHOULD stay minimal and MUST NOT introduce extra behavior or flags
  beyond the current packaging flow unless a new need appears.

## Non-goals

- Uberjar/Docker workflows are not the primary Biff 2.0 deployment path.
- These specs do not require additional Docker-specific tasks.
