#!/usr/bin/env bash
set -euo pipefail

workspace="${GITHUB_WORKSPACE:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
ios_kotlin_root="$workspace/shared/src/iosMain/kotlin"
shared_text_file="$workspace/shared/src/commonMain/kotlin/com/juren233/easyopen/shared/text/EasyOpenPlatformText.kt"

python3 - "$ios_kotlin_root" "$shared_text_file" <<'PY'
import re
import sys
from pathlib import Path

root = Path(sys.argv[1])
shared_text_file = Path(sys.argv[2]).resolve()
# Native UIKit/CoreBluetooth/Core NFC callbacks must use the common text
# interface instead of adding another platform-only set of Chinese literals.
string_literal = re.compile(r'"(?:\\.|[^"\\])*"')
chinese = re.compile(r'[\u4e00-\u9fff]')
violations = []

for path in sorted(root.rglob("*.kt")):
    if path.resolve() == shared_text_file:
        continue
    for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if any(chinese.search(match.group(0)) for match in string_literal.finditer(line)):
            violations.append(f"{path}:{line_number}: {line.strip()}")

if violations:
    print("Found user-facing Chinese string literals in iOS platform Kotlin:", file=sys.stderr)
    print("\n".join(violations), file=sys.stderr)
    raise SystemExit(1)

print("iOS native callback text is centralized in shared EasyOpenPlatformText")
PY
