#!/usr/bin/env bash
set -x
set -e

BIFF_ENV=${1:-prod}
CLJ_VERSION=1.10.3.822
TRENCH_VERSION=0.3.0
TAILWIND_VERSION=latest
TAILWIND_ARCH=linux-x64

echo waiting for apt to finish
while (ps aux | grep [a]pt); do
  sleep 3
done

# Dependencies
apt-get update
apt-get upgrade
apt-get -y install default-jre rlwrap
curl -O https://download.clojure.org/install/linux-install-$CLJ_VERSION.sh
chmod +x linux-install-$CLJ_VERSION.sh
./linux-install-$CLJ_VERSION.sh
rm linux-install-$CLJ_VERSION.sh
wget https://github.com/athos/trenchman/releases/download/v$TRENCH_VERSION/trenchman_${TRENCH_VERSION}_linux_amd64.tar.gz
tar -xf trenchman_${TRENCH_VERSION}_linux_amd64.tar.gz
mv trench /usr/local/bin/

# Non-root user
useradd -m app
mkdir -m 700 -p /home/app/.ssh
cp /root/.ssh/authorized_keys /home/app/.ssh
chown -R app:app /home/app/.ssh

# Git deploys
set_up_app () {
  cd
  mkdir repo.git
  cd repo.git
  git init --bare
  cat > hooks/post-receive << EOD
#!/usr/bin/env bash
git --work-tree=/home/app --git-dir=/home/app/repo.git checkout -f
/home/app/task post-receive
EOD
  chmod +x hooks/post-receive
}
sudo -u app bash -c "$(declare -f set_up_app); set_up_app"

# Systemd service
cat > /etc/systemd/system/app.service << EOD
[Unit]
Description=app
StartLimitIntervalSec=500
StartLimitBurst=5

[Service]
User=app
Restart=on-failure
RestartSec=5s
Environment="BIFF_ENV=$BIFF_ENV"
ExecStart=/home/app/task run

[Install]
WantedBy=multi-user.target
EOD
systemctl enable app
cat > /etc/systemd/journald.conf << EOD
[Journal]
Storage=persistent
EOD
systemctl restart systemd-journald
cat > /etc/sudoers.d/restart-app << EOD
app ALL= NOPASSWD: /bin/systemctl reset-failed app.service
app ALL= NOPASSWD: /bin/systemctl restart app
EOD
chmod 440 /etc/sudoers.d/restart-app

# Firewall
ufw allow OpenSSH
ufw enable

# Web dependencies
apt-get -y install nginx
snap install core
snap refresh core
snap install --classic certbot
ln -s /snap/bin/certbot /usr/bin/certbot

# Tailwind
curl -LO https://github.com/tailwindlabs/tailwindcss/releases/$TAILWIND_VERSION/download/tailwindcss-$TAILWIND_ARCH
chmod +x tailwindcss-$TAILWIND_ARCH
mkdir -p /home/app/bin
mv tailwindcss-$TAILWIND_ARCH /home/app/bin/tailwindcss
chown -R app:app /home/app/bin

# Nginx
rm /etc/nginx/sites-enabled/default
cat > /etc/nginx/sites-available/app << EOD
server {
    listen 80 default_server;
    listen [::]:80 default_server;
    server_name _;
    gzip_types text/plain text/css application/json application/javascript text/xml application/xml application/xml+rss text/javascript;
    root /home/app/target/resources/public;
    location / {
        try_files \$uri \$uri/index.html @resources;
    }
    location @resources {
        root /home/app/resources/public;
        try_files \$uri \$uri/index.html @proxy;
    }
    location @proxy {
        proxy_pass http://localhost:8080;
        proxy_http_version 1.1;
        proxy_set_header Host \$host;
        proxy_set_header Upgrade \$http_upgrade;
        proxy_set_header Connection "Upgrade";
        proxy_set_header X-Real-IP \$remote_addr;
    }
}
EOD
ln -s /etc/nginx/sites-{available,enabled}/app

# Firewall
ufw allow "Nginx Full"

# Let's encrypt
certbot --nginx

# App dependencies
# If you need to install additional packages for your app, you can do it here.
# apt-get -y install ...
