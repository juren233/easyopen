#!/usr/bin/env bash
set -euo pipefail

workspace="${GITHUB_WORKSPACE:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
project_path="${XCODE_PROJECT:-$workspace/iosApp/EasyOpen.xcodeproj}"
scheme="${XCODE_SCHEME:-EasyOpen}"
configuration="${XCODE_CONFIGURATION:-Debug}"
derived_data_path="${DERIVED_DATA_PATH:-${RUNNER_TEMP:-$workspace/.tmp}/easyopen-simulator-derived-data}"
shared_simulator_framework="$workspace/shared/build/bin/iosSimulatorArm64/debugFramework"
simulator_name="EasyOpen-CI-${GITHUB_RUN_ID:-$$}"

cleanup() {
  if [[ -n "${simulator_udid:-}" ]]; then
    xcrun simctl shutdown "$simulator_udid" >/dev/null 2>&1 || true
    xcrun simctl delete "$simulator_udid" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

runtime_id="$(xcrun simctl list runtimes | grep -o 'com.apple.CoreSimulator.SimRuntime.iOS[^ ]*' | head -n 1 || true)"
device_type_id="$(xcrun simctl list devicetypes | grep -o 'com.apple.CoreSimulator.SimDeviceType.iPhone[^ ]*' | head -n 1 || true)"
test -n "$runtime_id"
test -n "$device_type_id"

echo "Using simulator runtime: $runtime_id"
echo "Using simulator device type: $device_type_id"
simulator_udid="$(xcrun simctl create "$simulator_name" "$device_type_id" "$runtime_id")"
xcrun simctl boot "$simulator_udid"
xcrun simctl bootstatus "$simulator_udid" -b

./gradlew --no-daemon --max-workers=2 --build-cache -PallowUnsigned=true \
  :shared:linkDebugFrameworkIosSimulatorArm64

test -d "$shared_simulator_framework"
# The checked-in hand-written project points its framework file reference at
# iosArm64. The Simulator workflow uses a disposable checkout, so rewrite both
# the file reference and search paths to the simulator framework before Xcode
# reads the project. This avoids linking an iPhone binary into the Simulator.
sed -i.bak \
  -e 's#iosArm64/releaseFramework#iosSimulatorArm64/debugFramework#g' \
  -e 's#iosArm64/debugFramework#iosSimulatorArm64/debugFramework#g' \
  "$project_path/project.pbxproj"
test -e "$shared_simulator_framework/EasyOpenShared.framework"

rm -rf "$derived_data_path"
xcodebuild \
  -project "$project_path" \
  -scheme "$scheme" \
  -configuration "$configuration" \
  -sdk iphonesimulator \
  -destination "id=$simulator_udid" \
  -derivedDataPath "$derived_data_path" \
  build \
  CODE_SIGNING_ALLOWED=NO \
  CODE_SIGNING_REQUIRED=NO \
  CODE_SIGN_IDENTITY='' \
  CURRENT_PROJECT_VERSION="${CURRENT_PROJECT_VERSION:-1}" \
  MARKETING_VERSION="${MARKETING_VERSION:-1.1.0}" \
  DEBUG_INFORMATION_FORMAT=dwarf

app_path="$derived_data_path/Build/Products/${configuration}-iphonesimulator/EasyOpen.app"
test -d "$app_path"
test -f "$app_path/Info.plist"
plist_bundle_id="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleIdentifier' "$app_path/Info.plist")"
test "$plist_bundle_id" = "com.juren233.easyopen.ios"

xcrun simctl install "$simulator_udid" "$app_path"
xcrun simctl launch "$simulator_udid" "$plist_bundle_id"
sleep 5

# A launch command returning successfully is not enough: inspect the running
# process and recent app log for an immediate termination.
if ! xcrun simctl spawn "$simulator_udid" ps -axo comm= \
  | awk '$1 == "EasyOpen" { found = 1 } END { exit(found ? 0 : 1) }'; then
  echo "EasyOpen is not running after simulator launch" >&2
  exit 1
fi

simulator_recent_log="$(xcrun simctl spawn "$simulator_udid" log show --last 30s --style compact \
  --predicate "process == 'EasyOpen' AND (eventMessage CONTAINS[c] 'uncaught' OR eventMessage CONTAINS[c] 'exception' OR eventMessage CONTAINS[c] 'crash')" \
  || true)"
if [[ -n "$simulator_recent_log" ]]; then
  echo "EasyOpen emitted a simulator crash/exception log after launch" >&2
  printf '%s\n' "$simulator_recent_log" >&2
  exit 1
fi

echo "iOS Simulator smoke passed: build, install and launch remained alive"
