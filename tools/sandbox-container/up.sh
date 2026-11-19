#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

if [ -z "${GITHUB_TOKEN:-}" ] && command -v gh >/dev/null 2>&1; then
  GITHUB_TOKEN="$(gh auth token 2>/dev/null || true)"
fi
export GITHUB_TOKEN="${GITHUB_TOKEN:-}"

docker compose up -d --build
docker compose ps
