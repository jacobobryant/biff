# Changelog

I am far too lazy to follow semver. The commit hash is the version. I suppose
if you wanted to see the hash for a particular section in this file, you could
use git blame.

## Unreleased
### Changed
- In new projects, run the production jar from the home directory instead of
  the release directory.
- Use crux.api/db instead of crux.api/open-db when adding db to incoming
  requests and events.
### Fixed
- Make biff.util/prepend-keys ignore non-keyword keys instead of crashing.

## 2021-06-04
### Fixed
- Don't break subscriptions when an auth rule rejects a transaction
- JDK8 works now (downgraded to Jetty 9)

## 2021-05-14
### Added
- This changelog.

### Changed
- Just about everything; far too much to document here. But I'll keep this
  up-to-date going forward.
