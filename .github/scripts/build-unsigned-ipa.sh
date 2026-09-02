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
icon_bundle_path="$workspace/iosApp/EasyOpen/Resources/AppIcon.icon"
icon_catalog_path="$workspace/iosApp/EasyOpen/Resources/Assets.xcassets/AppIcon.appiconset"
run_token="${GITHUB_RUN_ID:-$$}"
archive_root="${RUNNER_TEMP:-$workspace/.tmp}/easyopen-archives"
payload_root="${RUNNER_TEMP:-$workspace/.tmp}/easyopen-payloads-$run_token"
archive_path="$archive_root/EasyOpen-$run_token.xcarchive"
payload_dir="$payload_root/Payload"
output_name="${OUTPUT_NAME:-EasyOpen-iOS-v${full_name}-${version_code}.ipa}"
output_path="$workspace/$output_name"
inspection_name="${output_name%.ipa}-inspection.txt"
inspection_path="$workspace/$inspection_name"
dsym_name="${output_name%.ipa}.dSYM.zip"
dsym_path_output="$workspace/$dsym_name"
derived_data_path="${RUNNER_TEMP:-$workspace/.tmp}/easyopen-derived-data"

mkdir -p "$archive_root" "$payload_dir"
printf '%s\n' "Android display version: $full_name"
printf '%s\n' "iOS CFBundleShortVersionString: $ios_version"
printf '%s\n' "iOS CFBundleVersion: $version_code"

bash "$workspace/.github/scripts/test-ios-icon-assets.sh"

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
  CURRENT_PROJECT_VERSION="$version_code" \
  DEBUG_INFORMATION_FORMAT=dwarf-with-dsym \
  GCC_GENERATE_DEBUGGING_SYMBOLS=YES
archive_seconds=$((SECONDS - archive_started))
package_started=$SECONDS
printf '%s\n' "Timing Xcode archive: ${archive_seconds}s"

app_path="$(find "$archive_path/Products/Applications" -maxdepth 1 -type d -name '*.app' -print -quit)"
test -n "$app_path"
test -d "$app_path"

dsym_path="$(find "$archive_path/dSYMs" -maxdepth 1 -type d -name '*.dSYM' -print -quit 2>/dev/null || true)"
dsym_uuid=""
if [[ -n "$dsym_path" && -d "$dsym_path" ]]; then
  /usr/bin/ditto -c -k --sequesterRsrc --keepParent "$dsym_path" "$dsym_path_output"
  test -s "$dsym_path_output"
  dsym_uuid="$(/usr/bin/dwarfdump --uuid "$dsym_path" 2>/dev/null || true)"
else
  printf '%s\n' "Warning: Xcode archive did not produce a dSYM; IPA build will continue without symbol artifact." >&2
  dsym_name=""
fi

# Compose Multiplatform resources are not part of a static Kotlin/Native
# framework's executable. This repository drives xcodebuild from a minimal
# hand-written project, so copy the exact iOS aggregate produced by the same
# Gradle build into the archived app. Do not invoke the plugin's Xcode sync
# task here: outside an Xcode-managed Gradle invocation it has no outputDir,
# and copying a stale/incomplete CVR can make stringResource() return mixed or
# garbled text after a relaunch.
compose_resources_source="$workspace/shared/build/kotlin-multiplatform-resources/aggregated-resources/iosArm64/composeResources"
compose_resources_source_file="$compose_resources_source/easyopen.shared.generated.resources/values/strings.commonMain.cvr"
compose_resources_source_xml="$workspace/shared/src/commonMain/composeResources/values/strings.xml"
./gradlew --no-daemon --max-workers=2 --build-cache --rerun-tasks \
  -PallowUnsigned=true \
  :shared:iosArm64AggregateResources
test -d "$compose_resources_source"
test -f "$compose_resources_source_file"
bash "$workspace/.github/scripts/test-ios-compose-resources.sh" \
  --resource-file "$compose_resources_source_file" \
  --source-xml "$compose_resources_source_xml"

compose_resources_dir="$app_path/compose-resources"
mkdir -p "$compose_resources_dir"
cp -R "$compose_resources_source/." "$compose_resources_dir/"
test -d "$compose_resources_dir/composeResources"
final_compose_resource_file="$compose_resources_dir/composeResources/easyopen.shared.generated.resources/values/strings.commonMain.cvr"
test -f "$final_compose_resource_file"
bash "$workspace/.github/scripts/test-ios-compose-resources.sh" \
  --resource-file "$final_compose_resource_file" \
  --source-xml "$compose_resources_source_xml"
compose_resource_files="$(find "$compose_resources_dir" -type f -print | wc -l | tr -d '[:space:]')"
test "$compose_resource_files" -gt 0
compose_resource_records="$(grep -c '^string|' "$final_compose_resource_file")"
compose_resource_sha256="$(/usr/bin/shasum -a 256 "$final_compose_resource_file" | awk '{print $1}')"
compose_source_xml_sha256="$(/usr/bin/shasum -a 256 "$compose_resources_source_xml" | awk '{print $1}')"
test "$compose_resource_records" -gt 0
test -n "$compose_resource_sha256"
test -n "$compose_source_xml_sha256"

