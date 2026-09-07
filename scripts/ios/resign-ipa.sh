#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "Usage: $0 Nimbo_*_ios_unsigned.ipa" >&2
  exit 1
fi

: "${NIMBO_SIGNING_IDENTITY:?Set NIMBO_SIGNING_IDENTITY}"
: "${NIMBO_APP_BUNDLE_ID:?Set NIMBO_APP_BUNDLE_ID}"
: "${NIMBO_APP_PROFILE:?Set NIMBO_APP_PROFILE to the main app .mobileprovision}"
: "${NIMBO_TUNNEL_PROFILE:?Set NIMBO_TUNNEL_PROFILE to the Packet Tunnel .mobileprovision}"

IPA_PATH="$(cd "$(dirname "$1")" && pwd)/$(basename "$1")"
TUNNEL_BUNDLE_ID="${NIMBO_PACKET_TUNNEL_BUNDLE_ID:-${NIMBO_APP_BUNDLE_ID}.PacketTunnel}"
OUTPUT_PATH="${NIMBO_OUTPUT_IPA:-${IPA_PATH%_unsigned.ipa}_resigned.ipa}"
WORK_DIR="$(mktemp -d)"
trap 'rm -rf "${WORK_DIR}"' EXIT

unzip -q "${IPA_PATH}" -d "${WORK_DIR}"
APP_PATH="${WORK_DIR}/Payload/Nimbo.app"
TUNNEL_PATH="${APP_PATH}/PlugIns/NimboPacketTunnel.appex"

[[ -d "${APP_PATH}" ]] || { echo "Nimbo.app is missing" >&2; exit 2; }
[[ -d "${TUNNEL_PATH}" ]] || { echo "NimboPacketTunnel.appex is missing" >&2; exit 3; }

PLIST_BUDDY=/usr/libexec/PlistBuddy
"${PLIST_BUDDY}" -c "Set :CFBundleIdentifier ${NIMBO_APP_BUNDLE_ID}" "${APP_PATH}/Info.plist"
"${PLIST_BUDDY}" -c "Set :NimboPacketTunnelBundleIdentifier ${TUNNEL_BUNDLE_ID}" "${APP_PATH}/Info.plist"
"${PLIST_BUDDY}" -c "Set :CFBundleIdentifier ${TUNNEL_BUNDLE_ID}" "${TUNNEL_PATH}/Info.plist"

# App Group lets the extension and the app share one diagnostics container, so
# tunnel failures show up in the report the user exports. Optional: without it
# each process keeps its own log and only the app side is visible. The group
# must exist in the signing team and be enabled on both App IDs, otherwise the
# profiles below will not carry it and installation fails.
set_app_group() {
  local plist="$1"
  "${PLIST_BUDDY}" -c "Set :NimboAppGroupIdentifier ${NIMBO_APP_GROUP}" "${plist}" 2>/dev/null ||
    "${PLIST_BUDDY}" -c "Add :NimboAppGroupIdentifier string ${NIMBO_APP_GROUP}" "${plist}"
}

if [[ -n "${NIMBO_APP_GROUP:-}" ]]; then
  set_app_group "${APP_PATH}/Info.plist"
  set_app_group "${TUNNEL_PATH}/Info.plist"
fi

cp "${NIMBO_APP_PROFILE}" "${APP_PATH}/embedded.mobileprovision"
cp "${NIMBO_TUNNEL_PROFILE}" "${TUNNEL_PATH}/embedded.mobileprovision"

security cms -D -i "${NIMBO_APP_PROFILE}" > "${WORK_DIR}/app-profile.plist"
security cms -D -i "${NIMBO_TUNNEL_PROFILE}" > "${WORK_DIR}/tunnel-profile.plist"
"${PLIST_BUDDY}" -x -c 'Print :Entitlements' "${WORK_DIR}/app-profile.plist" > "${WORK_DIR}/app-entitlements.plist"
"${PLIST_BUDDY}" -x -c 'Print :Entitlements' "${WORK_DIR}/tunnel-profile.plist" > "${WORK_DIR}/tunnel-entitlements.plist"

APP_PROFILE_ID="$("${PLIST_BUDDY}" -c 'Print :Entitlements:application-identifier' "${WORK_DIR}/app-profile.plist")"
TUNNEL_PROFILE_ID="$("${PLIST_BUDDY}" -c 'Print :Entitlements:application-identifier' "${WORK_DIR}/tunnel-profile.plist")"
[[ "${APP_PROFILE_ID}" == *."${NIMBO_APP_BUNDLE_ID}" ]] || { echo "Main profile does not match ${NIMBO_APP_BUNDLE_ID}" >&2; exit 4; }
[[ "${TUNNEL_PROFILE_ID}" == *."${TUNNEL_BUNDLE_ID}" ]] || { echo "Tunnel profile does not match ${TUNNEL_BUNDLE_ID}" >&2; exit 5; }

profile_has_packet_tunnel() {
  /usr/bin/python3 - "$1" <<'PY'
import plistlib
import sys

with open(sys.argv[1], "rb") as profile_file:
    entitlements = plistlib.load(profile_file).get("Entitlements", {})
values = entitlements.get("com.apple.developer.networking.networkextension", [])
raise SystemExit(0 if "packet-tunnel-provider" in values else 1)
PY
}

if ! profile_has_packet_tunnel "${WORK_DIR}/app-profile.plist"; then
  echo "Main app profile does not contain packet-tunnel-provider entitlement" >&2
  exit 6
fi

if ! profile_has_packet_tunnel "${WORK_DIR}/tunnel-profile.plist"; then
  echo "Tunnel profile does not contain packet-tunnel-provider entitlement" >&2
  exit 7
fi

find "${APP_PATH}" -type d -name _CodeSignature -prune -exec rm -rf {} +
if [[ -d "${APP_PATH}/Frameworks" ]]; then
  while IFS= read -r binary; do
    codesign --force --sign "${NIMBO_SIGNING_IDENTITY}" --timestamp=none "${binary}"
  done < <(find "${APP_PATH}/Frameworks" -type f -name '*.dylib' -print)
  while IFS= read -r framework; do
    codesign --force --sign "${NIMBO_SIGNING_IDENTITY}" --timestamp=none "${framework}"
  done < <(find "${APP_PATH}/Frameworks" -type d -name '*.framework' -print)
fi

codesign --force --sign "${NIMBO_SIGNING_IDENTITY}" --timestamp=none \
  --entitlements "${WORK_DIR}/tunnel-entitlements.plist" "${TUNNEL_PATH}"
codesign --force --sign "${NIMBO_SIGNING_IDENTITY}" --timestamp=none \
  --entitlements "${WORK_DIR}/app-entitlements.plist" "${APP_PATH}"

codesign --verify --deep --strict --verbose=2 "${APP_PATH}"
rm -f "${OUTPUT_PATH}"
(
  cd "${WORK_DIR}"
  /usr/bin/zip -qry "${OUTPUT_PATH}" Payload
)

echo "Created ${OUTPUT_PATH}"
