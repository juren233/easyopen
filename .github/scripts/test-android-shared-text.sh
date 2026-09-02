#!/usr/bin/env bash
set -euo pipefail

workspace="${GITHUB_WORKSPACE:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
ui_root="$workspace/app/src/main/java/com/juren233/easyopen"

if rg -n 'R\.string\.' \
  "$ui_root/EasyOpenContent.kt" \
  "$ui_root/ui" \
  --glob '*.kt'; then
  echo "Android UI still contains direct R.string usage; use shared EasyOpenStrings instead." >&2
  exit 1
fi

printf '%s\n' 'Android UI user-facing strings use shared EasyOpenStrings'