actual_ios_version="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleShortVersionString' "$app_path/Info.plist")"
actual_version_code="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleVersion' "$app_path/Info.plist")"
actual_icon_name="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleIconName' "$app_path/Info.plist")"
camera_usage_description="$(/usr/libexec/PlistBuddy -c 'Print :NSCameraUsageDescription' "$app_path/Info.plist" 2>/dev/null || true)"
nfc_usage_description="$(/usr/libexec/PlistBuddy -c 'Print :NFCReaderUsageDescription' "$app_path/Info.plist" 2>/dev/null || true)"
disable_minimum_frame_duration="$(/usr/libexec/PlistBuddy -c 'Print :CADisableMinimumFrameDurationOnPhone' "$app_path/Info.plist")"
launch_screen_type="$(/usr/libexec/PlistBuddy -c 'Print :UILaunchScreen' "$app_path/Info.plist" 2>/dev/null || true)"
test "$actual_ios_version" = "$ios_version"
test "$actual_version_code" = "$version_code"
test "$actual_icon_name" = "AppIcon"
test -n "$camera_usage_description"
test -n "$nfc_usage_description"
test "$disable_minimum_frame_duration" = "true"
[[ "$launch_screen_type" == Dict* ]]
if /usr/libexec/PlistBuddy -c 'Print :UILaunchStoryboardName' "$app_path/Info.plist" >/dev/null 2>&1; then
  printf 'Legacy UILaunchStoryboardName must not be present when using UILaunchScreen.\n' >&2
  exit 1
fi

app_executable_name="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleExecutable' "$app_path/Info.plist")"
app_executable="$app_path/$app_executable_name"
test -f "$app_executable"
app_uuid="$(/usr/bin/dwarfdump --uuid "$app_executable" 2>/dev/null || true)"
test -n "$app_uuid"
test -f "$app_path/Assets.car"
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
  printf 'CFBundleIconName=%s\n' "$actual_icon_name"
  printf 'NSCameraUsageDescription=true\n'
  printf 'NFCReaderUsageDescription=true\n'
  printf 'Assets.car=true\n'
  printf 'CADisableMinimumFrameDurationOnPhone=%s\n' "$disable_minimum_frame_duration"
  printf 'UILaunchScreen=%s\n' "$launch_screen_type"
  printf 'app_architectures=%s\n' "$app_architectures"
  printf 'framework_architectures=%s\n' "$framework_architectures"
  printf 'app_uuid=%s\n' "$app_uuid"
  printf 'dsym_uuid=%s\n' "$dsym_uuid"
  printf 'dsym_name=%s\n' "$dsym_name"
  printf 'unsigned=true\n'
  printf 'compose_resources_dir=%s\n' "${compose_resources_dir#"$app_path/"}"
  printf 'compose_resource_files=%s\n' "$compose_resource_files"
  printf 'compose_resource_records=%s\n' "$compose_resource_records"
  printf 'compose_resource_sha256=%s\n' "$compose_resource_sha256"
  printf 'compose_source_xml_sha256=%s\n' "$compose_source_xml_sha256"
  printf '\nlinked_libraries:\n'
  /usr/bin/otool -L "$app_executable"
} > "$inspection_path"

cp -R "$app_path" "$payload_dir/"
(cd "$payload_root" && /usr/bin/zip -qry "$output_path" Payload)
test -s "$output_path"
/usr/bin/unzip -tq "$output_path" >/dev/null

# Verify the bytes from the actual IPA, not only the pre-zip archive. This is
# the final guard against an incomplete/stale CVR being shipped in the artifact
# that gets signed and installed on a device.
ipa_check_root="$payload_root/ipa-check"
mkdir -p "$ipa_check_root"
/usr/bin/unzip -q "$output_path" -d "$ipa_check_root"
ipa_app_path="$(find "$ipa_check_root/Payload" -maxdepth 1 -type d -name '*.app' -print -quit)"
test -n "$ipa_app_path"
final_ipa_resource_file="$ipa_app_path/compose-resources/composeResources/easyopen.shared.generated.resources/values/strings.commonMain.cvr"
test -f "$final_ipa_resource_file"
bash "$workspace/.github/scripts/test-ios-compose-resources.sh" \
  --resource-file "$final_ipa_resource_file" \
  --source-xml "$compose_resources_source_xml"
final_ipa_ios_version="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleShortVersionString' "$ipa_app_path/Info.plist")"
final_ipa_version_code="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleVersion' "$ipa_app_path/Info.plist")"
final_ipa_camera_usage="$(/usr/libexec/PlistBuddy -c 'Print :NSCameraUsageDescription' "$ipa_app_path/Info.plist" 2>/dev/null || true)"
final_ipa_nfc_usage="$(/usr/libexec/PlistBuddy -c 'Print :NFCReaderUsageDescription' "$ipa_app_path/Info.plist" 2>/dev/null || true)"
test "$final_ipa_ios_version" = "$ios_version"
test "$final_ipa_version_code" = "$version_code"
test -n "$final_ipa_camera_usage"
test -n "$final_ipa_nfc_usage"
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
  printf 'dsym_name=%s\n' "$dsym_name" >> "$GITHUB_OUTPUT"
fi
