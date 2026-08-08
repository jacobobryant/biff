# Config reference

Config is read from `resources/config.edn` and `config.env` via
[biff.config](/libs/config/). Example:

```clojure
;; resources/config.edn -- checked into source
{:biff.tasks/main-ns        com.example
 :biff.tasks/domain         #biff/env DOMAIN
 :biff.tasks/clojars-secret #biff/secret CLOJARS_SECRET
 ...}
```

```
# config.env -- gitignored
DOMAIN=example.com
CLOJARS_SECRET=abc123
```

### :biff.tasks/clj-kondo-version

String, default `"2026.05.25"`. The version of clj-kondo to install and use.

### :biff.tasks/cljfmt-version

String, default `"0.16.4"`. The version of cljfmt to install and use.

### :biff.tasks/clojars-secret

A string wrapped with
[`biff.core/secret-delay`](/libs/core/docs/api/com.biffweb.core.md#secret-delay)
(i.e. set in `resources/config.edn` with `#biff/secret ...`). A secret to use
for publishing libraries to Clojars.

### :biff.tasks/clojars-username

String. A username to use for publishing libraries to Clojars.

### :biff.tasks/css-output-path

String, default `"target/resources/public/css/main.css"`. The path at which to
write generated CSS.

### :biff.tasks/deploy-untracked-files

A vector describing which non-git-tracked files should be pushed to the server
when deploying. Default:

```clojure
["target/resources/public/css/main.css"
 {:src "config.prod.env" :dest "config.env"}]
```

Each element is either a string (relative file path) or a map
with keys `:src` (file path on the local machine) and `:dest` (file path on the
server).

### :biff.tasks/deployment-name

String, default `"app"`. When provisioning a server and deployment, this is used
for the name of the home directory (and associated user) the app will run under.
It's also used as the name of the associated systemd service. You can deploy
multiple applications to the same server by giving them each a unique
name.

### :biff.tasks/docs-directory

String, default `"docs/api"`. The directory in which API documentation will be
generated.

### :biff.tasks/docs-namespaces

Vector of namespace symbols. A markdown file will be generated for each
namespace, containing the namespace and var docstrings.

### :biff.tasks/domain

String, e.g. `"example.com"`. The domain of the server to deploy your app to.
You must have ssh access as root. The default Caddy configuration will forward
requests for this domain to your application. You can deploy multiple apps to
the same server as long as they have different domains.

### :biff.tasks/gpg-sign-key-id

String. When set, deps-deploy will request this key from the GPG agent. Takes
precedence over `:biff.tasks/gpg-sign-with-passphrase`.

### :biff.tasks/gpg-sign-with-passphrase

Boolean, default false. When set, deps-deploy will prompt for a GPG passphrase
when publishing as a library to Clojars. Ignored if
`:biff.tasks/gpg-sign-key-id` is set.

### :biff.tasks/group-name

String. The group name to use when publishing as a library to Clojars.

### :biff.tasks/lib-name

String. The lib name to use when publishing as a library to Clojars.

### :biff.tasks/lib-version

String. The version to use when publishing as a library to Clojars.

### :biff.tasks/main-ns

Symbol. The namespace containing the `-main` function for this application.

### :biff.tasks/monorepo

Boolean, default false. When set and publishing as a library to Clojars, any
`:local/root` dependencies with the same group name will have `:mvn/version` set
to value of `:biff.tasks/lib-version` in the published artifact.

### :biff.tasks/nrepl-port

Int. The nREPL port to use in local development and in production. If you want
to use nREPL in production, your application is responsible for starting an
nREPL server on this port.

### :biff.tasks/pom-license

A `:pom-data` value for `clojure.tools.build.api/write-pom` to use when
publishing as a library to Clojars. Example:

```clojure
[[:licenses
  [:license
   [:name "MIT"]
   [:url "https://opensource.org/license/mit"]
   [:distribution "repo"]]]]
```

### :biff.tasks/pom-scm

A `:scm` value for `clojure.tools.build.api/write-pom` to use when publishing as
a library to Clojars. Example:

```clojure
{:connection          "scm:git:git://github.com/example/example.git"
 :developerConnection "scm:git:ssh://git@github.com/example/example.git"
 :tag                 "HEAD"
 :url                 "https://github.com/example/example"}
```

### :biff.tasks/skip-ssh-agent

Boolean, default false. To prevent you from having to supply an SSH password
multiple times, tasks that run multiple SSH commands will attempt to start an
ssh-agent session with `ssh-add` if they aren't already running in a session.
Set this flag to disable that behavior.

### :biff.tasks/tailwind-version

String, default `"4.3.0"`. The version of Tailwind to install and use (as a
standalone binary).
