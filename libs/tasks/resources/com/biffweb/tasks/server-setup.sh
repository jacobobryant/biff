#!/usr/bin/env bash
set -euo pipefail

APP=${1:-}
DOMAIN=${2:-}

if [ -z "$APP" ] || [ -z "$DOMAIN" ]; then
  echo 'Usage: server-setup.sh <app-name> <domain>' >&2
  exit 1
fi

if [ "$(whoami)" != root ]; then
  echo 'This script must be run as root.' >&2
  exit 2
fi

export DEBIAN_FRONTEND=noninteractive

echo 'Running apt-get update. If this fails, wait a few seconds for any background apt process to finish.'
apt-get update
apt-get -y upgrade
apt-get -y install curl default-jre git gnupg rlwrap ufw

if ! command -v clj >/dev/null 2>&1; then
  curl -L -O https://github.com/clojure/brew-install/releases/latest/download/linux-install.sh
  chmod +x linux-install.sh
  ./linux-install.sh
fi

if ! command -v trench >/dev/null 2>&1; then
  TRENCH_VERSION=0.4.0
  if [ "$(uname -m)" = "aarch64" ]; then
    ARCH=arm64
  else
    ARCH=amd64
  fi
  TRENCH_FILE=trenchman_${TRENCH_VERSION}_linux_${ARCH}.tar.gz
  curl -L -O https://github.com/athos/trenchman/releases/download/v$TRENCH_VERSION/$TRENCH_FILE
  tar zxvfC "$TRENCH_FILE" /usr/local/bin trench
fi

if ! id -u "$APP" >/dev/null 2>&1; then
  useradd -m "$APP"
fi

mkdir -p "/home/$APP/.ssh" "/home/$APP/repo"
if compgen -G /root/.ssh/* >/dev/null 2>&1; then
  cp -rn /root/.ssh/* "/home/$APP/.ssh/"
fi
chown -R "$APP:$APP" "/home/$APP/.ssh" "/home/$APP/repo"
chmod 700 "/home/$APP/.ssh"

PORT=""
SERVICE_FILE="/etc/systemd/system/$APP.service"
if [ -f "$SERVICE_FILE" ]; then
  PORT=$(sed -n 's/.*Environment="PORT=\([0-9]*\)".*/\1/p' "$SERVICE_FILE" | head -n 1)
fi

if [ -z "$PORT" ]; then
  PORT=8080
  if [ -f /etc/caddy/Caddyfile ]; then
    while grep -q "$PORT" /etc/caddy/Caddyfile; do
      PORT=$((PORT + 1))
    done
  fi
fi

cat > "$SERVICE_FILE" <<EOD
[Unit]
Description=$APP
StartLimitIntervalSec=500
StartLimitBurst=5

[Service]
User=$APP
WorkingDirectory=/home/$APP/repo
Environment="PORT=$PORT"
ExecStart=/bin/sh -c "mkdir -p target/resources; clj -M:prod"
Restart=on-failure
RestartSec=5s

[Install]
WantedBy=multi-user.target
EOD

systemctl daemon-reload
systemctl enable "$APP"

cat > /etc/systemd/journald.conf <<EOD
[Journal]
Storage=persistent
EOD
systemctl restart systemd-journald

cat > "/etc/sudoers.d/restart-$APP" <<EOD
$APP ALL= NOPASSWD: /bin/systemctl reset-failed $APP.service
$APP ALL= NOPASSWD: /bin/systemctl restart $APP
$APP ALL= NOPASSWD: /usr/bin/systemctl reset-failed $APP.service
$APP ALL= NOPASSWD: /usr/bin/systemctl restart $APP
EOD
chmod 440 "/etc/sudoers.d/restart-$APP"

if ! command -v caddy >/dev/null 2>&1; then
  apt-get install -y debian-keyring debian-archive-keyring apt-transport-https
  curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' | gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
  curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' | tee /etc/apt/sources.list.d/caddy-stable.list >/dev/null
  chmod o+r /usr/share/keyrings/caddy-stable-archive-keyring.gpg
  chmod o+r /etc/apt/sources.list.d/caddy-stable.list
  apt-get update
  apt-get install -y caddy
fi

if [ ! -f /etc/caddy/Caddyfile ] || ! grep -q "^$DOMAIN" /etc/caddy/Caddyfile; then
  cat >> /etc/caddy/Caddyfile <<EOD
$DOMAIN {
    encode gzip
    reverse_proxy localhost:$PORT
}
EOD
fi

systemctl reload caddy

ufw allow OpenSSH
ufw allow http
ufw allow https
ufw --force enable
