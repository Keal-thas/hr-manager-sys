#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

if [ ! -d web/node_modules ]; then
  echo "web/node_modules not found, run: cd web && npm ci"
  exit 1
fi

if [ ! -f src/main/resources/static/index.html ]; then
  echo "[e2e] frontend build missing, building"
  (cd web && npm run build)
fi

if ! command -v mysql >/dev/null 2>&1; then
  echo "[e2e] mysql client not found; skipping schema reset, assuming MySQL 8 is reachable"
else
  : "${DB_HOST:=localhost}"
  : "${DB_PORT:=3306}"
  : "${DB_NAME:=hr_e2e}"
  : "${DB_USERNAME:=root}"
  : "${DB_PASSWORD:=}"
  echo "[e2e] resetting e2e schema"
  mysql -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USERNAME" ${DB_PASSWORD:+-p"$DB_PASSWORD"} \
    -e "DROP DATABASE IF EXISTS $DB_NAME;" 2>/dev/null || true
fi

echo "[e2e] starting backend on http://127.0.0.1:18080"
exec mvn -B -ntp -q -DskipTests spring-boot:run \
  -Dspring-boot.run.profiles=e2e \
  -Dspring-boot.run.arguments=--server.port=18080
