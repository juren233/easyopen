#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
version_file="${VERSION_FILE:-app/build.gradle.kts}"
event_name="${EVENT_NAME:-push}"
output_file="${GITHUB_OUTPUT:-/dev/stdout}"

emit() {
  printf '%s\n' "$1" >> "$output_file"
}

while IFS='=' read -r key value; do
  case "$key" in
    source_name) source_name="$value" ;;
    code) code="$value" ;;
    base) base="$value" ;;
    channel) channel="$value" ;;
    full_name) full_name="$value" ;;
    full_tag) full_tag="$value" ;;
    prev_stable_tag) prev_stable_tag="$value" ;;
    ios_version) ios_version="$value" ;;
    *) emit "$key=$value" ;;
  esac
done < <(VERSION_FILE="$version_file" bash "$script_dir/resolve-version.sh")

: "${source_name:?resolve-version.sh did not return source_name}"
: "${code:?resolve-version.sh did not return code}"
: "${full_name:?resolve-version.sh did not return full_name}"
: "${ios_version:?resolve-version.sh did not return ios_version}"

current_name="$source_name"
current_code="$code"
skip=false

if [[ "$event_name" == "pull_request" || "$event_name" == "workflow_dispatch" ]]; then
  echo "${event_name} trigger, proceeding with resolved version"
elif [[ "$current_name" =~ -(beta|canary)$ ]]; then
  echo "${BASH_REMATCH[1]} channel commit detected, proceeding"
elif git rev-parse HEAD^ >/dev/null 2>&1; then
  previous_name="$(git show HEAD^:"$version_file" 2>/dev/null | sed -nE 's/^[[:space:]]*versionName[[:space:]]*=.*"([^"]+)".*/\1/p' | head -n1 || true)"
  previous_code="$(git show HEAD^:"$version_file" 2>/dev/null | sed -nE 's/^[[:space:]]*versionCode[[:space:]]*=*[[:space:]]*([0-9]+).*/\1/p' | head -n1 || true)"

  if [[ "$current_name" != "$previous_name" || "$current_code" != "$previous_code" ]]; then
    echo "version changed (Name: $previous_name -> $current_name, Code: $previous_code -> $current_code), proceeding"
  else
    skip=true
    echo "stable version unchanged (Name: $current_name, Code: $current_code), skipping build"
  fi
else
  echo "no parent commit, proceeding"
fi

for key in source_name code base channel full_name full_tag prev_stable_tag ios_version; do
  emit "$key=${!key-}"
done
emit "skip=$skip"
