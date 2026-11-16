#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
git config core.hooksPath .githooks
git config core.whitespace cr-at-eol
chmod +x .githooks/pre-commit .githooks/pre-push 2>/dev/null || true
echo "git hooks installed: core.hooksPath=.githooks"
