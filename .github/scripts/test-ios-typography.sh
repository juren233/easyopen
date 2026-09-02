#!/usr/bin/env bash
set -euo pipefail

workspace="${GITHUB_WORKSPACE:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
common_file="$workspace/shared/src/commonMain/kotlin/com/juren233/easyopen/shared/ui/EasyOpenTheme.kt"
expect_file="$workspace/shared/src/commonMain/kotlin/com/juren233/easyopen/shared/ui/EasyOpenPlatformTypography.kt"
ios_file="$workspace/shared/src/iosMain/kotlin/com/juren233/easyopen/shared/ui/EasyOpenPlatformTypography.ios.kt"
android_file="$workspace/shared/src/androidMain/kotlin/com/juren233/easyopen/shared/ui/EasyOpenPlatformTypography.android.kt"

python3 - "$common_file" "$expect_file" "$ios_file" "$android_file" <<'PY'
from pathlib import Path
import sys

common, expect, ios, android = map(lambda value: Path(value).read_text(encoding="utf-8"), sys.argv[1:])

if "internal expect val easyOpenTextFontFamily: FontFamily" not in expect:
    raise SystemExit("shared typography expect declaration is missing")
if "internal actual val easyOpenTextFontFamily: FontFamily = FontFamily.Default" not in android:
    raise SystemExit("Android typography must keep the platform default font family")
for weight in ("W400", "W500", "W700"):
    marker = f'SystemFont("PingFang SC", FontWeight.{weight})'
    if marker not in ios:
        raise SystemExit(f"iOS typography is missing explicit PingFang weight: {weight}")
if "MiuixTheme(controller = controller, textStyles = textStyles, content = content)" not in common:
    raise SystemExit("EasyOpenTheme does not install the platform typography")
for style in ("main", "body2", "button", "headline1", "subtitle", "title1"):
    marker = f"{style} = defaults.{style}.copy(fontFamily = easyOpenTextFontFamily)"
    if marker not in common:
        raise SystemExit(f"EasyOpenTheme does not apply the platform family to {style}")

print("iOS typography validated: PingFang SC is explicit for CJK weights W400/W500/W700")
PY
