#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

if [ ! -d web/node_modules ]; then
  echo "web/node_modules not found, run: cd web && npm ci"
  exit 1
fi

if [ "${E2E_INSTALL_BROWSER:-0}" = "1" ]; then
  echo "[e2e] installing Playwright Chromium"
  (cd web && npx playwright install chromium)
fi

echo "[e2e] building frontend and running Playwright"
(cd web && npm run e2e)
