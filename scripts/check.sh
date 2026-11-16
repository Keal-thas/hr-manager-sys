#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

echo "[check] backend tests"
mvn -q test

echo "[check] frontend build"
if [ ! -d web/node_modules ]; then
  echo "web/node_modules not found, run: cd web && npm ci"
  exit 1
fi
(cd web && npm run build)

echo "[check] static assets committed"
if ! git diff --quiet -- src/main/resources/static; then
  echo "ERROR: frontend build changed src/main/resources/static"
  git status --short src/main/resources/static
  exit 1
fi

echo "[check] done"
