#!/usr/bin/env bash
set -euo pipefail

workspace="${GITHUB_WORKSPACE:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
source_xml="$workspace/shared/src/commonMain/composeResources/values/strings.xml"
fallback_file="$workspace/shared/src/commonMain/kotlin/com/juren233/easyopen/shared/resources/EasyOpenStringValues.kt"

python3 - "$source_xml" "$fallback_file" <<'PY'
import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

source_path = Path(sys.argv[1])
fallback_path = Path(sys.argv[2])


def decode_resource_escapes(value: str) -> str:
    result = []
    index = 0
    replacements = {"n": "\n", "r": "\r", "t": "\t", "\\": "\\", '"': '"', "'": "'"}
    while index < len(value):
        if value[index] != "\\" or index + 1 >= len(value):
            result.append(value[index])
            index += 1
            continue
        escaped = value[index + 1]
        if escaped in replacements:
            result.append(replacements[escaped])
            index += 2
            continue
        if escaped == "u" and index + 5 < len(value):
            try:
                result.append(chr(int(value[index + 2:index + 6], 16)))
                index += 6
                continue
            except ValueError:
                pass
        result.append("\\")
        index += 1
    return "".join(result)


def decode_kotlin_string(value: str) -> str:
    # The generated table uses ordinary Kotlin string literals. Convert the
    # two Kotlin-only escapes before letting JSON decode the remaining syntax.
    value = value.replace(r"\$", "$" )
    return json.loads('"' + value + '"')

source_root = ET.parse(source_path).getroot()
expected = {
    element.attrib["name"]: decode_resource_escapes("".join(element.itertext()))
    for element in source_root.findall("string")
}
pattern = re.compile(r'^\s*"(?P<key>(?:\\.|[^"\\])*)"\s+to\s+"(?P<value>(?:\\.|[^"\\])*)",\s*$')
actual = {}
for line_number, line in enumerate(fallback_path.read_text(encoding="utf-8").splitlines(), start=1):
    match = pattern.match(line)
    if not match:
        continue
    key = decode_kotlin_string(match.group("key"))
    value = decode_kotlin_string(match.group("value"))
    if key in actual:
        raise SystemExit(f"duplicate fallback key {key!r} at line {line_number}")
    actual[key] = value

if actual != expected:
    missing = sorted(set(expected) - set(actual))
    extra = sorted(set(actual) - set(expected))
    mismatched = sorted(key for key in set(expected) & set(actual) if expected[key] != actual[key])
    raise SystemExit(
        "iOS string fallback differs from strings.xml: "
        f"missing={missing[:5]} extra={extra[:5]} mismatched={mismatched[:5]}"
    )

print(f"iOS string fallback validated: {len(actual)} values match strings.xml")
PY
