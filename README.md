# Biff

A simple and easy web framework for Clojure.

 - Main website: [biffweb.com](https://biffweb.com)
 - [Newsletter](https://biffweb.com/newsletter/)
 - [Documentation](https://biffweb.com/docs/)
 - [API](https://biffweb.com/api/)
 - [Community](https://biffweb.com/community/)

## Contributing

To hack on Biff:

1. `cd example`
2. `cp config.edn.TEMPLATE config.edn`
3. `cp config.sh.TEMPLATE config.sh`
4. Edit config.edn and set `:biff.middleware/cookie-secret` and
   `:biff/jwt-secret`. This is inconvenient right now, you can use the
   `new-secret` function in `new-project/src/com/biffweb/new_project.cl`.
5. `./task dev`. deps.edn declares a local dependency on the Biff library code.
