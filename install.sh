#!/bin/bash
set -e

# todo set up non-root user

apt update
apt upgrade
add-apt-repository ppa:certbot/certbot
apt install nginx python-certbot-nginx default-jre
curl -O https://download.clojure.org/install/linux-install-1.10.1.536.sh
chmod +x linux-install-1.10.1.536.sh
./linux-install-1.10.1.536.sh

(cd /root/biff/prod; clojure -Sresolve-tags)

cat > /etc/systemd/system/biff.service << EOD
[Unit]
Description=Biff

[Service]
ExecStart=/root/biff/prod/task run

[Install]
WantedBy=multi-user.target
EOD
systemctl enable biff

rm /etc/nginx/sites-enabled/default
cat > /etc/nginx/sites-available/biff << EOD
server {
    listen 80 default_server;
    listen [::]:80 default_server;
    server_name _;
    root /var/www/\$host;
    location / {
        try_files \$uri \$uri/ @proxy;
    }
    location @proxy {
        proxy_pass http://localhost:8080;
        proxy_http_version 1.1;
        proxy_set_header Host \$host;
        proxy_set_header Upgrade \$http_upgrade;
        proxy_set_header Connection "Upgrade";
    }
}
EOD
ln -s /etc/nginx/sites-{available,enabled}/biff
systemctl restart nginx
ln -s /var/www /root/biff/prod/www

echo
echo Running certbot now. When it asks if you\'d like to redirect HTTP requests to
echo HTTPS, say yes.
read -p "Press Enter to continue"
certbot --nginx
systemctl restart nginx

ufw allow "Nginx Full"
ufw allow OpenSSH
ufw enable

echo
echo
echo Installation complete. Edit /root/biff/prod/deps.edn and add your project.
echo Then run \`reboot\` and then wait for a minute.
echo
echo Watch logs: \`journalctl -u biff -f\`
echo Update: edit ~/biff/prod/deps.edn
echo Restart: \`systemctl restart biff\`
