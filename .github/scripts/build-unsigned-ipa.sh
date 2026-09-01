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
inspection_name="${output_name%.ipa}-inspection.txt"
inspection_path="$workspace/$inspection_name"
derived_data_path="${RUNNER_TEMP:-$workspace/.tmp}/easyopen-derived-data"

mkdir -p "$archive_root" "$payload_dir"
printf '%s\n' "Android display version: $full_name"
printf '%s\n' "iOS CFBundleShortVersionString: $ios_version"
printf '%s\n' "iOS CFBundleVersion: $version_code"

framework_started=$SECONDS
./gradlew --no-daemon --max-workers=2 --build-cache -PallowUnsigned=true "$framework_task"
framework_seconds=$((SECONDS - framework_started))
test -d "$framework_path"
printf '%s\n' "Timing Kotlin/Native framework: ${framework_seconds}s"

archive_started=$SECONDS
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
archive_seconds=$((SECONDS - archive_started))
package_started=$SECONDS
printf '%s\n' "Timing Xcode archive: ${archive_seconds}s"

app_path="$(find "$archive_path/Products/Applications" -maxdepth 1 -type d -name '*.app' -print -quit)"
test -n "$app_path"
test -d "$app_path"

# Compose Multiplatform resources are not part of a static Kotlin/Native
# framework's executable. The normal Xcode integration runs
# syncComposeResourcesForIos, but this repository intentionally drives
# xcodebuild from a minimal hand-written project and packages the archive
# manually. Sync the resource tree into the archived app before creating the
# IPA; otherwise the first stringResource() lookup can terminate the app at
# launch even though archive/link validation succeeds.
app_products_dir="$(dirname "$app_path")"
app_bundle_name="$(basename "$app_path")"
compose_resources_dir="$app_path/compose-resources"
PLATFORM_NAME=iphoneos \
ARCHS=arm64 \
BUILT_PRODUCTS_DIR="$app_products_dir" \
UNLOCALIZED_RESOURCES_FOLDER_PATH="$app_bundle_name" \
./gradlew --no-daemon --max-workers=2 --build-cache \
  -Pcompose.ios.resources.platform=iphoneos \
  -Pcompose.ios.resources.archs=arm64 \
  :shared:syncComposeResourcesForIos
test -d "$compose_resources_dir/composeResources"
test -f "$compose_resources_dir/composeResources/easyopen.shared.generated.resources/values/strings.commonMain.cvr"
compose_resource_files="$(find "$compose_resources_dir" -type f -print | wc -l | tr -d '[:space:]')"
test "$compose_resource_files" -gt 0

actual_ios_version="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleShortVersionString' "$app_path/Info.plist")"
actual_version_code="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleVersion' "$app_path/Info.plist")"
test "$actual_ios_version" = "$ios_version"
test "$actual_version_code" = "$version_code"

app_executable_name="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleExecutable' "$app_path/Info.plist")"
app_executable="$app_path/$app_executable_name"
test -f "$app_executable"
app_architectures="$(/usr/bin/lipo -archs "$app_executable")"
framework_architectures="$(/usr/bin/lipo -archs "$framework_path/EasyOpenShared")"
[[ " $app_architectures " == *" arm64 "* ]]
[[ " $framework_architectures " == *" arm64 "* ]]
test ! -d "$app_path/_CodeSignature"
if /usr/bin/codesign --verify --deep --strict "$app_path" >/dev/null 2>&1; then
  printf 'Expected an unsigned app, but codesign verification succeeded: %s\n' "$app_path" >&2
  exit 1
fi

{
  printf 'android_display_version=%s\n' "$full_name"
  printf 'ios_bundle_short_version=%s\n' "$actual_ios_version"
  printf 'ios_bundle_version=%s\n' "$actual_version_code"
  printf 'app_architectures=%s\n' "$app_architectures"
  printf 'framework_architectures=%s\n' "$framework_architectures"
  printf 'unsigned=true\n'
  printf 'compose_resources_dir=%s\n' "${compose_resources_dir#"$app_path/"}"
  printf 'compose_resource_files=%s\n' "$compose_resource_files"
  printf '\nlinked_libraries:\n'
  /usr/bin/otool -L "$app_executable"
} > "$inspection_path"

cp -R "$app_path" "$payload_dir/"
(cd "$payload_root" && /usr/bin/zip -qry "$output_path" Payload)
test -s "$output_path"
/usr/bin/unzip -tq "$output_path" >/dev/null
test -s "$inspection_path"
package_seconds=$((SECONDS - package_started))
printf 'framework_seconds=%s\n' "$framework_seconds" >> "$inspection_path"
printf 'archive_seconds=%s\n' "$archive_seconds" >> "$inspection_path"
printf 'package_seconds=%s\n' "$package_seconds" >> "$inspection_path"
printf '%s\n' "Timing IPA packaging and inspection: ${package_seconds}s"
printf '%s\n' "Timing total: ${SECONDS}s"

printf '%s\n' "IPA: $output_path"
/usr/bin/unzip -l "$output_path" | sed -n '1,24p'

if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
  printf 'ipa_path=%s\n' "$output_path" >> "$GITHUB_OUTPUT"
  printf 'ipa_name=%s\n' "$output_name" >> "$GITHUB_OUTPUT"
  printf 'inspection_name=%s\n' "$inspection_name" >> "$GITHUB_OUTPUT"
fi
