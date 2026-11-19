#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
docker compose exec sandbox bash -lc 'cd /workspace/hr-manager-sys && exec bash'
