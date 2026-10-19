# CrowdRewards server bootstrap

After connecting to EC2 via SSM Session Manager:

```bash
sudo dnf update -y
sudo dnf install -y git
git clone https://github.com/jeev567/CrowdRewards.git /tmp/CrowdRewards
sudo bash /tmp/CrowdRewards/infra/scripts/bootstrap.sh
```

The bootstrap installs Amazon Corretto 21, Nginx, Git, Node.js and npm, and creates a restricted service account and SQLite data directory. It does not deploy the application, set secrets, or enable public HTTPS.

Next: build the application, provide secure backend environment variables, install the systemd unit and Nginx configuration, and verify the PayPal return URL before opening checkout. The Nginx sample is HTTP-only and must not be exposed to the public with unauthenticated merchant/admin endpoints.
