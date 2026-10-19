#!/usr/bin/env bash
# Local build -> private S3 -> SSM deployment. No SSH or EC2 build needed.
set -euo pipefail
PROFILE="${AWS_PROFILE:-dealseal-admin}"
REGION="${AWS_REGION:-us-west-2}"
INSTANCE_ID="${CROWDREWARDS_INSTANCE_ID:-i-0e935fba545b0c00a}"
BUCKET="${CROWDREWARDS_DEPLOY_BUCKET:-}"
[[ -n "$BUCKET" ]] || { echo "Set CROWDREWARDS_DEPLOY_BUCKET to a private S3 bucket." >&2; exit 1; }
AWS=(aws --profile "$PROFILE" --region "$REGION")
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
for cmd in aws python3 npm tar curl; do command -v "$cmd" >/dev/null || { echo "Missing: $cmd" >&2; exit 1; }; done
if [[ -x backend/gradlew ]]; then GRADLE=(./gradlew); elif command -v gradle >/dev/null; then GRADLE=(gradle); else
  echo "Gradle not found. Install Gradle or add a Gradle wrapper in backend/." >&2; exit 1
fi
echo "Building backend..."
(cd backend && "${GRADLE[@]}" clean bootJar --no-daemon)
echo "Building frontend..."
if [[ -f frontend/package-lock.json ]]; then (cd frontend && npm ci && npm run build); else (cd frontend && npm install && npm run build); fi
JAR="$(find backend/build/libs -maxdepth 1 -name '*.jar' ! -name '*plain.jar' | head -n 1)"
test -s "$JAR" && test -f frontend/dist/index.html || { echo "Missing build output" >&2; exit 1; }
TMP="$(mktemp -d)"
DEPLOY_ID="$(date -u +%Y%m%dT%H%M%SZ)-$$"
PREFIX="crowdrewards/deployments/$DEPLOY_ID"
cleanup() { "${AWS[@]}" s3 rm "s3://$BUCKET/$PREFIX/" --recursive >/dev/null 2>&1 || true; rm -rf "$TMP"; }
trap cleanup EXIT
cp "$JAR" "$TMP/backend.jar"
tar -C frontend/dist -czf "$TMP/frontend.tar.gz" .
"${AWS[@]}" s3 cp "$TMP/backend.jar" "s3://$BUCKET/$PREFIX/backend.jar" --only-show-errors
"${AWS[@]}" s3 cp "$TMP/frontend.tar.gz" "s3://$BUCKET/$PREFIX/frontend.tar.gz" --only-show-errors
BUCKET_REGION="$("${AWS[@]}" s3api get-bucket-location --bucket "$BUCKET" --query LocationConstraint --output text)"
[[ "$BUCKET_REGION" == "None" || "$BUCKET_REGION" == "null" ]] && BUCKET_REGION=us-east-1
[[ "$BUCKET_REGION" == "EU" ]] && BUCKET_REGION=eu-west-1
JAR_URL="$(aws --profile "$PROFILE" --region "$BUCKET_REGION" s3 presign "s3://$BUCKET/$PREFIX/backend.jar" --expires-in 3600)"
FRONTEND_URL="$(aws --profile "$PROFILE" --region "$BUCKET_REGION" s3 presign "s3://$BUCKET/$PREFIX/frontend.tar.gz" --expires-in 3600)"
python3 - "$JAR_URL" "$FRONTEND_URL" "$TMP/ssm.json" <<'PY'
import json, shlex, sys
jar, frontend, path = sys.argv[1:]
script = r"""
set -euo pipefail
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
curl -fsSL --retry 3 -o "$work/backend.jar" JAR_URL
curl -fsSL --retry 3 -o "$work/frontend.tar.gz" FRONTEND_URL
mkdir -p "$work/frontend"
tar -xzf "$work/frontend.tar.gz" -C "$work/frontend"
test -s "$work/backend.jar"
test -f "$work/frontend/index.html"
install -o crowdrewards -g crowdrewards -m 0644 "$work/backend.jar" /opt/crowdrewards/backend.jar.next
# Keep previous application and frontend for rollback.
cp -p /opt/crowdrewards/backend.jar /opt/crowdrewards/backend.jar.previous
mkdir -p /opt/crowdrewards
rm -rf /opt/crowdrewards/frontend.previous
if test -d /opt/crowdrewards/frontend; then
  mv /opt/crowdrewards/frontend /opt/crowdrewards/frontend.previous
else
  mkdir -p /opt/crowdrewards/frontend.previous
  cp -a /var/www/crowdrewards/. /opt/crowdrewards/frontend.previous/
fi
mv "$work/frontend" /opt/crowdrewards/frontend
mv /opt/crowdrewards/backend.jar.next /opt/crowdrewards/backend.jar
systemctl restart crowdrewards
sleep 12
if ! systemctl is-active --quiet crowdrewards || ! curl -fsS --max-time 8 http://127.0.0.1:8080/api/campaigns >/dev/null; then
  echo "Health check failed; rolling back" >&2
  cp -p /opt/crowdrewards/backend.jar.previous /opt/crowdrewards/backend.jar
  systemctl restart crowdrewards || true
  exit 1
fi
# Only replace public assets after backend health succeeds.
cp -a /opt/crowdrewards/frontend/. /var/www/crowdrewards/
if systemctl list-unit-files duckdns-crowdrewards.service --no-legend | grep -q '^duckdns-crowdrewards.service'; then
  systemctl start duckdns-crowdrewards.service || echo "WARNING: DuckDNS refresh failed" >&2
fi
echo "CrowdRewards deployment healthy"
"""
script = script.replace("JAR_URL", shlex.quote(jar)).replace("FRONTEND_URL", shlex.quote(frontend))
with open(path, "w") as f:
    json.dump({"commands":[script], "executionTimeout":["300"]}, f)
PY
echo "Deploying via SSM..."
COMMAND_ID="$("${AWS[@]}" ssm send-command --instance-ids "$INSTANCE_ID" --document-name AWS-RunShellScript --comment "CrowdRewards deployment $DEPLOY_ID" --parameters "file://$TMP/ssm.json" --timeout-seconds 600 --query 'Command.CommandId' --output text)"
echo "SSM command: $COMMAND_ID"
for attempt in $(seq 1 90); do
  STATUS="$("${AWS[@]}" ssm get-command-invocation --command-id "$COMMAND_ID" --instance-id "$INSTANCE_ID" --query Status --output text 2>/dev/null || true)"
  case "$STATUS" in
    Success)
      "${AWS[@]}" ssm get-command-invocation --command-id "$COMMAND_ID" --instance-id "$INSTANCE_ID" --query StandardOutputContent --output text
      echo "Deployed: https://crowdrewards-demo.duckdns.org"
      exit 0 ;;
    Failed|Cancelled|TimedOut|Cancelling)
      "${AWS[@]}" ssm get-command-invocation --command-id "$COMMAND_ID" --instance-id "$INSTANCE_ID" --query '[StandardOutputContent,StandardErrorContent]' --output text >&2 || true
      exit 1 ;;
  esac
  sleep 5
done
echo "SSM command still running: $COMMAND_ID" >&2
exit 1
