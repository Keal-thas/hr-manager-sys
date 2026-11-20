#!/usr/bin/env bash
set -Eeuo pipefail

GIT_URL="https://github.com/Keal-thas/hr-manager-sys.git"
REPO_DIR="${REPO_DIR:-/workspace/hr-manager-sys}"
LOG_DIR="${LOG_DIR:-/workspace/logs}"
APP_PORT="${APP_PORT:-8081}"
OC_PORT="${OC_PORT:-4097}"

mkdir -p "$LOG_DIR" /workspace
export GIT_TERMINAL_PROMPT=0

if [ -n "${GH_TOKEN:-}" ]; then
  gh auth setup-git >/dev/null 2>&1 || true
fi

if [ -n "${GIT_USER_NAME:-}" ]; then
  git config --global user.name "$GIT_USER_NAME"
fi
if [ -n "${GIT_USER_EMAIL:-}" ]; then
  git config --global user.email "$GIT_USER_EMAIL"
fi

if [ ! -d "$REPO_DIR/.git" ]; then
  echo "[init] cloning $GIT_URL"
  if [ -n "${GITHUB_TOKEN:-}" ]; then
    git clone --depth 1 \
      "https://x-access-token:${GITHUB_TOKEN}@github.com/Keal-thas/hr-manager-sys.git" \
      "$REPO_DIR"
    git -C "$REPO_DIR" remote set-url origin "$GIT_URL"
  else
    git clone --depth 1 "$GIT_URL" "$REPO_DIR"
  fi
elif [ "${REFRESH:-0}" = "1" ]; then
  echo "[init] refreshing working tree"
  git -C "$REPO_DIR" fetch --depth 1 origin master
  git -C "$REPO_DIR" reset --hard origin/master
fi

jar_path() {
  find "$REPO_DIR/target" -maxdepth 1 -name 'hr-manager-sys-*.jar' ! -name '*.original' 2>/dev/null | head -1
}

JAR="$(jar_path || true)"
if [ "${REBUILD:-0}" = "1" ] || [ -z "$JAR" ]; then
  echo "[init] building frontend + jar (first run can take several minutes)"
  cd "$REPO_DIR/web"
  npm ci
  npm run build
  cd "$REPO_DIR"
  mvn -B -ntp -DskipTests package
  JAR="$(jar_path)"
fi
[ -n "$JAR" ] || { echo "[init] jar not found after build"; exit 1; }

cd "$REPO_DIR"

: > "$LOG_DIR/app.log"
: > "$LOG_DIR/opencode.log"

echo "[run] app on :$APP_PORT ($JAR)"
java -jar "$JAR" --server.port="$APP_PORT" > "$LOG_DIR/app.log" 2>&1 &
APP_PID=$!

echo "[run] opencode serve on :$OC_PORT"
opencode serve --pure --hostname 0.0.0.0 --port "$OC_PORT" --print-logs \
  > "$LOG_DIR/opencode.log" 2>&1 &
OC_PID=$!

tail -n +1 -F "$LOG_DIR/app.log" 2>/dev/null | sed -u 's/^/[app] /' &
tail -n +1 -F "$LOG_DIR/opencode.log" 2>/dev/null | sed -u 's/^/[opencode] /' &

cleanup() {
  echo "[run] stopping"
  kill "$APP_PID" "$OC_PID" 2>/dev/null || true
  wait "$APP_PID" "$OC_PID" 2>/dev/null || true
}
trap cleanup TERM INT

wait -n "$APP_PID" "$OC_PID"
echo "[run] a service exited, stopping container"
cleanup
exit 1
