Project structure

[ ] A1. model namespace contents
[ ] A2. app namespace contents
[ ] A3. one page-rendering handler per app namespace
[ ] A4. app namespace hierarchy reflects the UI hierarchy
[ ] A5. work namespace contents
[ ] A6. one workflow per work namespace
[ ] A7. uicomp namespace contents
[ ] A8. external API namespace contents
[ ] A9. module namespaces only expose modules
[ ] A10. fx handlers are defined centrally
[ ] A11. shared functions belong in lib
[ ] A12. single-module functions belong in that module
[ ] A13. semantic namespace names
[ ] A14. keep state in the system map

biff.fx

[ ] B1. effectful functions use biff.fx
[ ] B2. biff.fx state functions are pure and use fx handlers
[ ] B3. biff.fx/return is only used when needed
[ ] B4. machines and pipelines minimize state functions
[ ] B5. use existing fx handlers instead of defining new ones
[ ] B6. if you must define fx handlers, keep them small

biff.graph

[ ] C1. model resolver namespace placement
[ ] C2. domain data is computed by model resolvers, not lib functions
[ ] C3. database reads are done by model resolvers
[ ] C4. Ring handlers begin with biff.graph queries
[ ] C5. request parameters are extracted by resolvers
[ ] C6. resolvers do not duplicate database adapter resolvers

Database adapters

[ ] D1. authorization rules are current and enforce ownership
[ ] D2. authorization rules query ownership rather than accept out-of-band data
[ ] D3. user-initiated writes go through authorization rules
[ ] D4. SQL queries use HoneySQL

biff.ring

[ ] E1. route templates use defpath
[ ] E2. defpaths have the correct location

biff.datastar

[ ] F1. application pages use biff.datastar
[ ] F2. rendering updates are pushed by biff.datastar middleware
[ ] F3. tab-specific state uses biff.datastar/tab-id

Tests

[ ] G1. test namespace naming
[ ] G2. biff.fx state function coverage
[ ] G3. model resolver unit tests

Code style

[ ] H1. biff.sqlite columns map formatting
[ ] H2. lint and formatter configuration
[ ] H3. vector and map entry formatting
[ ] H4. biff.fx and biff.graph blank lines
[ ] H5. tick usage for date/time
[ ] H6. hiccup used for rendering HTML
