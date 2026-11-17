#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

mode="${1:-standard}"

if [ ! -d web/node_modules ]; then
  echo "web/node_modules not found, run: cd web && npm ci"
  exit 1
fi

case "$mode" in
  quick)
    echo "[agent-check] backend compile"
    mvn -B -ntp -q -DskipTests compile
    echo "[agent-check] frontend unit tests"
    (cd web && npm test)
    ;;
  standard)
    echo "[agent-check] standard checks"
    bash scripts/check.sh
    ;;
  full)
    echo "[agent-check] full checks"
    bash scripts/check.sh
    echo "[agent-check] e2e"
    bash scripts/e2e.sh
    ;;
  *)
    echo "usage: bash scripts/agent-check.sh [quick|standard|full]"
    exit 1
    ;;
esac

echo "[agent-check] $mode passed"
