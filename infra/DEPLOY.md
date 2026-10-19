# Deploy CrowdRewards from your Mac

This mirrors DealSeal: local Gradle and Vite build, private S3 temporary artifacts, AWS SSM deployment, health check and backend rollback. It does not alter SQLite at /var/lib/crowdrewards or credentials at /etc/crowdrewards/backend.env.

## One-time local setup
- AWS CLI profile `dealseal-admin` with S3 upload/presign and SSM SendCommand/GetCommandInvocation permissions.
- Java 21, Gradle installed on your Mac (or add a Gradle wrapper to backend), Node/npm, Python 3.
- A private S3 bucket (the DealSeal deployment bucket can be reused if your IAM policy permits a `crowdrewards/` prefix).

```bash
export CROWDREWARDS_DEPLOY_BUCKET="YOUR_PRIVATE_DEPLOY_BUCKET"
chmod +x infra/deploy.sh
./infra/deploy.sh
```

The deploy script builds local working-tree changes; commit/push separately if you want GitHub to match the deployment. Do not put AWS credentials or PayPal secrets in the repo.

## One-time EC2 DuckDNS setup
Use AWS Session Manager to access the CrowdRewards EC2 instance. On the instance:

```bash
sudo install -d -m 700 /etc/crowdrewards
sudo sh -c 'umask 077; printf "DUCKDNS_TOKEN=YOUR_DUCKDNS_TOKEN\\n" > /etc/crowdrewards/duckdns.env'
```

Replace the placeholder privately on EC2; do not post or commit the token.

From a checkout of this repo **on EC2**, install the updater and timer:

```bash
sudo install -m 0755 infra/scripts/update-duckdns.sh /usr/local/bin/crowdrewards-update-duckdns
sudo install -m 0644 infra/systemd/duckdns-crowdrewards.service /etc/systemd/system/
sudo install -m 0644 infra/systemd/duckdns-crowdrewards.timer /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now duckdns-crowdrewards.timer
sudo systemctl start duckdns-crowdrewards.service
sudo journalctl -u duckdns-crowdrewards.service -n 10 --no-pager
```

DuckDNS refresh runs every five minutes and after successful deployments. The domain remains the same when EC2's public IP changes; a stop/start can briefly interrupt service while DNS updates propagate. For a stable IP, use an Elastic IP instead.

## Safety
- The existing HTTPS AWS allowlist and Nginx allowlist remain in effect; changing DuckDNS does **not** update your Mac's trusted CIDR.
- The frontend is copied to /var/www/crowdrewards after the backend health check.
- The script does not provision the S3 bucket or automatically install the DuckDNS timer.
- Verify with `curl -i https://crowdrewards-demo.duckdns.org/api/campaigns`.
