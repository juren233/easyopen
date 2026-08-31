#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
resolver="$script_dir/resolve-ios-version.sh"

assert_mapping() {
  local android_version="$1"
  local expected="$2"
  local actual
  actual="$(bash "$resolver" "$android_version")"
  if [[ "$actual" != "$expected" ]]; then
    echo "version mapping failed: $android_version -> $actual (expected $expected)" >&2
    exit 1
  fi
}

assert_mapping '1.1.0' '1.1.0'
assert_mapping '1.1.0-beta.1' '1.1.0.1'
assert_mapping '1.1.0-beta.12' '1.1.0.12'
assert_mapping '1.1.0-canary.1' '1.1.0.0.1'
assert_mapping '1.1.0-canary.12' '1.1.0.0.12'

if bash "$resolver" '1.1.0-beta' >/dev/null 2>&1; then
  echo 'invalid beta version was accepted' >&2
  exit 1
fi
if bash "$resolver" '1.1.0-canary' >/dev/null 2>&1; then
  echo 'invalid canary version was accepted' >&2
  exit 1
fi

resolved_ios="$(bash "$script_dir/resolve-version.sh" | sed -n 's/^ios_version=//p')"
resolved_full="$(bash "$script_dir/resolve-version.sh" | sed -n 's/^full_name=//p')"
expected_ios="$(bash "$resolver" "$resolved_full")"
if [[ "$resolved_ios" != "$expected_ios" ]]; then
  echo "resolve-version.sh ios_version mismatch: $resolved_ios (expected $expected_ios)" >&2
  exit 1
fi

printf 'version resolution tests passed\n'
