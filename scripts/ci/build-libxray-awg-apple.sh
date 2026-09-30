#!/usr/bin/env bash
set -euo pipefail

# Compile upstream API3, AWG, diagnostics and Mihomo V1 in ONE package main.
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
LIBXRAY_COMMIT=3c694b23290f9849fe52284a345ebd4343bc90cd
LIBXRAY_SOURCE_SHA256=0b9162518c1eb2aadca13f39e63e9c5f7c904c4843ad6bc8f8251f2d1f99c185
GO_VERSION=go1.27.1
AWG_VERSION=v3.1.20260828
BRIDGE_DIR="${ROOT_DIR}/iosApp/GoBridge"
AWG_DIR="${ROOT_DIR}/tools/native/awg-core"
MIHOMO_DIR="${ROOT_DIR}/tools/native/mihomo-core"
CACHE_DIR="${ROOT_DIR}/.build-dependencies/libxray-awg/${LIBXRAY_COMMIT}"
DESTINATION="${ROOT_DIR}/iosApp/Vendor/LibXray.xcframework"
ARCHIVE="${CACHE_DIR}/libxray-source.tar.gz"

[[ "$(uname -s)" == Darwin ]] || { echo 'Apple archives require macOS and Xcode' >&2; exit 20; }
export GOFLAGS='-tags=no_tailscale,no_zerotier,no_easytier'
export GOTOOLCHAIN=local GOWORK=off GOSUMDB=sum.golang.org
[[ "$(go env GOVERSION)" == "${GO_VERSION}" ]] || { echo "Use ${GO_VERSION}" >&2; exit 20; }
[[ -f "${AWG_DIR}/go.mod" ]] || { echo 'Shared AWG sources are missing' >&2; exit 20; }
mkdir -p "${CACHE_DIR}" "$(dirname "${DESTINATION}")"
if [[ ! -f "${ARCHIVE}" ]]; then
  curl --fail --location --retry 3 \
    "https://codeload.github.com/XTLS/libXray/tar.gz/${LIBXRAY_COMMIT}" -o "${ARCHIVE}"
fi
echo "${LIBXRAY_SOURCE_SHA256}  ${ARCHIVE}" | shasum -a 256 --check
WORK_DIR="$(mktemp -d "${CACHE_DIR}/build.XXXXXX")"
trap 'rm -rf "${WORK_DIR}"' EXIT
tar -xzf "${ARCHIVE}" -C "${WORK_DIR}"
SOURCE_DIR="${WORK_DIR}/libXray-${LIBXRAY_COMMIT}"
cp "${BRIDGE_DIR}/"*.go "${SOURCE_DIR}/cgo_bridge/"
cp "${BRIDGE_DIR}/go.mod" "${BRIDGE_DIR}/go.sum" "${SOURCE_DIR}/"
cd "${SOURCE_DIR}"
go mod edit "-replace=nimbo/awgcore=${AWG_DIR}"
python3 "${ROOT_DIR}/scripts/ci/prepare-mihomo-merged.py" \
  --source-dir "${SOURCE_DIR}" --dependency-dir "${WORK_DIR}/mihomo-dependencies"
go mod download
cmp go.sum "${BRIDGE_DIR}/go.sum"
# nimbo/awgcore is a repository module, not a public module-cache path. Go's
# verify command tries to find its ziphash despite the local replace. Verify
# the complete external graph separately, retaining all pinned AWG dependencies.
mkdir "${WORK_DIR}/verify"
cp go.mod go.sum "${WORK_DIR}/verify/"
(
  cd "${WORK_DIR}/verify"
  go mod edit -droprequire=nimbo/awgcore -dropreplace=nimbo/awgcore \
    -droprequire=nimbo/mihomocore -dropreplace=nimbo/mihomocore \
    -dropreplace=google.golang.org/protobuf \
    -dropreplace=github.com/metacubex/mihomo
  go mod verify
)
[[ "$(go list -m -f '{{.Version}}' github.com/amnezia-vpn/amneziawg-go/v3)" == "${AWG_VERSION}" ]]
[[ "$(go list -m -f '{{.Version}}' github.com/metacubex/mihomo)" == 'v1.19.31' ]]
go test -mod=readonly -count=1 nimbo/awgcore nimbo/mihomocore ./cgo_bridge

# Execute the production request contract against the same merged C bridge on
# the macOS host. This temporary library contains both engines in one Go build;
# it is never shipped. The Apple archives below each still contain one runtime.
go build -mod=readonly -trimpath -buildvcs=false -buildmode=c-shared \
  -o "${WORK_DIR}/libXray-contract.dylib" ./cgo_bridge
python3 "${ROOT_DIR}/scripts/ci/test-libxray-cabi.py" "${WORK_DIR}/libXray-contract.dylib"
python3 "${ROOT_DIR}/scripts/ci/test-libxray-mihomo-cabi.py" "${WORK_DIR}/libXray-contract.dylib"

