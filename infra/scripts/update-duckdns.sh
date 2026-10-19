#!/usr/bin/env bash
set -euo pipefail
# Root-only file: DUCKDNS_TOKEN=... (never commit this value)
source /etc/crowdrewards/duckdns.env
: "${DUCKDNS_TOKEN:?DUCKDNS_TOKEN is required}"
TOKEN="$(curl -fsS --connect-timeout 3 --max-time 5 -X PUT http://169.254.169.254/latest/api/token -H 'X-aws-ec2-metadata-token-ttl-seconds: 60')"
IP="$(curl -fsS --connect-timeout 3 --max-time 5 -H "X-aws-ec2-metadata-token: $TOKEN" http://169.254.169.254/latest/meta-data/public-ipv4)"
[[ "$IP" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "Invalid EC2 public IP" >&2; exit 1; }
RESULT="$(curl -fsS --get --connect-timeout 5 --max-time 15 'https://www.duckdns.org/update' --data-urlencode 'domains=crowdrewards-demo' --data-urlencode "token=$DUCKDNS_TOKEN" --data-urlencode "ip=$IP")"
[[ "$RESULT" == "OK" ]] || { echo "DuckDNS rejected update" >&2; exit 1; }
echo "DuckDNS points to EC2 public IP $IP"
