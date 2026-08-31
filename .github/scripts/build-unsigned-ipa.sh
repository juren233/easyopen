#!/usr/bin/env bash
set -euo pipefail

workspace="${GITHUB_WORKSPACE:-$(pwd)}"
full_name="${FULL_NAME:?FULL_NAME is required}"
ios_version="${IOS_VERSION:?IOS_VERSION is required}"
version_code="${VERSION_CODE:?VERSION_CODE is required}"

project_path="${XCODE_PROJECT:-$workspace/iosApp/EasyOpen.xcodeproj}"
scheme="${XCODE_SCHEME:-EasyOpen}"
configuration="${XCODE_CONFIGURATION:-Release}"
framework_task="${FRAMEWORK_TASK:-:shared:linkReleaseFrameworkIosArm64}"
framework_path="${FRAMEWORK_PATH:-$workspace/shared/build/bin/iosArm64/releaseFramework/EasyOpenShared.framework}"
run_token="${GITHUB_RUN_ID:-$$}"
archive_root="${RUNNER_TEMP:-$workspace/.tmp}/easyopen-archives"
payload_root="${RUNNER_TEMP:-$workspace/.tmp}/easyopen-payloads-$run_token"
archive_path="$archive_root/EasyOpen-$run_token.xcarchive"
payload_dir="$payload_root/Payload"
output_name="${OUTPUT_NAME:-EasyOpen-iOS-v${full_name}-${version_code}.ipa}"
output_path="$workspace/$output_name"
derived_data_path="${RUNNER_TEMP:-$workspace/.tmp}/easyopen-derived-data"

mkdir -p "$archive_root" "$payload_dir"

printf '%s\n' "Android display version: $full_name"
printf '%s\n' "iOS CFBundleShortVersionString: $ios_version"
printf '%s\n' "iOS CFBundleVersion: $version_code"

./gradlew --no-daemon --max-workers=2 --build-cache -PallowUnsigned=true "$framework_task"
test -d "$framework_path"

xcodebuild \
  -project "$project_path" \
  -scheme "$scheme" \
  -configuration "$configuration" \
  -sdk iphoneos \
  -destination 'generic/platform=iOS' \
  -derivedDataPath "$derived_data_path" \
  -archivePath "$archive_path" \
  archive \
  CODE_SIGNING_ALLOWED=NO \
  CODE_SIGNING_REQUIRED=NO \
  CODE_SIGN_IDENTITY='' \
  MARKETING_VERSION="$ios_version" \
  CURRENT_PROJECT_VERSION="$version_code"

app_path="$(find "$archive_path/Products/Applications" -maxdepth 1 -type d -name '*.app' -print -quit)"
test -n "$app_path"
test -d "$app_path"

actual_ios_version="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleShortVersionString' "$app_path/Info.plist")"
actual_version_code="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleVersion' "$app_path/Info.plist")"
test "$actual_ios_version" = "$ios_version"
test "$actual_version_code" = "$version_code"

cp -R "$app_path" "$payload_dir/"
(cd "$payload_root" && /usr/bin/zip -qry "$output_path" Payload)
test -s "$output_path"

printf '%s\n' "IPA: $output_path"
/usr/bin/unzip -l "$output_path" | sed -n '1,24p'

if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
  printf 'ipa_path=%s\n' "$output_path" >> "$GITHUB_OUTPUT"
  printf 'ipa_name=%s\n' "$output_name" >> "$GITHUB_OUTPUT"
fi
