#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
VERSION="${NIMBO_VERSION:-1.2.0-beta.2}"
MARKETING_VERSION="${VERSION%%-*}"
BUILD_NUMBER="${NIMBO_BUILD_NUMBER:-12}"
APP_BUNDLE_ID="${NIMBO_APP_BUNDLE_ID:-com.nimbo.resignable}"
TUNNEL_BUNDLE_ID="${NIMBO_PACKET_TUNNEL_BUNDLE_ID:-${APP_BUNDLE_ID}.PacketTunnel}"
ARTIFACT_DIR="${ROOT_DIR}/artifacts/ios"
DERIVED_DATA="${ROOT_DIR}/iosApp/build/DerivedData"
PRODUCTS_DIR="${DERIVED_DATA}/Build/Products/Release-iphoneos"
APP_PATH="${PRODUCTS_DIR}/Nimbo.app"
OUTPUT_NAME="Nimbo_v${VERSION}_ios_resignable.ipa"
OUTPUT_PATH="${ARTIFACT_DIR}/${OUTPUT_NAME}"

cd "${ROOT_DIR}"
mkdir -p "${ARTIFACT_DIR}"
rm -rf "${DERIVED_DATA}" "${ROOT_DIR}/iosApp/Nimbo.xcodeproj"

chmod +x ./gradlew
./gradlew --no-daemon -PnimboIosOnly=true :shared:linkReleaseFrameworkIosArm64

chmod +x "${ROOT_DIR}/scripts/ci/prepare-libxray-apple.sh"
"${ROOT_DIR}/scripts/ci/prepare-libxray-apple.sh"

PACKET_TUNNEL_RESOURCES="${ROOT_DIR}/iosApp/build/PacketTunnelResources"
if [[ -d "${ROOT_DIR}/app/src/main/assets" ]]; then
  ANDROID_ASSETS_DIR="${ROOT_DIR}/app/src/main/assets"
elif [[ -d "${ROOT_DIR}/apps/android/app/src/main/assets" ]]; then
  ANDROID_ASSETS_DIR="${ROOT_DIR}/apps/android/app/src/main/assets"
else
  echo "Android geo database assets were not found" >&2
  exit 4
fi
rm -rf "${PACKET_TUNNEL_RESOURCES}"
mkdir -p "${PACKET_TUNNEL_RESOURCES}"
cp "${ANDROID_ASSETS_DIR}/geoip.dat" "${PACKET_TUNNEL_RESOURCES}/geoip.dat"
cp "${ANDROID_ASSETS_DIR}/geosite.dat" "${PACKET_TUNNEL_RESOURCES}/geosite.dat"

SHARED_FRAMEWORK="${ROOT_DIR}/shared/build/bin/iosArm64/releaseFramework/NimboShared.framework"
SHARED_XCFRAMEWORK="${ROOT_DIR}/shared/build/XCFrameworks/release/NimboShared.xcframework"
if [[ ! -d "${SHARED_FRAMEWORK}" ]]; then
  echo "NimboShared device framework was not produced" >&2
  exit 2
fi
rm -rf "${SHARED_XCFRAMEWORK}"
mkdir -p "$(dirname "${SHARED_XCFRAMEWORK}")"
xcodebuild -create-xcframework \
  -framework "${SHARED_FRAMEWORK}" \
  -output "${SHARED_XCFRAMEWORK}"

if ! command -v xcodegen >/dev/null 2>&1; then
  echo "xcodegen is required" >&2
  exit 3
fi

(
  cd iosApp
  xcodegen generate --spec project.yml
)

xcodebuild \
  -project "${ROOT_DIR}/iosApp/Nimbo.xcodeproj" \
  -scheme Nimbo \
  -configuration Release \
  -sdk iphoneos \
  -destination 'generic/platform=iOS' \
  -derivedDataPath "${DERIVED_DATA}" \
  NIMBO_APP_BUNDLE_ID="${APP_BUNDLE_ID}" \
  NIMBO_PACKET_TUNNEL_BUNDLE_ID="${TUNNEL_BUNDLE_ID}" \
  NIMBO_DISPLAY_VERSION="${VERSION}" \
  MARKETING_VERSION="${MARKETING_VERSION}" \
  CURRENT_PROJECT_VERSION="${BUILD_NUMBER}" \
  CODE_SIGNING_ALLOWED=NO \
  CODE_SIGNING_REQUIRED=NO \
  CODE_SIGN_IDENTITY='' \
  DEVELOPMENT_TEAM='' \
  build

if [[ ! -d "${APP_PATH}" ]]; then
  echo "Nimbo.app was not produced at ${APP_PATH}" >&2
  exit 3
fi

TUNNEL_PATH="${APP_PATH}/PlugIns/NimboPacketTunnel.appex"
if [[ ! -d "${TUNNEL_PATH}" ]]; then
  echo "Packet Tunnel extension is missing from the app bundle" >&2
  exit 4
