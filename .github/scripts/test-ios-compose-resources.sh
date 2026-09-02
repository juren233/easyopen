#!/usr/bin/env bash
set -euo pipefail

workspace="${GITHUB_WORKSPACE:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
resource_file=""
source_xml=""
accessor_dir="$workspace/shared/build/generated/compose/resourceGenerator/kotlin/commonMainResourceAccessors/easyopen/shared/generated/resources"

usage() {
  cat >&2 <<'USAGE'
Usage: test-ios-compose-resources.sh --resource-file <strings.commonMain.cvr> --source-xml <strings.xml> [--accessor-dir <generated accessor directory>]
USAGE
  exit 2
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --resource-file)
      [[ $# -ge 2 ]] || usage
      resource_file="$2"
      shift 2
      ;;
    --source-xml)
      [[ $# -ge 2 ]] || usage
      source_xml="$2"
      shift 2
      ;;
    --accessor-dir)
      [[ $# -ge 2 ]] || usage
      accessor_dir="$2"
      shift 2
      ;;
    *)
      usage
      ;;
  esac
done

[[ -n "$resource_file" && -n "$source_xml" ]] || usage
test -f "$resource_file"
test -f "$source_xml"
test -d "$accessor_dir"

python3 - "$resource_file" "$source_xml" "$accessor_dir" <<'PY'
import base64
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

resource_path = Path(sys.argv[1])
source_xml_path = Path(sys.argv[2])
accessor_dir = Path(sys.argv[3])
resource_bytes = resource_path.read_bytes()
accessor_files = sorted(accessor_dir.glob("String*.commonMain.kt"))
if not accessor_files:
    raise SystemExit(f"no generated Compose string accessors found in {accessor_dir}")

pattern = re.compile(
    r'public val Res\.string\.(?P<key>[A-Za-z_][A-Za-z0-9_]*)'
    r'.*?ResourceItem\(setOf\(\), "\$\{MD\}values/strings\.commonMain\.cvr", '
    r'(?P<offset>\d+), (?P<size>\d+)\)',
    re.DOTALL,
)
entries = {}
for accessor_file in accessor_files:
    source = accessor_file.read_text(encoding="utf-8")
    for match in pattern.finditer(source):
        key = match.group("key")
        entry = (int(match.group("offset")), int(match.group("size")), accessor_file.name)
        previous = entries.get(key)
        if previous is not None and previous[:2] != entry[:2]:
            raise SystemExit(f"resource accessor {key!r} has conflicting offsets: {previous} vs {entry}")
        entries[key] = entry

if not entries:
    raise SystemExit("no Compose string accessor entries were parsed")

xml_root = ET.parse(source_xml_path).getroot()
def decode_resource_escapes(value: str) -> str:
    # Compose resource XML follows Android-style escaped text for common
    # controls (for example \n in device_summary). Match the generator's
    # decoded value before comparing it with the CVR payload.
    result = []
    index = 0
    while index < len(value):
        if value[index] != "\\" or index + 1 >= len(value):
            result.append(value[index])
            index += 1
            continue
        escaped = value[index + 1]
        replacements = {"n": "\n", "r": "\r", "t": "\t", "\\": "\\", "\"": "\"", "'": "'"}
        if escaped in replacements:
            result.append(replacements[escaped])
            index += 2
            continue
        if escaped == "u" and index + 5 < len(value):
            codepoint = value[index + 2:index + 6]
            try:
                result.append(chr(int(codepoint, 16)))
                index += 6
                continue
            except ValueError:
                pass
        result.append("\\")
        index += 1
    return "".join(result)

source_entries = {}
for element in xml_root.findall("string"):
    key = element.attrib.get("name", "")
    if not key:
        raise SystemExit(f"source XML contains a string without a name: {source_xml_path}")
    if key in source_entries:
        raise SystemExit(f"source XML contains duplicate string key: {key!r}")
    source_entries[key] = decode_resource_escapes("".join(element.itertext()))

if set(entries) != set(source_entries):
    raise SystemExit(
        "generated accessors and source XML disagree: "
        f"missing_in_accessors={sorted(set(source_entries) - set(entries))[:10]} "
        f"extra_in_accessors={sorted(set(entries) - set(source_entries))[:10]}"
    )

if not resource_bytes.startswith(b"version:0\n"):
    raise SystemExit(f"{resource_path} does not start with the expected CVR header")

cvr_entries = []
for line_number, line in enumerate(resource_bytes.splitlines()[1:], start=2):
    parts = line.split(b"|", 2)
    if len(parts) != 3 or parts[0] != b"string":
        raise SystemExit(f"invalid CVR string record at line {line_number}: {line[:120]!r}")
    key = parts[1].decode("ascii")
    try:
        decoded = base64.b64decode(parts[2], validate=True).decode("utf-8")
    except Exception as error:
        raise SystemExit(f"CVR record {key!r} is not valid UTF-8 base64: {error}") from error
    cvr_entries.append((key, decoded))

cvr_keys = [key for key, _ in cvr_entries]
if len(cvr_keys) != len(set(cvr_keys)):
    raise SystemExit("CVR contains duplicate string keys")

missing = sorted(set(entries) - set(cvr_keys))
extra = sorted(set(cvr_keys) - set(entries))
if missing or extra:
    raise SystemExit(
        "generated accessors and packaged CVR disagree: "
        f"missing={missing[:10]} extra={extra[:10]}"
    )

for key, (offset, size, accessor_file) in sorted(entries.items()):
    segment = resource_bytes[offset:offset + size]
    if len(segment) != size:
        raise SystemExit(
            f"{key!r} from {accessor_file} points past the CVR: offset={offset} size={size}"
        )
    record = segment.rstrip(b"\n")
    parts = record.split(b"|", 2)
    if len(parts) != 3 or parts[0] != b"string" or parts[1].decode("ascii") != key:
        raise SystemExit(
            f"{key!r} accessor offset does not point to its own CVR record: "
            f"offset={offset} size={size} record={record[:120]!r}"
        )
    try:
        decoded = base64.b64decode(parts[2], validate=True).decode("utf-8")
    except Exception as error:
        raise SystemExit(f"{key!r} accessor record is not valid UTF-8: {error}") from error
    expected = source_entries[key]
    if decoded != expected:
        raise SystemExit(
            f"{key!r} CVR text does not match {source_xml_path}: "
            f"expected={expected!r} actual={decoded!r}"
        )

print(
    "iOS Compose resources validated: "
    f"{len(entries)} accessors, {len(cvr_entries)} CVR records, UTF-8, source text and offsets match"
)
PY
