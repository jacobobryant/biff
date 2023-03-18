---
title: Deploy to production
---

We'll follow the steps in [Reference > Production](https://biffweb.com/docs/reference/production/).

> 1․ Create an Ubuntu VPS in e.g. DigitalOcean. Give it at least 1GB of memory.

Create a [Digital Ocean](https://digitalocean.com) account if you don't have one already. Go to the
[create droplet](https://cloud.digitalocean.com/droplets/new) page. Change the default droplet to something cheaper:

![Screenshot of creating a droplet on Digital Ocean](/img/tutorial/do-price.png)

Also be sure to select an SSH key:

![Screenshot of creating a droplet on Digital Ocean](/img/tutorial/do-ssh.png)

You may need to add a new SSH key to your Digital Ocean account if you haven't
done so already.

Now you can create the droplet.

> 2․ (Optional) If this is an important application, you may want to set up a
> managed Postgres instance and edit config.edn to use that for XTDB's storage
> backend instead of the filesystem. With the default standalone topology,
> you'll need to handle backups yourself, and you can't use more than one
> server.

We'll skip this. If you're deploying a Real Application with Real Users, you should
*at least* enable Digital Ocean's weekly filesystem backups when you create the droplet, and
preferably use their managed Postgres offering.

> 3․ Edit `config.edn` and set `:biff.tasks/server` to the domain you'd like to use
> for your app. For now we'll assume you're using `example.com`. Also update
> `:biff/base-url`. If you use main instead of master as your default branch,
> update `:biff.tasks/deploy-cmd`.

I'll use `eelchat.biffweb.com` for my domain. Replace that with whatever domain
you're using:

```clojure
;; config.edn
{:prod {...
        :biff/base-url "https://eelchat.biffweb.com"
        ...}
 :dev {...}
 :tasks {...
         :biff.tasks/server "eelchat.biffweb.com"}}
```

> 4․ Set an A record on example.com that points to your Ubuntu server.

As a prerequisite to this step, you'll need to register a domain somewhere. I use
[Namecheap](https://namecheap.com). After you point the domain at Digital Ocean's name servers,
you can [add the domain](https://cloud.digitalocean.com/networking/domains) to your DigitalOcean account.
Then, create an A record for the domain (or a subdomain) and point it at the droplet you created earlier.
I'm using the `eelchat.biffweb.com` subdomain:

![Screenshot of creating a DNS record on Digital Ocean](/img/tutorial/do-dns.png)

> 5․ Make sure you can ssh into the server, then run `scp server-setup.sh root@example.com:`.

Go ahead and run the command:

```plaintext
$ %%scp server-setup.sh root@eelchat.biffweb.com:%%
The authenticity of host 'eelchat.biffweb.com (164.92.125.199)' can't be established.
ECDSA key fingerprint is SHA256:BKnRyRjJlwsQTWi9ktWVz2gQz7sLBa2vuB4dNghFlGI.
Are you sure you want to continue connecting (yes/no/[fingerprint])? %%yes%%
Warning: Permanently added 'eelchat.biffweb.com,164.92.125.199' (ECDSA) to the list of known hosts.
server-setup.sh                                    100% 3278    50.3KB/s   00:00
```

> 6․ Run `ssh root@example.com`, then `bash server-setup.sh`. After it finishes, run `reboot`.

```plaintext
$ %%ssh root@eelchat.biffweb.com%%
Welcome to Ubuntu 22.10 (GNU/Linux 5.19.0-23-generic x86_64)
[...]

root@biff-tutorial:~# %%bash server-setup.sh%%
+ set -e
+ BIFF_ENV=prod
+ CLJ_VERSION=1.11.1.1165
[...]
```

While `server-setup.sh` runs, you'll be asked a few questions. For the first few questions,
you can go with the defaults (i.e. just press `Enter`). When you get to part where
`server-setup.sh` tries to provision an SSL certificate for your domain (via the `certbot` command),
you'll need to enter your domain and answer the other questions appropriately:

```plaintext
+ certbot --nginx
Saving debug log to /var/log/letsencrypt/letsencrypt.log
Enter email address (used for urgent renewal and security notices)
 (Enter 'c' to cancel): %%youremail@example.com%%

- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
Please read the Terms of Service at
https://letsencrypt.org/documents/LE-SA-v1.3-September-21-2022.pdf. You must
agree in order to register with the ACME server. Do you agree?
- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
(Y)es/(N)o: %%yes%%

- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
Would you be willing, once your first certificate is successfully issued, to
share your email address with the Electronic Frontier Foundation, a founding
partner of the Let's Encrypt project and the non-profit organization that
develops Certbot? We'd like to send you email about our work encrypting the web,
EFF news, campaigns, and ways to support digital freedom.
- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
(Y)es/(N)o: %%no%%
Account registered.
Please enter the domain name(s) you would like on your certificate (comma and/or
space separated) (Enter 'c' to cancel): %%eelchat.biffweb.com%%
Requesting a certificate for eelchat.biffweb.com

Successfully received certificate.
[...]
```

The entire process should take under 5 minutes. When it's done, run `reboot`.

> 7․ On your local machine, run `git remote add prod ssh://app@example.com/home/app/repo.git`.

This is the last step, so after we add the remote, we can go ahead and deploy our app:

```plaintext
$ %%git remote add prod ssh://app@eelchat.biffweb.com/home/app/repo.git%%

$ %%git add .%%

$ %%git commit -m "Add the landing page"%%

$ %%bb deploy%%
[...]
```

After the command finishes, run `bb logs` and look for the `System started`
message. This part may take a minute or so. Once you see it, you can load the
website in your web browser!

### Sending email

At this stage, when you sign in to eelchat, it will still print the sign-in
link to the console instead of emailing it to you. You can get the sign-in link
for your production app by running `bb logs`.

If you'd like to actually send the link via email (which you'll need to do at
some point if you plan on having users), create a
[Postmark](https://postmarkapp.com/) account. Once you have a Postmark API key
and sending identity, add them to your `config.edn` and `secrets.env` files. 

```clojure
;; config.edn
{:prod {...
        :postmark/from "hello@example.com"
        ...
```

```bash
# secrets.env
...
export POSTMARK_API_KEY=...
```

Then register your site with [reCAPTCHA](https://www.google.com/recaptcha/admin/). Select 
v2 Invisible for the reCAPTCHA type. Add your domain and `localhost` to the
allowed domains. Then add your credentials to `config.edn` and `secrets.env`:

```clojure
;; config.edn
{:prod {...
        :recaptcha/site-key "..."
        ...
```

```bash
# secrets.env
...
export RECAPTCHA_SECRET_KEY=...
```

Then run `bb soft-deploy; bb restart` to make the config change take effect.
