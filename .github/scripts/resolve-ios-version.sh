#!/usr/bin/env bash
set -euo pipefail

android_version="${1:-}"

if [[ -z "$android_version" ]]; then
  echo "Usage: $0 X.Y.Z, X.Y.Z-beta.N, or X.Y.Z-canary.N" >&2
  exit 1
fi

if [[ "$android_version" =~ ^([0-9]+\.[0-9]+\.[0-9]+)$ ]]; then
  printf '%s\n' "${BASH_REMATCH[1]}"
elif [[ "$android_version" =~ ^([0-9]+\.[0-9]+\.[0-9]+)-beta\.([1-9][0-9]*)$ ]]; then
  printf '%s.%s\n' "${BASH_REMATCH[1]}" "${BASH_REMATCH[2]}"
elif [[ "$android_version" =~ ^([0-9]+\.[0-9]+\.[0-9]+)-canary\.([1-9][0-9]*)$ ]]; then
  printf '%s.0.%s\n' "${BASH_REMATCH[1]}" "${BASH_REMATCH[2]}"
else
  echo "Android full version must use X.Y.Z, X.Y.Z-beta.N, or X.Y.Z-canary.N: $android_version" >&2
  exit 1
fi
