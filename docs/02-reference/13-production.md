---
title: Production
---

Biff comes with a script (`server-setup.sh`) for setting up an Ubuntu server. It's
been tested on DigitalOcean. You can of course deploy Biff anywhere that can
run a JVM&mdash;but if you're happy with the defaults then you can simply
follow these steps (for example screenshots, see [the tutorial](https://biffweb.com/docs/tutorial/deploy/)):

1. Create an Ubuntu VPS in e.g. DigitalOcean. Give it at least 1GB of memory.
2. (Optional) If this is an important application, you may want to set up a
   managed Postgres instance and edit `config.edn` to use that for XTDB's
   storage backend instead of the filesystem. With the default standalone
   topology, you'll need to handle backups yourself, and you can't use more
   than one server.
3. Edit `config.edn` and set `:biff.tasks/server` to the domain you'd like to
   use for your app. For now we'll assume you're using `example.com`. Also
   update `:biff/base-url`. If you use `main` instead of `master` as your
   default branch, update `:biff.tasks/deploy-cmd`.
4. Set an A record on `example.com` that points to your Ubuntu server.
5. Make sure you can ssh into the server, then run `scp server-setup.sh root@example.com:`.
6. Run `ssh root@example.com`, then `bash server-setup.sh`. After it finishes, run `reboot`.
7. On your local machine, run `git remote add prod ssh://app@example.com/home/app/repo.git`.

Now you can deploy your application any time by committing your code and then
running `bb deploy`. This will copy your config files (which aren't checked
into Git) to the server, then it'll deploy the latest commit via git push. You can run
`bb logs` to make sure the deploy was successful.

If you need to make changes to the server (e.g. perhaps you need to install an
additional package), be sure to update `server-setup.sh` so you can always
easily provision a new server from scratch.

### Monitoring and alerting

Besides using `bb logs`, [Papertrail](https://www.papertrail.com/) is cheap,
easy to set up, and useful for alerts. For example, it can send you an email
whenever your application logs include the text `Exception`.

DigitalOcean provides [uptime checks](https://www.digitalocean.com/products/uptime-monitoring)
which are useful if e.g. your application fails to start.

### Developing in prod

After you've deployed the first time, you can continue developing the production
system while it's running. You'll need to install
[fswatch](https://emcrisostomo.github.io/fswatch/getting.html).
(`sudo apt install fswatch` on Ubuntu, `brew install fswatch` on Mac.) Then run
`bb prod-dev`. Whenever you save a file, it'll get copied to the server and
evaluated.

### Workers

If you need a dedicated worker(s), you can create a modified version of
`server-setup.sh` which sets the `BIFF_ENV` environment variable to `worker`.
Then add a `:worker` configuration section to `config.edn` and modify your
application code to run as a web server or worker depending on the runtime
configuration.

### Container-based deployment

Instructions and code for deploying with Docker are on the roadmap. In the mean time, a first
take on a Dockerfile is [available here](https://gist.github.com/jacobobryant/d3b2654c36161414a7b6a306b3f678b5).
