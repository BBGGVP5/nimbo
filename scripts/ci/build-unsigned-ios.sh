#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
VERSION="${NIMBO_VERSION:-1.2.0-beta.3}"
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
if command -v ldid2 >/dev/null 2>&1; then
  LDID_BIN="$(command -v ldid2)"
elif command -v ldid >/dev/null 2>&1; then
  LDID_BIN="$(command -v ldid)"
else
  LDID_BIN=""
fi
if [[ -z "${LDID_BIN}" ]]; then
  echo "ldid-procursus is required to preserve Network Extension entitlements" >&2
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
WIDGET_PATH="${APP_PATH}/PlugIns/NimboControlWidget.appex"
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
# loses that contract and NetworkExtension returns `permission denied`.
#
# Xcode 26's codesign strips this restricted entitlement from an ad-hoc signed
# containing app even when it is supplied explicitly. ldid writes the requested
# entitlement into each executable without an Apple certificate or provisioning
# profile. The result remains suitable for TrollStore and for later re-signing.
if [[ -z "${LDID_BIN}" ]]; then
  echo "ldid-procursus is required to build the re-signable IPA" >&2
  exit 7
fi

LDID_BANNER="$("${LDID_BIN}" 2>&1 | head -n 5 || true)"
if ! printf '%s\n' "${LDID_BANNER}" | grep -qi 'procursus'; then
  echo "The installed ldid is not ldid-procursus and cannot safely embed the Packet Tunnel entitlement" >&2
  printf '%s\n' "${LDID_BANNER}" >&2
  exit 7
fi

find "${APP_PATH}" -type d -name _CodeSignature -prune -exec rm -rf {} +
find "${APP_PATH}" -name embedded.mobileprovision -delete

while IFS= read -r framework; do
  codesign --force --sign - --timestamp=none "${framework}"
done < <(find "${APP_PATH}" -type d -name '*.framework' -print)

while IFS= read -r library; do
  codesign --force --sign - --timestamp=none "${library}"
done < <(find "${APP_PATH}" -type f -name '*.dylib' -print)

APP_EXECUTABLE="${APP_PATH}/$(read_plist "${APP_PATH}/Info.plist" "CFBundleExecutable")"
TUNNEL_EXECUTABLE="${TUNNEL_PATH}/$(read_plist "${TUNNEL_PATH}/Info.plist" "CFBundleExecutable")"
# Элемент Пункта управления собирается только на новых Xcode: если его нет,
# сборка не должна падать — кнопка приятная, но не обязательная.
WIDGET_EXECUTABLE=""
# Виджет кладётся в сборку только по просьбе: пока не проверено на
# устройстве, что он не мешает запуску туннеля, рабочее подключение важнее
# кнопки в шторке.
if [[ -d "${WIDGET_PATH}" && "${NIMBO_WITH_CONTROL_WIDGET:-0}" != "1" ]]; then
  echo "Убираю виджет Пункта управления из сборки (NIMBO_WITH_CONTROL_WIDGET=1 вернёт его)"
  rm -rf "${WIDGET_PATH}"
fi
if [[ -d "${WIDGET_PATH}" ]]; then
  WIDGET_EXECUTABLE="${WIDGET_PATH}/$(read_plist "${WIDGET_PATH}/Info.plist" "CFBundleExecutable")"
fi

prepare_entitlements() {
  local source_plist="$1"
  local output_plist="$2"
  # Третьим аргументом — нужно ли право туннеля. У виджета его нет и быть
  # не должно: он лишь переключает уже настроенный туннель.
  local require_tunnel="${3:-1}"
  # Keep the source XML byte-for-byte. On the Xcode 26 runner `plutil
  # -convert ... -o` produced a syntactically valid but empty dictionary for
  # entitlement files, which made NetworkExtension fail with permission denied.
  cp "${source_plist}" "${output_plist}"
  plutil -lint "${output_plist}"
  # PlistBuddy treats the dots in the entitlement name as a nested key path on
  # recent macOS runners. Read the plist as data instead, otherwise a valid
  # `com.apple.developer.networking.networkextension` array is reported empty.
  if [[ "${require_tunnel}" != "1" ]]; then
    plutil -p "${output_plist}"
    return 0
  fi
  if ! /usr/bin/python3 - "${output_plist}" <<'PY'
import plistlib
import sys

with open(sys.argv[1], "rb") as plist_file:
    values = plistlib.load(plist_file).get(
        "com.apple.developer.networking.networkextension", []
    )
raise SystemExit(0 if "packet-tunnel-provider" in values else 1)
PY
  then
    echo "Packet Tunnel entitlement source is invalid: ${source_plist}" >&2
    plutil -p "${output_plist}" >&2 || true
    exit 7
  fi
  plutil -p "${output_plist}"
}

