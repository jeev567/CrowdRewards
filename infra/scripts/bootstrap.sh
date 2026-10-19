#!/usr/bin/env bash
set -euo pipefail

if [[ "$(id -u)" -ne 0 ]]; then
  echo "Run with sudo: sudo bash infra/scripts/bootstrap.sh" >&2
  exit 1
fi

dnf update -y
dnf install -y java-21-amazon-corretto-devel nginx git nodejs npm
systemctl enable --now nginx

# Application user owns application files, not Nginx or system paths.
if ! id crowdrewards >/dev/null 2>&1; then
  useradd --system --create-home --home-dir /opt/crowdrewards --shell /sbin/nologin crowdrewards
fi
install -d -m 0750 -o crowdrewards -g crowdrewards /opt/crowdrewards
install -d -m 0750 -o crowdrewards -g crowdrewards /var/lib/crowdrewards
install -d -m 0750 -o crowdrewards -g crowdrewards /etc/crowdrewards

echo "Bootstrap complete. Check Java, Node, npm and Nginx versions."
java -version
node --version
npm --version
nginx -v