build_slice() {
  local sdk="$1" go_arch="$2" apple_arch="$3" target="$4"
  local out="${WORK_DIR}/${sdk}-${apple_arch}"
  local sdk_path
  sdk_path="$(xcrun --sdk "${sdk}" --show-sdk-path)"
  local flags="-isysroot ${sdk_path} -target ${target}"
  mkdir -p "${out}/Headers"
  env GOOS=ios GOARCH="${go_arch}" CGO_ENABLED=1 \
    CC="$(xcrun --sdk "${sdk}" --find clang)" \
    CXX="$(xcrun --sdk "${sdk}" --find clang++)" \
    CGO_CFLAGS="${flags}" CGO_CXXFLAGS="${flags}" CGO_LDFLAGS="${flags}" \
    go build -mod=readonly -tags=ios,no_tailscale,no_zerotier,no_easytier -trimpath -buildvcs=false \
      -ldflags='-s -w -buildid=' -buildmode=c-archive -o "${out}/libXray.a" ./cgo_bridge
  cp "${out}/libXray.h" "${out}/Headers/"
  cp build/template/module.modulemap "${out}/Headers/"
  nm -gU "${out}/libXray.a" > "${out}/symbols.txt"
  for symbol in CGoInvoke CGoFree NimboAWGStart NimboAWGStop NimboAWGStats NimboDiagnosticRun NimboDiagnosticCancel NimboMihomoInvokeV1 NimboMihomoCancelV1 NimboMihomoSetSocketProtectorV1 NimboMihomoStartIOSV1 NimboMihomoFreeV1; do
    grep -q " _${symbol}$" "${out}/symbols.txt" || { echo "Missing ${symbol} in ${sdk}/${apple_arch} archive" >&2; exit 21; }
    grep -q "${symbol}(" "${out}/Headers/libXray.h" || { echo "Missing ${symbol} in generated C header" >&2; exit 21; }
  done
  # Compile and link the production Swift bridge against this real C archive.
  # This catches header/module/link errors before the much larger IPA build.
  # The temporary dylib is a link check only and is never shipped or executed.
  xcrun --sdk "${sdk}" swiftc -sdk "${sdk_path}" -target "${target}" \
    -swift-version 5 -application-extension -emit-library \
    -module-name NimboAWGLinkCheck -I "${out}/Headers" \
    "${ROOT_DIR}/iosApp/Shared/NimboAWGConfiguration.swift" \
    "${ROOT_DIR}/iosApp/PacketTunnel/AmneziaWGBridge.swift" \
    "${out}/libXray.a" -lresolv -framework Security -framework CoreFoundation \
    -o "${out}/NimboAWGLinkCheck.dylib"
}
build_slice iphoneos arm64 arm64 arm64-apple-ios16.0
build_slice iphonesimulator arm64 arm64 arm64-apple-ios16.0-simulator
build_slice iphonesimulator amd64 x86_64 x86_64-apple-ios16.0-simulator
mkdir -p "${WORK_DIR}/simulator"
lipo -create "${WORK_DIR}/iphonesimulator-arm64/libXray.a" \
  "${WORK_DIR}/iphonesimulator-x86_64/libXray.a" -output "${WORK_DIR}/simulator/libXray.a"
xcodebuild -create-xcframework \
  -library "${WORK_DIR}/iphoneos-arm64/libXray.a" -headers "${WORK_DIR}/iphoneos-arm64/Headers" \
  -library "${WORK_DIR}/simulator/libXray.a" -headers "${WORK_DIR}/iphonesimulator-arm64/Headers" \
  -output "${WORK_DIR}/LibXray.xcframework"
rm -rf "${DESTINATION}"
ditto "${WORK_DIR}/LibXray.xcframework" "${DESTINATION}"
{
  echo 'libxray_version=26.9.30'
  echo "libxray_commit=${LIBXRAY_COMMIT}"
  echo "libxray_source_sha256=${LIBXRAY_SOURCE_SHA256}"
  echo "awg_version=${AWG_VERSION}"
  echo "go_version=${GO_VERSION}"
  echo 'go_runtime_archives_per_slice=1'
  echo 'native_api3_awg_contract_test=passed'
  echo 'native_mihomo_v1_contract_test=passed'
  echo 'mihomo_version=v1.19.31'
  echo 'mihomo_ios_tun=unavailable'
  echo 'swift_awg_link_check=iphoneos-arm64,iphonesimulator-arm64,iphonesimulator-x86_64'
  shasum -a 256 "${BRIDGE_DIR}/"*.go "${BRIDGE_DIR}/go.mod" "${BRIDGE_DIR}/go.sum"
  find "${AWG_DIR}" -type f \( -name '*.go' -o -name go.mod -o -name go.sum \) -print | LC_ALL=C sort | while IFS= read -r file; do
    shasum -a 256 "${file}"
  done
} > "${ROOT_DIR}/iosApp/Vendor/libxray-build-info.txt"
cp "${WORK_DIR}/mihomo-dependencies/mihomo-source-verification.json" \
  "${ROOT_DIR}/iosApp/Vendor/mihomo-source-verification.json"
echo "Prepared single-runtime LibXray 26.9.30 / AWG ${AWG_VERSION} / Mihomo V1 bridge; iOS Mihomo TUN remains unavailable"
