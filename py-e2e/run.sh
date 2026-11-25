#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

IMAGE="${PY_E2E_IMAGE:-hr-manager-py-e2e}"
HOST_APP_URL="${E2E_BASE_URL:-http://127.0.0.1:18080}"
CONTAINER_APP_URL="${PY_E2E_CONTAINER_BASE_URL:-}"

if [ -z "$CONTAINER_APP_URL" ]; then
  case "$HOST_APP_URL" in
    http://127.0.0.1:*|http://localhost:*)
      CONTAINER_APP_URL="http://host.docker.internal:${HOST_APP_URL##*:}"
      ;;
    *)
      CONTAINER_APP_URL="$HOST_APP_URL"
      ;;
  esac
fi

RECORD_ARGS=()
if [ "${1:-}" = "--record" ]; then
  shift
  RECORD_ARGS=(--video=on --tracing=on --screenshot=on --slowmo=500)
fi

if [ "${PY_E2E_SKIP_HOST_CHECK:-0}" != "1" ]; then
  if ! curl -fsS "$HOST_APP_URL/api/health" >/dev/null; then
    echo "[py-e2e] backend not reachable: $HOST_APP_URL"
    echo "[py-e2e] start it from the repo root first: bash scripts/e2e-server.sh"
    exit 1
  fi
fi

mkdir -p artifacts

echo "[py-e2e] building $IMAGE"
docker build -t "$IMAGE" .

exec docker run --rm \
  --add-host=host.docker.internal:host-gateway \
  -e "E2E_BASE_URL=$CONTAINER_APP_URL" \
  -v "$PWD/artifacts:/results" \
  "$IMAGE" -q --output=/results ${RECORD_ARGS[@]+"${RECORD_ARGS[@]}"} "$@"
