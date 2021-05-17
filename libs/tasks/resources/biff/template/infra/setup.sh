# If you install more packages or make other changes to your server, add the
# changes to this file (at least as documentation).

set -x
set -e

CLJ_VERSION=1.10.3.822

echo waiting for apt to finish
while (ps aux | grep [a]pt); do
  sleep 3
done

# Dependencies
apt-get update
apt-get upgrade
apt-get -y install nginx default-jre rlwrap nodejs npm
snap install core
snap refresh core
snap install --classic certbot
ln -s /snap/bin/certbot /usr/bin/certbot
curl -O https://download.clojure.org/install/linux-install-$CLJ_VERSION.sh
chmod +x linux-install-$CLJ_VERSION.sh
./linux-install-$CLJ_VERSION.sh
rm linux-install-$CLJ_VERSION.sh

# Nginx
rm /etc/nginx/sites-enabled/default
cat > /etc/nginx/sites-available/app << EOD
server {
    listen 80 default_server;
    listen [::]:80 default_server;
    server_name _;
    gzip_types text/plain text/css application/json application/javascript text/xml application/xml application/xml+rss text/javascript;
    location / {
        proxy_pass http://localhost:8080;
        proxy_http_version 1.1;
        proxy_set_header Host \$host;
        proxy_set_header Upgrade \$http_upgrade;
        proxy_set_header Connection "Upgrade";
    }
}
EOD
ln -s /etc/nginx/sites-{available,enabled}/app

# Let's encrypt
certbot --nginx

# Firewall
ufw allow "Nginx Full"
ufw allow OpenSSH
ufw enable

# Non-root user
useradd -m app
mkdir -m 700 -p /home/app/.ssh
cp /root/.ssh/authorized_keys /home/app/.ssh
chown -R app:app /home/app/.ssh

# Set up app with git
set_up_app () {
  cd
  mkdir code
  mkdir repo.git
  cd repo.git
  git init --bare
  cat > hooks/post-receive << EOD
#!/usr/bin/env bash
git --work-tree=/home/app/code --git-dir=/home/app/repo.git checkout -f
cd /home/app/code
chmod +x infra/post-receive
infra/post-receive
EOD
  chmod +x hooks/post-receive
}
sudo -u app bash -c "$(declare -f set_up_app); set_up_app"

# Set up systemd service
cat > /etc/systemd/system/app.service << EOD
[Unit]
Description=app
StartLimitIntervalSec=500
StartLimitBurst=5

[Service]
User=app
Restart=on-failure
RestartSec=5s
ExecStart=/home/app/releases/current/run.sh

[Install]
WantedBy=multi-user.target
EOD
systemctl enable app
cat > /etc/systemd/journald.conf << EOD
[Journal]
Storage=persistent
EOD
systemctl restart systemd-journald
echo app ALL= NOPASSWD: /bin/systemctl restart app > /etc/sudoers.d/restart-app
chmod 440 /etc/sudoers.d/restart-app
