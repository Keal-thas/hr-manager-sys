#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

if [ ! -d web/node_modules ]; then
  echo "web/node_modules not found, run: cd web && npm ci"
  exit 1
fi

echo "[check] frontend tests"
(cd web && npm test)

echo "[check] frontend build"
(cd web && npm run build)

echo "[check] generated assets not tracked"
tracked="$(git ls-files -- src/main/resources/static)"
if [ -n "$tracked" ]; then
  echo "ERROR: generated frontend assets are tracked by git:"
  echo "$tracked"
  exit 1
fi

echo "[check] backend tests and coverage"
mvn -B -ntp verify

echo "[check] done"
