#!/usr/bin/env bash
set -euo pipefail

workspace="${GITHUB_WORKSPACE:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
icon_bundle="$workspace/iosApp/EasyOpen/Resources/AppIcon.icon"
icon_catalog="$workspace/iosApp/EasyOpen/Resources/Assets.xcassets/AppIcon.appiconset"
info_plist="$workspace/iosApp/EasyOpen/Info.plist"
pbxproj="$workspace/iosApp/EasyOpen.xcodeproj/project.pbxproj"

for path in \
  "$icon_bundle/icon.json" \
  "$icon_catalog/Contents.json" \
  "$icon_catalog/AppIcon-1024.png" \
  "$info_plist" \
  "$pbxproj"; do
  test -f "$path"
done

python3 - "$icon_bundle/icon.json" "$icon_catalog/Contents.json" "$icon_catalog/AppIcon-1024.png" "$info_plist" "$pbxproj" <<'PY'
import json
import plistlib
import struct
import sys
from pathlib import Path

icon_json_path, catalog_json_path, png_path, plist_path, pbx_path = map(Path, sys.argv[1:])
assets_path = icon_json_path.parent / "Assets"
icon = json.loads(icon_json_path.read_text(encoding="utf-8"))
catalog = json.loads(catalog_json_path.read_text(encoding="utf-8"))
plist = plistlib.loads(plist_path.read_bytes())
pbx = pbx_path.read_text(encoding="utf-8")

assert icon.get("groups"), "Icon Composer icon must contain at least one layer group"
layer_names = [
    layer.get("image-name")
    for group in icon["groups"]
    for layer in group.get("layers", [])
    if layer.get("image-name")
]
assert layer_names, "Icon Composer icon must reference at least one layer asset"
available_assets = {path.name for path in assets_path.iterdir() if path.is_file()}
assert set(layer_names).issubset(available_assets), (
    f"Icon Composer layer asset is missing: {set(layer_names) - available_assets}"
)
assert icon.get("supported-platforms", {}).get("squares") == "shared"

# Accept both the original explicit background/foreground SVG setup and the
# Icon Composer representation exported by Xcode as one PNG layer plus an
# automatic gradient fill. Both are valid layered icon inputs; the important
# invariant is that every referenced asset exists and the foreground is not
# silently dropped.
foreground_path = assets_path / "Foreground.svg"
if foreground_path.is_file():
    foreground = foreground_path.read_text(encoding="utf-8")
    assert 'viewBox="0 0 1024 1024"' in foreground, (
        "iOS foreground must use the full 1024px icon canvas"
    )
    assert "scale(.60)" not in foreground and 'scaleX="0.60"' not in foreground, (
        "Android adaptive-icon inset must not be copied to the iOS foreground"
    )

images = catalog.get("images", [])
assert any(
    image.get("filename") == "AppIcon-1024.png"
    and image.get("idiom") == "universal"
    and image.get("size") == "1024x1024"
    and image.get("scale") == "1x"
    for image in images
), "Static 1024px fallback App Icon is missing"

png = png_path.read_bytes()
assert png[:8] == b"\x89PNG\r\n\x1a\n"
width, height = struct.unpack(">II", png[16:24])
assert (width, height) == (1024, 1024), (width, height)

assert plist.get("CFBundleIconName") == "AppIcon"
for required in (
    "lastKnownFileType = folder.assetcatalog",
    "lastKnownFileType = folder.iconcomposer.icon",
    "ASSETCATALOG_COMPILER_APPICON_NAME = AppIcon;",
    "ASSETCATALOG_COMPILER_INCLUDE_ALL_APPICON_ASSETS = YES;",
):
    assert required in pbx, f"Xcode icon wiring is missing: {required}"

print("iOS Icon Composer and fallback App Icon assets are structurally valid")
PY