fi

for resource in geoip.dat geosite.dat; do
  if [[ ! -f "${TUNNEL_PATH}/${resource}" ]]; then
    echo "Packet Tunnel resource ${resource} is missing from the app bundle" >&2
    exit 5
  fi
done

read_plist() {
  /usr/libexec/PlistBuddy -c "Print :$2" "$1"
}

assert_plist() {
  local plist="$1"
  local key="$2"
  local expected="$3"
  local actual
  actual="$(read_plist "${plist}" "${key}")"
  if [[ "${actual}" != "${expected}" ]]; then
    echo "Unexpected ${key} in ${plist}: ${actual} (expected ${expected})" >&2
    exit 6
  fi
}

assert_plist "${APP_PATH}/Info.plist" "CFBundleIdentifier" "${APP_BUNDLE_ID}"
assert_plist "${APP_PATH}/Info.plist" "CFBundleShortVersionString" "${MARKETING_VERSION}"
assert_plist "${APP_PATH}/Info.plist" "CFBundleVersion" "${BUILD_NUMBER}"
assert_plist "${APP_PATH}/Info.plist" "CADisableMinimumFrameDurationOnPhone" "true"
assert_plist "${APP_PATH}/Info.plist" "NimboDisplayVersion" "${VERSION}"
assert_plist "${APP_PATH}/Info.plist" "NimboPacketTunnelBundleIdentifier" "${TUNNEL_BUNDLE_ID}"
assert_plist "${TUNNEL_PATH}/Info.plist" "CFBundleIdentifier" "${TUNNEL_BUNDLE_ID}"
assert_plist "${TUNNEL_PATH}/Info.plist" "NSExtension:NSExtensionPointIdentifier" "com.apple.networkextension.packet-tunnel"
assert_plist "${TUNNEL_PATH}/Info.plist" "NSExtension:NSExtensionPrincipalClass" "NimboPacketTunnel.PacketTunnelProvider"

# TrollStore and other private installers need the Packet Tunnel entitlement to
# remain attached to both Mach-O executables. A byte-for-byte unsigned bundle
# loses that contract and NetworkExtension returns `permission denied`. Ad-hoc
# signing uses no Apple certificate or provisioning profile, remains re-signable,
# and preserves the entitlement for TrollStore installations.
find "${APP_PATH}" -type d -name _CodeSignature -prune -exec rm -rf {} +
find "${APP_PATH}" -name embedded.mobileprovision -delete

while IFS= read -r framework; do
  codesign --force --sign - --timestamp=none "${framework}"
done < <(find "${APP_PATH}" -type d -name '*.framework' -print)

while IFS= read -r library; do
  codesign --force --sign - --timestamp=none "${library}"
done < <(find "${APP_PATH}" -type f -name '*.dylib' -print)

codesign --force --sign - --timestamp=none --generate-entitlement-der \
  --entitlements "${ROOT_DIR}/iosApp/PacketTunnel/PacketTunnel.entitlements" \
  "${TUNNEL_PATH}"
codesign --force --sign - --timestamp=none --generate-entitlement-der \
  --entitlements "${ROOT_DIR}/iosApp/Nimbo/Nimbo.entitlements" \
  "${APP_PATH}"

codesign --verify --deep --strict "${APP_PATH}"
for signed_bundle in "${APP_PATH}" "${TUNNEL_PATH}"; do
  entitlements="$(codesign -d --entitlements :- "${signed_bundle}" 2>/dev/null)"
  if [[ "${entitlements}" != *"packet-tunnel-provider"* ]]; then
    echo "Packet Tunnel entitlement is missing from ${signed_bundle}" >&2
    exit 7
  fi
done

PACKAGE_DIR="$(mktemp -d)"
trap 'rm -rf "${PACKAGE_DIR}"' EXIT
mkdir -p "${PACKAGE_DIR}/Payload"
ditto "${APP_PATH}" "${PACKAGE_DIR}/Payload/Nimbo.app"
(
  cd "${PACKAGE_DIR}"
  /usr/bin/zip -qry "${OUTPUT_PATH}" Payload
)

shasum -a 256 "${OUTPUT_PATH}" > "${OUTPUT_PATH}.sha256"

cat > "${ARTIFACT_DIR}/build-manifest.txt" <<MANIFEST
name=${OUTPUT_NAME}
version=${VERSION}
main_bundle_id=${APP_BUNDLE_ID}
packet_tunnel_bundle_id=${TUNNEL_BUNDLE_ID}
signed=adhoc
apple_certificate=false
apple_provisioning_profile=false
contains_packet_tunnel=true
requires_resigning=true
requires_network_extension_entitlement=true
resignable=true
libxray_version=26.7.28
libxray_sha256=07f7ed7697277930e1c517755855950f594f41435b0dfc5917a66eea6278aeb9
MANIFEST

echo "Built ${OUTPUT_PATH}"
