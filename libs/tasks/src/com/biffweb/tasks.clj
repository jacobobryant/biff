(ns com.biffweb.tasks
  "A collection of CLI tasks for use with biff.run. See docs/config.md."
  (:refer-clojure :exclude [format test update])
  (:require [com.biffweb.tasks.app :as app]
            [com.biffweb.tasks.lib :as lib]))

(def
  ^{:dynamic true

    :doc
    "Tasks that call other tasks can bind this to override the user's config."}
  *extra-config*
  {})

(def
  ^{:doc
    "A collection of tasks for applications.

     Included tasks:

     - code-quality (see `app-code-quality`)
     - css
     - deploy
     - dev
     - format
     - lint
     - nrepl
     - prod-logs
     - prod-nrepl
     - prod-restart
     - prod-setup
     - setup
     - test
     - uberjar
     - update

     Use `:main-opts [\"-m\" \"com.biffweb.tasks.app\"]` as the entrypoint for
     these tasks."}
  app-tasks app/tasks)

(def
  ^{:doc
    "A collection of tasks for libraries.

     Included tasks:

     - code-quality (see `lib-code-quality`)
     - docs
     - format
     - lint
     - nrepl
     - publish
     - test
     - update

     Use `:main-opts [\"-m\" \"com.biffweb.tasks.lib\"]` as the entrypoint for
     these tasks."}
  lib-tasks lib/tasks)

(defn agent-refresh
  "A function coding agents can call over nREPL after they update source files.

   - Evaluates changed files without unloading them first.
   - Then runs the `lint` task.
   - Then runs the `test` task.

   Returns a map containing either `:status :ok` or `:status :error, :exception
   ...`. Also includes `:out` and `:err` (stdout and stderr)."
  []
  ((requiring-resolve 'com.biffweb.tasks.impl.agent/agent-refresh)))

(defn add
  "Add the latest release of a dependency to deps.edn.

   Usage:

     add com.example/example                 # maven dep
     add https://github.com/example/example  # git dep"
  [& args]
  (apply (requiring-resolve 'com.biffweb.tasks.impl.add/add) args))

(defn app-code-quality
  "Format, lint, and test code.

  Runs the following tasks:

  - update --clj-kondo-files-onle
  - format
  - lint
  - test

  You can run this task in a CI workflow and ensure afterward there are no
  unstaged changes (e.g. from formatting changes etc)."
  []
  ((requiring-resolve 'com.biffweb.tasks.impl.code-quality/app-code-quality)))

(defn lib-code-quality
  "Format, lint, and test code, and generate API docs.

  Runs the following tasks:

  - update --clj-kondo-files-only
  - format
  - docs
  - lint
  - test

  You can run this task in a CI workflow and ensure afterward there are no
  unstaged changes (e.g. from formatting changes etc)."
  []
  ((requiring-resolve 'com.biffweb.tasks.impl.code-quality/lib-code-quality)))

(defn css
  "Compile CSS with Tailwind.

   Reads the following config keys:

   - :biff.tasks/tailwind-version
   - :biff.tasks/css-output-path

   If there is not a `tailwind` executable on the path with the version
   specified by `tailwind-version`, downloads a binary to `target/bin/tailwind`.

   Writes the generated CSS to `css-output-path`. `args` are passed to the
   `tailwind` executable with `-i resources/tailwind.css` included."
  [& args]
  (apply (requiring-resolve 'com.biffweb.tasks.impl.css/css) args))

(defn deploy
  "Deploy to a server provisioned with the prod-setup task.

   Reads the following config keys:

   - :biff.tasks/domain (required)
   - :biff.tasks/deploy-untracked-files
   - :biff.tasks/deployment-name
   - :biff.tasks/nrepl-port
   - :biff.tasks/skip-ssh-agent

   Accepts the following CLI options:

     --soft    Evaluates files on the server instead of running `prod-restart`

   Runs the `css --minify` task, force pushes the current git branch to
   `/home/{deployment-name}/repo` on the server, pushes any additional files
   listed in `deploy-untracked-files` (such as the compiled CSS), then runs the
   `prod-restart` task.

   The local git repo must have a clean worktree. If you pass --soft, the server
   must be running an nREPL server on `nrepl-port` and it must have the `trench`
   command installed (handled by `prod-setup`).

   The deployed application must:

   - Include a :prod alias in deps.edn that starts the app in production.
   - Use the PORT environment variable for the webserver port."
  [& args]
  (apply (requiring-resolve 'com.biffweb.tasks.impl.deploy/deploy) args))

(defn dev
  "Start the app in dev mode.

   Reads the following config keys:

   - :biff.tasks/main-ns (required)

   Ensures all :paths / :extra-paths directories from deps.edn exist. Runs the
   `css --watch` task in the background. Starts another file watcher that
   evaluates source files and their dependants when saved.

   Then calls the `-main` function in the `main-ns` namespace."
  []
  ((requiring-resolve 'com.biffweb.tasks.impl.dev/dev)))

(defn docs
  "Generate API docs.

   Reads the following config keys:

   - :biff.tasks/docs-namespaces (required)
   - :biff.tasks/docs-directory

   Generates a markdown file in `docs-directory` for each namespace in
   `docs-namespaces` containing the namespace and var docstrings.

   Each namespace will be required and thus must be on the classpath."
  []
  ((requiring-resolve 'com.biffweb.tasks.impl.docs/docs)))

(defn format
  "Format code with cljfmt.

   Reads the following config keys:

   - :biff.tasks/cljfmt-version

   If there is not a `cljfmt` executable on the path with the version specified
   by `cljfmt-version`, downloads a binary to `target/bin/cljfmt`.

   Runs `cljfmt fix --parallel [files]` on all the Clojure and EDN files in the
   current project.

   Attempts to use `git ls-files` to get a list of the project files. Otherwise,
   uses :paths and :extra-paths from deps.edn."
  []
  ((requiring-resolve 'com.biffweb.tasks.impl.format/format)))

(defn lint
  "Lint code with clj-kondo.

   Reads the following config keys:

   - :biff.tasks/clj-kondo-version

   If there is not a `clj-kondo` executable on the path with the version
   specified by `clj-kondo-version`, downloads a binary to
   `target/bin/clj-kondo`.

   Runs `clj-kondo --parallel --lint [files]` on all the Clojure and EDN files
   in the current project.

   Attempts to use `git ls-files` to get a list of the project files. Otherwise,
   uses :paths and :extra-paths from deps.edn."
  []
  ((requiring-resolve 'com.biffweb.tasks.impl.lint/lint)))

(defn nrepl
  "Start an nREPL server.

   Reads the following config keys:

   - :biff.tasks/nrepl-port (required)

   Thin wrapper around nrepl.cmdline/-main. Sets `--port <nrepl port>` and
   `--middleware [cider.nrepl/cider-middleware]`. Passes on `args` to `-main`.

   If the first arg is `--`, calls `-main` without setting `--port` or
   `--middleware`. Pass `-- --help` to see nrepl.cmdline's help."
  [& args]
  (apply (requiring-resolve 'com.biffweb.tasks.impl.nrepl/nrepl) args))

(defn prod-logs
  "Tail logs from the server.

   Reads the following config keys:

   - :biff.tasks/domain (required)
   - :biff.tasks/deployment-name

   Accepts a single, optional `n-lines` CLI argument, default 300. Runs
   `journalctl -u {deployment-name} -n {n-lines} -f` on the server."
  [& args]
  (apply (requiring-resolve 'com.biffweb.tasks.impl.prod/prod-logs) args))

(defn prod-nrepl
  "Start an SSH tunnel to the production nREPL server.

   Reads the following config keys:

   - :biff.tasks/nrepl-port (required)
   - :biff.tasks/domain (required)
   - :biff.tasks/deployment-name

   The server is expected to already have an nREPL server running on
   `nrepl-port`."
  [& args]
  (apply (requiring-resolve 'com.biffweb.tasks.impl.prod/prod-nrepl) args))

(defn prod-restart
  "Restart the application in production.

   Reads the following config keys:

   - :biff.tasks/domain (required)
   - :biff.tasks/deployment-name

   Runs `systemctl restart` on the server."
  [& args]
  (apply (requiring-resolve 'com.biffweb.tasks.impl.prod/prod-restart) args))

(defn prod-setup
  "Provision a server so the app can be deployed to it.

   Reads the following config keys:

   - :biff.tasks/domain (required)
   - :biff.tasks/deployment-name
   - :biff.tasks/skip-ssh-agent

   Accepts the following CLI options:

     --copy-only    copy the setup script to the server but don't run it.

   You must have SSH access as root to the (Ubuntu) server pointed to by
   `domain`. Runs a setup script on the server that:

   - Installs packages with apt-get.

   - Creates a user (named by `deployment-name`).

   - Copies /root/.ssh/authorized_keys to ~/.ssh for the new user.

   - Creates a systemd service (named by `deployment-name`) that runs `clj
     -M:prod` in the ~/repo directory for the new user on system startup. The
     PORT env variable is set to a unique port (in case you setup multiple apps
     on this server).

   - Installs Caddy and configures it to forward requests for `domain` to the
     app's unique port.

   - Sets up the firewall with ufw, allowing only ports for http, https, and
     ssh.

   After running this task, you can deploy your application with the `deploy`
   task.

   The script is only tested on Ubuntu, though it may work on other Debian-based
   distros."
  [& args]
  (apply (requiring-resolve 'com.biffweb.tasks.impl.prod/prod-setup) args))

(defn publish
  "Publish library to Clojars with deps-deploy.

   Reads the following required config keys:

   - :biff.tasks/group-name
   - :biff.tasks/lib-name
   - :biff.tasks/lib-version
   - :biff.tasks/pom-data
   - :biff.tasks/pom-scm
   - :biff.tasks/clojars-secret
   - :biff.tasks/clojars-username

   And the following optional keys:

   - :biff.tasks/gpg-sign-key-id
   - :biff.tasks/gpg-sign-wih-passphrase
   - :biff.tasks/monorepo"
  [& args]
  (apply (requiring-resolve 'com.biffweb.tasks.impl.publish/publish) args))

(defn init
  "Initialize a freshly cloned project.

   Reads the following config keys:

   - :biff.tasks/main-ns
   - :biff.tasks/clj-kondo-version
   - :biff.tasks/cljfmt-version
   - :biff.tasks/tailwind-version

   This task can be run after cloning a project template and after cloning a
   project that's already been initialized previously.

   If the project's current namespace is com.example, prompts for a new
   namespace and rewrites files accordingly.

   Generates default config.env and config.prod.env files if they don't already
   exist yet and their corresponding template config files
   (resources/TEMPLATE.config.env and resources/TEMPLATE.config.prod.env) do
   exist. Text like `{{ new-secret 32 }}` in the template files will be replaced
   with a randomly-generated (via SecureRandom/getInstanceStrong) base64-encoded
   byte array of the given length.

   Ensures that `clj-kondo`, `cljfmt`, and `tailwind` are installed with the
   specified versions. If not, downloads them to target/bin/.

   Then runs the `update --clj-kondo-files-only` task."
  []
  ((requiring-resolve 'com.biffweb.tasks.impl.init/init)))

(defn test
  "Run tests with Kaocha.

   Thin wrapper around kaocha.runner."
  [& args]
  (apply (requiring-resolve 'com.biffweb.tasks.impl.test/test) args))

(defn uberjar
  "Generate an uberjar.

   Reads the following config keys:

   - :biff.tasks/main-ns (required)

   Deletes target/resources/ (if it's in deps.edn :paths), runs the `css
   --minify` task, then writes an uberjar file to target/jar/app.jar via
   `clojure.tools.build.api/uber`. Directories from deps.edn's :paths that
   include \"resources\" in their name are copied into the jar."
  []
  ((requiring-resolve 'com.biffweb.tasks.impl.uberjar/uberjar)))

(defn update
  "Update dependencies with antq and update clj-kondo files.

   CLI options:

     --deps-only               don't update clj-kondo files.
     --clj-kondo-files-only    don't update dependencies.

   Reads the following config keys:

   - :biff.tasks/clj-kondo-version

   If there is not a `clj-kondo` executable on the path with the version
   specified by `clj-kondo-version`, downloads a binary to
   `target/bin/clj-kondo`.

   Updates clj-kondo cache and dependency configs per the instructions in
   https://github.com/clj-kondo/clj-kondo#project-setup (`--parallel
   --dependencies --copy-configs --lint <classpath>`)."
  [& args]
  (apply (requiring-resolve 'com.biffweb.tasks.impl.update/update) args))
