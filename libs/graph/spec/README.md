# biff.graph specs

These documents describe the intended behavior of `biff.graph`. They are
behavioral contracts, not user-facing reference docs.

## Repo-wide assumptions

- `biff.graph` stays intentionally small; specs should prefer explicit contracts
  over inference or magic.
- Resolver metadata and query vectors are both part of the public behavior
  surface. If a behavior change affects either, the corresponding spec should be
  updated in the same PR.
- Map-shaped entity relationships SHOULD be explicit at every boundary where
  they are declared or consumed.

## How to read these specs

Each spec tries to answer:

- what problem the behavior is meant to solve
- which surfaces are affected
- what the system MUST or SHOULD do
- what is intentionally out of scope
- what questions are still open