LDID_ENTITLEMENTS_DIR="$(mktemp -d)"
APP_ENTITLEMENTS="${LDID_ENTITLEMENTS_DIR}/Nimbo.entitlements"
TUNNEL_ENTITLEMENTS="${LDID_ENTITLEMENTS_DIR}/PacketTunnel.entitlements"
prepare_entitlements "${ROOT_DIR}/iosApp/Nimbo/Nimbo.entitlements" "${APP_ENTITLEMENTS}"
prepare_entitlements "${ROOT_DIR}/iosApp/PacketTunnel/PacketTunnel.entitlements" "${TUNNEL_ENTITLEMENTS}"
WIDGET_ENTITLEMENTS="${LDID_ENTITLEMENTS_DIR}/ControlWidget.entitlements"
if [[ -n "${WIDGET_EXECUTABLE}" ]]; then
  prepare_entitlements "${ROOT_DIR}/iosApp/ControlWidget/NimboControlWidget.entitlements" "${WIDGET_ENTITLEMENTS}" 0
fi

echo "Embedding Network Extension entitlements with ${LDID_BIN}"
# Remove any empty ad-hoc signature emitted by Xcode before ldid writes the
# explicit entitlement blob. Re-signers will replace these signatures later.
codesign --remove-signature "${TUNNEL_EXECUTABLE}" 2>/dev/null || true
codesign --remove-signature "${APP_EXECUTABLE}" 2>/dev/null || true
"${LDID_BIN}" -S"${TUNNEL_ENTITLEMENTS}" "${TUNNEL_EXECUTABLE}"
"${LDID_BIN}" -S"${APP_ENTITLEMENTS}" "${APP_EXECUTABLE}"
if [[ -n "${WIDGET_EXECUTABLE}" ]]; then
  codesign --remove-signature "${WIDGET_EXECUTABLE}" 2>/dev/null || true
  "${LDID_BIN}" -S"${WIDGET_ENTITLEMENTS}" "${WIDGET_EXECUTABLE}"
fi

ENTITLEMENTS_REPORT_DIR="${ARTIFACT_DIR}/codesign-entitlements"
rm -rf "${ENTITLEMENTS_REPORT_DIR}"
mkdir -p "${ENTITLEMENTS_REPORT_DIR}"
SIGNED_EXECUTABLES=("${APP_EXECUTABLE}" "${TUNNEL_EXECUTABLE}")
if [[ -n "${WIDGET_EXECUTABLE}" ]]; then
  SIGNED_EXECUTABLES+=("${WIDGET_EXECUTABLE}")
fi
for signed_executable in "${SIGNED_EXECUTABLES[@]}"; do
  bundle_name="$(basename "${signed_executable}")"
  report_path="${ENTITLEMENTS_REPORT_DIR}/${bundle_name}.plist"
  # ldid's entitlement blob is intentionally verified by ldid itself. Recent
  # macOS `codesign` versions report ldid fake signatures as "no signature" or
  # "invalid entitlements blob" even though ldid/TrollStore can read and
  # preserve the entitlement correctly while installing the bundle.
  entitlements="$("${LDID_BIN}" -e "${signed_executable}" 2>&1)"
  printf '%s\n' "${entitlements}" > "${report_path}"
  if [[ "${entitlements}" != *"packet-tunnel-provider"* ]]; then
    echo "Packet Tunnel entitlement is missing from ${signed_executable}" >&2
    sed -n '1,120p' "${report_path}" >&2
    exit 7
  fi
done

rm -rf "${LDID_ENTITLEMENTS_DIR}"

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
signed=adhoc-ldid
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
