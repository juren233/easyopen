#!/usr/bin/env bash
set -euo pipefail

minimum_xcode_major="${MINIMUM_XCODE_MAJOR:-26}"
xcode_output="$(xcodebuild -version)"
xcode_major="$(awk '/^Xcode / { split($2, version, "."); print version[1]; exit }' <<< "$xcode_output")"

printf '%s\n' "$xcode_output"
xcrun --sdk iphoneos --show-sdk-version
swift --version

if [[ ! "$xcode_major" =~ ^[0-9]+$ ]]; then
  printf 'Unable to parse Xcode major version from: %s\n' "$xcode_output" >&2
  exit 1
fi
if (( xcode_major < minimum_xcode_major )); then
  printf 'Xcode %s or newer is required; current major version is %s.\n' \
    "$minimum_xcode_major" "$xcode_major" >&2
  exit 1
fi
