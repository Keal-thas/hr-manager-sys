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

echo "[e2e] starting backend on http://127.0.0.1:18080"
exec mvn -B -ntp -q -DskipTests spring-boot:run \
  -Dspring-boot.run.profiles=e2e \
  -Dspring-boot.run.arguments=--server.port=18080
