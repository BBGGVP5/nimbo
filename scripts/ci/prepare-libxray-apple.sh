#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
LIBXRAY_VERSION="${LIBXRAY_VERSION:-26.7.28}"
LIBXRAY_SHA256="${LIBXRAY_SHA256:-07f7ed7697277930e1c517755855950f594f41435b0dfc5917a66eea6278aeb9}"
ARCHIVE_URL="https://github.com/XTLS/libXray/releases/download/v${LIBXRAY_VERSION}/libxray-apple-cgo.zip"
CACHE_DIR="${ROOT_DIR}/.build-dependencies/libxray-apple/v${LIBXRAY_VERSION}"
ARCHIVE_PATH="${CACHE_DIR}/libxray-apple-cgo.zip"
EXTRACT_DIR="${CACHE_DIR}/extract"
DESTINATION="${ROOT_DIR}/iosApp/Vendor/LibXray.xcframework"

mkdir -p "${CACHE_DIR}" "$(dirname "${DESTINATION}")"

if [[ ! -f "${ARCHIVE_PATH}" ]] || \
   [[ "$(shasum -a 256 "${ARCHIVE_PATH}" | awk '{print $1}')" != "${LIBXRAY_SHA256}" ]]; then
  rm -f "${ARCHIVE_PATH}"
  curl --fail --location --retry 3 --output "${ARCHIVE_PATH}" "${ARCHIVE_URL}"
fi

ACTUAL_SHA256="$(shasum -a 256 "${ARCHIVE_PATH}" | awk '{print $1}')"
if [[ "${ACTUAL_SHA256}" != "${LIBXRAY_SHA256}" ]]; then
  echo "libXray checksum mismatch: ${ACTUAL_SHA256}" >&2
  exit 20
fi

rm -rf "${EXTRACT_DIR}"
mkdir -p "${EXTRACT_DIR}"
ditto -x -k "${ARCHIVE_PATH}" "${EXTRACT_DIR}"

SOURCE_FRAMEWORK="$(find "${EXTRACT_DIR}" -type d -name LibXray.xcframework -print -quit)"
if [[ -z "${SOURCE_FRAMEWORK}" ]]; then
  echo "LibXray.xcframework is missing from the verified archive" >&2
  exit 21
fi

if [[ ! -f "${SOURCE_FRAMEWORK}/ios-arm64/libXray.a" ]] || \
   [[ ! -f "${SOURCE_FRAMEWORK}/ios-arm64/Headers/libXray.h" ]]; then
  echo "Verified libXray archive does not contain the iOS device slice" >&2
  exit 22
fi

rm -rf "${DESTINATION}"
ditto "${SOURCE_FRAMEWORK}" "${DESTINATION}"
cat > "${ROOT_DIR}/iosApp/Vendor/libxray-build-info.txt" <<INFO
version=${LIBXRAY_VERSION}
sha256=${LIBXRAY_SHA256}
source=${ARCHIVE_URL}
INFO

echo "Prepared official LibXray.xcframework v${LIBXRAY_VERSION} (${LIBXRAY_SHA256})"
