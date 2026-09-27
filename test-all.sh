#!/usr/bin/env bash
# Root-level launcher for macOS/Linux; the implementation lives in scripts/.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec bash "$repo_root/scripts/test-all.sh" "$@"
