---
title: Production
---

<p style="padding: 56.25% 0 0 0; position: relative;"><iframe style="position: absolute; top: 0; left: 0; width: 100%; height: 100%;" title="output" src="https://player.vimeo.com/video/918739523?badge=0&amp;autopause=0&amp;player_id=0&amp;app_id=58479" frameborder="0" allow="autoplay; fullscreen; picture-in-picture" allowfullscreen="allowfullscreen"></iframe></p>

Biff comes with a script (`server-setup.sh`) for setting up an Ubuntu server. It's
been tested on DigitalOcean. You can deploy Biff anywhere that can
run a JVM, but if you're happy with the defaults then you can simply
follow these steps (for example screenshots, see [the tutorial](https://biffweb.com/docs/tutorial/deploy/)):

1. Create an Ubuntu VPS in e.g. DigitalOcean. Give it at least 1GB of memory. Here's a
   [DigitalOcean referral link](https://m.do.co/c/141610534c91) that'll give you $200 of
   credit.
2. (Optional) If this is an important application, you may want to set up a
   managed Postgres instance and edit `config.env` to use that for XTDB's
   storage backend instead of the filesystem. With the default standalone
   topology, you'll need to handle backups yourself, and you can't use more
   than one server.
3. Edit `config.env` and set `DOMAIN` to the domain you'd like to use for your
   app. For now we'll assume you're using `example.com`. (If you haven't run `clj -M:dev dev` yet,
   `config.env` won't exist. You can create it with `clj -M:dev generate-config`.)
4. Set an A record on `example.com` that points to your Ubuntu server.
5. Make sure you can ssh into the server, then run `scp server-setup.sh root@example.com:`.
6. Run `ssh root@example.com`, then `bash server-setup.sh`. After it finishes, run `reboot`.
7. (Optional) On your local machine, run `git remote add prod ssh://app@example.com/home/app/repo.git`.
   This is required if you don't have `rsync` installed. If your default git
   branch is `main` instead of `master`, you'll also need to edit
   `:biff.tasks/deploy-cmd` in `resources/config.edn`.

Now you can deploy your application any time by committing your code and then
running `clj -M:dev deploy`. Run `clj -M:dev logs` to make sure the deploy was successful.

Your code and config will be uploaded with `rsync` if it's available. However,
`git ls-files` will still be used to decide which files to upload, so files
won't be uploaded unless they're checked into git (with the exception of
`config.env` and `target/resources/public/css/main.css`&mdash;see
`:biff.tasks/deploy-untracked-files` in `resources/config.edn`). If `rsync`
isn't installed, files will be uploaded with `git push`.

If you need to make changes to the server (e.g. perhaps you need to install an
additional package), be sure to update `server-setup.sh` so you can always
easily provision a new server from scratch.

### Monitoring and alerting

Besides using `clj -M:dev logs`, [Papertrail](https://www.papertrail.com/) is cheap,
easy to set up, and useful for alerts. For example, it can send you an email
whenever your application logs include the text `Exception`.

DigitalOcean provides [uptime checks](https://www.digitalocean.com/products/uptime-monitoring)
which are useful if e.g. your application fails to start.

### Developing in prod

After you've deployed the first time, you can continue developing the production
system while it's running via the `clj -M:dev prod-dev` task. Whenever you save
a file, it'll get copied to the server and evaluated.

### Workers

If you need a dedicated worker(s), you can create a modified version of
`server-setup.sh` which sets the `BIFF_PROFILE` environment variable to `worker`.
Then use `#profile {:worker ...` in `resources/config.edn` to specify
worker-specific configuration, and modify your application code to run as a web
server or worker depending on the runtime configuration.

### Alternative deployment options

You can use the provided `Dockerfile` to build a container for your app or use
`clj -M:dev uberjar` to build an Uberjar. Deploying the container/Uberjar is up
to you. However you deploy, make sure that the contents of `config.env` are
included in the environment, or make sure that `config.env` is in the working
directory at runtime.
