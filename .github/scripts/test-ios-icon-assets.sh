#!/usr/bin/env bash
set -euo pipefail

workspace="${GITHUB_WORKSPACE:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
icon_bundle="$workspace/iosApp/EasyOpen/Resources/AppIcon.icon"
icon_catalog="$workspace/iosApp/EasyOpen/Resources/Assets.xcassets/AppIcon.appiconset"
info_plist="$workspace/iosApp/EasyOpen/Info.plist"
pbxproj="$workspace/iosApp/EasyOpen.xcodeproj/project.pbxproj"

for path in \
  "$icon_bundle/icon.json" \
  "$icon_bundle/Assets/Background.svg" \
  "$icon_bundle/Assets/Foreground.svg" \
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
foreground_path = icon_json_path.parent / "Assets" / "Foreground.svg"
icon = json.loads(icon_json_path.read_text(encoding="utf-8"))
catalog = json.loads(catalog_json_path.read_text(encoding="utf-8"))
plist = plistlib.loads(plist_path.read_bytes())
pbx = pbx_path.read_text(encoding="utf-8")
foreground = foreground_path.read_text(encoding="utf-8")

assert len(icon.get("groups", [])) >= 2, "Icon Composer icon must contain background and foreground groups"
layer_names = [
    layer.get("image-name")
    for group in icon["groups"]
    for layer in group.get("layers", [])
]
assert "Background.svg" in layer_names, "Icon Composer background layer is missing"
assert "Foreground.svg" in layer_names, "Icon Composer foreground layer is missing"
assert 'viewBox="0 0 1024 1024"' in foreground, "iOS foreground must use the full 1024px icon canvas"
assert "scale(.60)" not in foreground and 'scaleX="0.60"' not in foreground, (
    "Android adaptive-icon inset must not be copied to the iOS foreground"
)
assert icon.get("supported-platforms", {}).get("squares") == "shared"

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

print("iOS layered App Icon assets are structurally valid")
PY
