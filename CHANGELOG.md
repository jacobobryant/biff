# Changelog

## v1.0.0 (2023-02-03)

Changes:

- Use Aero to parse config.
- Switch from bb tasks to plain clj-based tasks.
- When using `:db.op/upsert` on a document that doesn't yet exist, use the provided `:xt/id` value
  if there is one instead of always using `(random-uuid)` as the value.
- Make `use-xtdb` get the value of `:biff.xtdb.jdbc/jdbcUrl` from the `:biff/secret` function if
  it's defined as a secret.
- Documentation fixes/improvements.
- Make `server-setup.sh` delete Trenchman artifacts after installing.
- Add libs to forward Java logs to slf4j.

Thank you to [@mathisto](https://github.com/mathisto), [@carlos](https://github.com/carlos) and
[@olavfosse](https://github.com/olavfosse) for contributions.

### Upgrade instructions

- Update your Biff dependencies (in `deps.edn`, `bb/deps.edn` and/or `tasks/deps.edn`) to
  `:git/tag "v1.0.0", :git/sha "529660f"`

#### Optional: upgrade config to use Aero

Apply the changes in [this
commit](https://github.com/jacobobryant/biff/commit/40510a006f8c436effdef4f9956e02856525bfbf) to
your project:

1. Copy the new
   [`resources/config.edn`](https://github.com/jacobobryant/biff/commit/40510a006f8c436effdef4f9956e02856525bfbf#diff-4839ac6399b8842945209a68dbc2cd952f84694a4e82ca600461cd5a1c19e434)
   file into your project. (NOTE: this new file should be saved at `resources/config.edn`, not
   `config.edn`).
2. Copy the new
   [`resources/config.template.env`](https://github.com/jacobobryant/biff/commit/40510a006f8c436effdef4f9956e02856525bfbf#diff-728b542a42e75fd36ef44f06223412bca330adc63288fade9ebee757f8aae345)
   file into your project. Copy it to both `resources/config.template.env` and `config.env`.
3. Add `/config.env` to your `.gitignore` file.
4. Edit `resources/config.edn` and `config.env` to include any custom config that you previously put
   in `config.edn` or `secrets.env`. For example, you'll need to copy the values of `COOKIE_SECRET`
   and `JWT_SECRET` from `secrets.env` into `config.env`. `resources/config.edn` will be parsed with
   [Aero](https://github.com/juxt/aero). To specify different config values for different
   environments, you'll do e.g. `{:example/foo #profile {:prod "bar" :dev "baz"} ...` instead of
   `{:prod {:example/foo "bar" ...} :dev {:example/foo "baz" ...} ...`. Here's some
   [more migration advice](https://github.com/jacobobryant/biff/discussions/184).
5. Test your config. Add the [`check-config`
   function](https://github.com/jacobobryant/biff/commit/40510a006f8c436effdef4f9956e02856525bfbf#diff-43bcb43dea16d7f18c742b44d2ec057d3c97bd4a3717c89558aa714d7b9bc4b1)
   to your `repl.clj` file. Then run `bb dev` and evaluate `(check-config)` from your editor. (You
   can also do `(biff/pprint (check-config))` if needed.) Make sure everything looks correct.
6. Run `git add .; git commit`.
7. If your app is already deployed, run `bb deploy` and then `bb prod-repl`. Evaluate `(check-config)` again to make
   sure the new config is available in production.
8. [Edit your main
   namespace](https://github.com/jacobobryant/biff/commit/40510a006f8c436effdef4f9956e02856525bfbf#diff-dc8d794a683c27486f2b534a9fd84dab78e04d2c68963cd22dc776598320aa82)
   and replace `biff/use-config` and `biff/use-secrets` with `biff/use-aero-config`. Run `bb dev` and make sure
   everything works locally. Then make a new commit, run `bb deploy`, and ensure everything works in production.

NOTE: this will make your application use the new Aero config, however, bb tasks will still read
from the old `config.edn` file. To completely upgrade, you'll need to switch to clj tasks.

#### Optional: switch from bb tasks to clj tasks

If you switch to clj tasks, you must first upgrade your config to use Aero as described above.

Apply the changes in [this
commit](https://github.com/jacobobryant/biff/commit/2f341c0a517944978ac52c77a4e8d9d490680db3)
to your project:

1. Add the [`:dev` and `:prod`
   aliases](https://github.com/jacobobryant/biff/commit/2f341c0a517944978ac52c77a4e8d9d490680db3#diff-6e20ca141152dfb6f7f46348d9cfa96099e11c646de6c53afb382bb5d2df53e6)
   to your `deps.edn` file. Replace `:local/root "../libs/tasks"` with `:git/url
   "https://github.com/jacobobryant/biff", :git/tag "v1.0.0", :git/sha "529660f", :deps/root
   "libs/tasks"` and change `com.example` to your main namespace.
2. Add the [`dev/tasks.clj`
   file](https://github.com/jacobobryant/biff/commit/2f341c0a517944978ac52c77a4e8d9d490680db3#diff-7938fae2e6818a0970d52c71ac7b16b4dd0b47b337238dd4d3dfbf63769c5efe)
   to your project.
3. Edit your `-main` and `start` functions [like
   so](https://github.com/jacobobryant/biff/commit/2f341c0a517944978ac52c77a4e8d9d490680db3#diff-dc8d794a683c27486f2b534a9fd84dab78e04d2c68963cd22dc776598320aa82).
4. If you set up a server with `server-setup.sh`, SSH into it as root, edit `/etc/systemd/system/app.service`, and
   change the `ExecStart` line to `ExecStart=/bin/sh -c "mkdir -p target/resources; clj -M:prod"`. Then run `systemctl
   daemon-reload`. Also make the same change in your `server-setup.sh` file. (Note: new projects also have
   `server-setup.sh` set `BIFF_PROFILE=prod` instead of `BIFF_ENV=dev`, but `BIFF_ENV` is still recognized.)
5. Run `clj -M:dev dev` to make sure everything works locally, then (if you did step 4) run
   `clj -M:dev deploy` and make sure it works in prod.
6. Recommended: add `alias biff='clj -M:dev'` to your `.bashrc` file.

Run `clj -M:dev --help` (or `biff --help` if you set up the alias) for a list of available commands.
e.g. instead of running `bb dev` and `bb deploy`, you'll now run `clj -M:dev dev` and `clj -M:dev
deploy`.

#### Optional: make your app work with Docker/Uberjars

After you upgrade to clj tasks, you can run `clj -M:dev uberjar` to generate an Uberjar for your app.
You can also add [this
`Dockerfile`](https://github.com/jacobobryant/biff/commit/731c9716bb34352a240334a967825ed35bcb5af1#diff-864db13d8bc4f4de07dbc9d7d376481b8ab5bda07c176d78738836cb5cf86ab0)
to your app. The recommended way for most people to deploy Biff is still the old way (setting up a
DigitalOcean droplet with `server-setup.sh` and deploying with `clj -M:dev deploy`). But for those
who know they want to deploy another way, `clj -M:dev uberjar` and the `Dockerfile` are provided as a
convenience.

You'll need to make a few additional changes to your project. See [this
commit](https://github.com/jacobobryant/biff/commit/731c9716bb34352a240334a967825ed35bcb5af1):

- [Add `(:gen-class)`](https://github.com/jacobobryant/biff/commit/731c9716bb34352a240334a967825ed35bcb5af1#diff-dc8d794a683c27486f2b534a9fd84dab78e04d2c68963cd22dc776598320aa82) to your main namespace.
- [Update `css-path` and `js-path`](https://github.com/jacobobryant/biff/commit/731c9716bb34352a240334a967825ed35bcb5af1#diff-5209835b4e108639e20e21f93531eb22ed904154da72098d62db3cf1a29c49d6) so they don't break when called inside an Uberjar.
- Add [`.dockerignore`](https://github.com/jacobobryant/biff/commit/731c9716bb34352a240334a967825ed35bcb5af1#diff-f5654fbea76a10b28b2baa9d3e1aecaed62e1946f8d97a1d9bab2c68400a7ccb) to your project.

#### Optional: clean up

If you followed the instructions above, there will be several files and directories that you no
longer need. Feel free to delete them:

- `bb.edn`
- `bb/` (previously this folder was named `tasks/`)
- `config.edn` and `secrets.env` (these aren't checked into git by default, so be sure you've
  migrated all your config over to the new files before you delete them)

Additionally, you can move your `repl.clj` file into the new `dev` directory and your `test.clj`
file into a new `test` directory. You'll also need to edit `deps.edn` and your `on-save`
function—see [this
commit](https://github.com/jacobobryant/biff/commit/700fa177527c9da50f90e7e86c0a9048337139c3).

#### Optional: rename "plugins" to "modules"

This is a purely cosmetic change, but if you'd like to stay in line with new projects,
the change is [a simple
find-and-replace](https://github.com/jacobobryant/biff/commit/1bb5365f6bff51a7e6da37b133702cb86f5016bf).

## v0.7.15 (2023-09-20)

### Upgrade instructions

 - Update your Biff dependency in `deps.edn` to  `{:tag "v0.7.15", :sha "b730c85", ...}`
 - Update your Biff dependency in `bb/deps.edn` to  `{:tag "v0.7.15", :sha "b730c85", :deps/root "tasks", ...}`

### Changes

- Refactored `bb deploy` and other tasks so that it attempts to start an ssh-agent session if one
  isn't already running. This way, your SSH key decryption password will only be requested once per
  command. If Biff is unable to start an ssh-agent session, `bb deploy` will only ask for your
  password twice (in most cases). These changes also fix a bug in v0.7.11 where `bb deploy` was
  completely broken if you didn't already have an ssh-agent session running.

- Add a `:biff.tasks/skip-ssh-agent` config option which, if set to true, will disable the
  `ssh-agent` functionality described above.

- Fix regressions in multiple commands, including `bb dev` and `bb deploy`, so that they work again
  on Windows (without WSL).

- Fix a bug in `bb css` (and several other commands by extension, like `bb deploy`) where it crashed
  if you didn't have `npm` on your path.

- Check if the downloaded `tailwindcss` binary is corrupted/incomplete, and prompt the user to
  delete it if so. #166 

- Fix a bug in the authentication plugin where the 6-digit code flow didn't correctly update new
  users' sessions. #167 (Thanks @Invertisment)

## 0.7.11 (2023-09-09)

### Update 2023-09-16:

There were a few regressions in this release. I've updated the upgrade instructions below to have
you upgrade to a commit with the fixes. I'll write up separate release notes for that soon.

### Upgrade instructions

 - Update your Biff dependency in `deps.edn` to ~`{:tag "v0.7.11", :sha "6428c7f", ...}`~ `{:tag "v0.7.14", :sha "aad7173", ...}`
 - Update your Biff dependency in `bb/deps.edn` to ~`{:tag "v0.7.11", :sha "6428c7f", :deps/root "tasks", ...}`~ `{:tag "v0.7.14", :sha "aad7173", :deps/root "tasks", ...}`

#### Optional

- Move the default middleware stack into your project so that it's easier to modify and debug:
  https://github.com/jacobobryant/biff/commit/d8c83c4cc25123b67e14751ff5d19e6b24f7317c

- Move the default `on-error` handler into your project so that it's easier to modify:
  https://github.com/jacobobryant/biff/commit/7622c402d2590bc31c97c2025eafa19b6de4c38c

- Add a `main.js` file to your project for cases where you need more client-side logic than what
  hyperscript can comfortably handle:
  https://github.com/jacobobryant/biff/commit/2e943bb891f9431e8420176171a2c5703ca8cda8

### Added

- The
  [com.biffweb/s3-request](https://github.com/jacobobryant/biff/commit/d9b3be26b10c626843a08aa56a71eed5c671ec0d#diff-a969895380b554328c2417a465c807d46a731f431e76f6e638c8f4eb8cb656eeR233)
  function is used for uploading/downloading files to/from S3-compatible services. Adapted from the
  [image upload howto](https://gist.github.com/jacobobryant/02de6c2b3a1dae7c86737a2610311a3a).

### Changes

- `bb deploy` now uses `rsync` (if available) instead of `git push`. Closes #164, #155

- Improved logic in `bb css` / `bb dev` for selecting the right Tailwind command and installing the
  standalone binary.

- `com.biffweb/eval-files!` now returns the result from
  `clojure.tools.namespace.reload/track-reload` instead of `nil`, which will be helpful for #117.

- Template project changes (discussed above in Upgrade instructions > Optional).

### Fixed

- `bb soft-deploy` now works on Mac. Closes #152 

- The authentication plugin no longer blocks occasionally when generating sign-in codes. Closes #163 

- Various documentation updates.

- Fixed a websocket-related bug in the tutorial where messages weren't received if you open the same
  chat channel with the same user in two different tabs.

---

See Github for [previous releases](https://github.com/jacobobryant/biff/releases).
