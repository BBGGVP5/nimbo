#!/usr/bin/env bash
set -euo pipefail

# Run on macOS, then transfer the archive to Windows/WSL. No app build/signing.
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
EXPORT_DIR="${NIMBO_XTOOL_EXPORT_DIR:-${ROOT_DIR}/artifacts/ios-xtool-dependencies}"
VERSION="${NIMBO_VERSION:-1.3.0-beta.2}"
BUILD_NUMBER="${NIMBO_BUILD_NUMBER:-181}"
usage() {
  echo 'Usage: bash scripts/ci/export-ios-xtool-dependencies.sh [--output DIRECTORY]'
}
while (( $# )); do
  case "$1" in
    --output) [[ $# -ge 2 && -n "$2" ]] || { usage >&2; exit 2; }; EXPORT_DIR="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) usage >&2; exit 2 ;;
  esac
done
[[ "$(uname -s)" == Darwin ]] || { echo 'xtool dependency export requires macOS and Xcode' >&2; exit 2; }
[[ "${VERSION}" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-beta\.[1-9][0-9]*)?$ ]] || { echo 'Invalid NIMBO_VERSION' >&2; exit 2; }
[[ "${BUILD_NUMBER}" =~ ^[1-9][0-9]{0,3}$ ]] && (( BUILD_NUMBER > 170 )) || { echo 'Invalid NIMBO_BUILD_NUMBER' >&2; exit 2; }
for tool in python3 git xcodebuild xcrun ditto tar shasum; do
  command -v "${tool}" >/dev/null || { echo "Required tool missing: ${tool}" >&2; exit 3; }
done
mkdir -p "${EXPORT_DIR}"
EXPORT_DIR="$(cd "${EXPORT_DIR}" && pwd)"
ARCHIVE_NAME="Nimbo_v${VERSION}_ios_xtool_dependencies.tar.gz"
# Refuse collisions; never clean or recursively delete the caller's directory.
for name in dependencies manifest.json "${ARCHIVE_NAME}" "${ARCHIVE_NAME}.sha256"; do
  [[ ! -e "${EXPORT_DIR}/${name}" && ! -L "${EXPORT_DIR}/${name}" ]] || { echo "Export already exists: ${EXPORT_DIR}/${name}" >&2; exit 3; }
done
WORK_DIR="$(mktemp -d "${TMPDIR:-/tmp}/nimbo-xtool.XXXXXX")"
trap 'rm -rf -- "${WORK_DIR}"' EXIT
STAGE="${WORK_DIR}/dependencies"
mkdir -p "${STAGE}/PacketTunnelResources" "${STAGE}/NativeBuildReceipts" "${STAGE}/BrandingResources"
cd "${ROOT_DIR}"

# Same notice directory as build-unsigned-ios.sh; copy the entire directory.
for notice in Chromium-LICENSE.txt cronet-go-LICENSE.txt GPL-3.0.txt NaiveProxy-LICENSE.txt Naive-Sources.txt; do
  [[ -s "iosApp/NativeNotices/${notice}" ]] || { echo "Missing NativeNotices/${notice}" >&2; exit 4; }
done
ditto "${ROOT_DIR}/iosApp/NativeNotices" "${STAGE}/NativeNotices"

# Reuse the reviewed archive/file checksum helper. Its CLI core is temporary;
# only the verified geo databases are exported to the PacketTunnel resource path.
python3 "${ROOT_DIR}/apps/ui/scripts/stage-xray.py" \
  --target x86_64-unknown-linux-gnu --output "${WORK_DIR}/geo-source"
for resource in geoip.dat geosite.dat; do
  [[ -s "${WORK_DIR}/geo-source/${resource}" ]] || { echo "Missing ${resource}" >&2; exit 4; }
  cp "${WORK_DIR}/geo-source/${resource}" "${STAGE}/PacketTunnelResources/${resource}"
done

# Linux cannot run actool. Compile both catalogs: AppIcon is in Nimbo/Assets,
# while the shared cloud image/symbol live in Branding/Branding.xcassets.
xcrun actool --compile "${STAGE}/BrandingResources" \
  --platform iphoneos --minimum-deployment-target 16.0 --app-icon AppIcon \
  --target-device iphone --target-device ipad \
  --output-partial-info-plist "${STAGE}/BrandingResources/BrandingPartialInfo.plist" \
  "${ROOT_DIR}/iosApp/Nimbo/Assets.xcassets" \
  "${ROOT_DIR}/iosApp/Branding/Branding.xcassets"

NIMBO_APPLE_DEVICE_ONLY=1 bash "${ROOT_DIR}/scripts/ci/prepare-libxray-apple.sh"
echo 'iOS Kotlin/Native release link: Gradle heap=6 GiB, workers=1'
bash ./gradlew --no-daemon --max-workers=1 \
  '-Dorg.gradle.jvmargs=-Xmx6g -XX:MaxMetaspaceSize=1g -Dfile.encoding=UTF-8 -XX:+HeapDumpOnOutOfMemoryError' \
  -PnimboIosOnly=true :shared:linkReleaseFrameworkIosArm64
SHARED_FRAMEWORK="${ROOT_DIR}/shared/build/bin/iosArm64/releaseFramework/NimboShared.framework"
[[ -d "${SHARED_FRAMEWORK}" ]] || { echo 'NimboShared device framework was not produced' >&2; exit 4; }
xcodebuild -create-xcframework -framework "${SHARED_FRAMEWORK}" \
  -output "${STAGE}/NimboShared.xcframework"
[[ -d "${ROOT_DIR}/iosApp/Vendor/LibXray.xcframework" ]] || { echo 'LibXray framework missing' >&2; exit 4; }
ditto "${ROOT_DIR}/iosApp/Vendor/LibXray.xcframework" "${STAGE}/LibXray.xcframework"
for receipt in libxray-build-info.txt mihomo-source-verification.json; do
  [[ -s "${ROOT_DIR}/iosApp/Vendor/${receipt}" ]] || { echo "Native receipt missing: ${receipt}" >&2; exit 4; }
  cp "${ROOT_DIR}/iosApp/Vendor/${receipt}" "${STAGE}/NativeBuildReceipts/${receipt}"
done

# Fail closed on layout, slice metadata, source inputs and every payload file.
python3 - "${ROOT_DIR}" "${STAGE}" "${VERSION}" "${BUILD_NUMBER}" <<'PY'
import hashlib
import json
import os
from pathlib import Path
import plistlib
import re
import subprocess
import sys

root, stage = map(Path, sys.argv[1:3])


def digest(path):
    value = hashlib.sha256()
    with path.open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            value.update(chunk)
    return value.hexdigest()


def require_file(path):
    if not path.is_file() or path.stat().st_size == 0:
        raise ValueError('Missing or empty required file: ' + str(path))


for framework in ('NimboShared', 'LibXray'):
    directory = stage / (framework + '.xcframework')
    require_file(directory / 'Info.plist')
    with (directory / 'Info.plist').open('rb') as stream:
        libraries = plistlib.load(stream)['AvailableLibraries']
    if (len(libraries) != 1 or libraries[0]['SupportedPlatform'] != 'ios'
            or libraries[0].get('SupportedPlatformVariant')
            or libraries[0]['SupportedArchitectures'] != ['arm64']):
        raise ValueError('Expected only iphoneos arm64: ' + framework)
    library = libraries[0]
    binary = directory / library['LibraryIdentifier'] / library['LibraryPath']
    require_file(binary / framework if binary.suffix == '.framework' else binary)
    if framework == 'LibXray':
        headers = directory / library['LibraryIdentifier'] / library['HeadersPath']
        require_file(headers / 'libXray.h')
        require_file(headers / 'module.modulemap')
    else:
        require_file(binary / 'Headers/NimboShared.h')
        require_file(binary / 'Modules/module.modulemap')

branding = stage / 'BrandingResources'
require_file(branding / 'Assets.car')
require_file(branding / 'BrandingPartialInfo.plist')
icons = list(branding.glob('AppIcon*.png'))
if not icons:
    raise ValueError('actool did not produce AppIcon PNGs')
for icon in icons:
    require_file(icon)
with (branding / 'BrandingPartialInfo.plist').open('rb') as stream:
    if not plistlib.load(stream).get('CFBundleIcons'):
        raise ValueError('actool partial plist has no CFBundleIcons')
for resource in ('geoip.dat', 'geosite.dat'):
    require_file(stage / 'PacketTunnelResources' / resource)
for notice in ('Chromium-LICENSE.txt', 'cronet-go-LICENSE.txt', 'GPL-3.0.txt',
               'NaiveProxy-LICENSE.txt', 'Naive-Sources.txt'):
    require_file(stage / 'NativeNotices' / notice)
receipts = stage / 'NativeBuildReceipts'
require_file(receipts / 'libxray-build-info.txt')
require_file(receipts / 'mihomo-source-verification.json')
native = dict(line.split('=', 1) for line in
              (receipts / 'libxray-build-info.txt').read_text().splitlines()
              if re.match(r'^[a-z0-9_]+=', line))
for key in ('apple_slices', 'swift_awg_link_check'):
    if native.get(key) != 'iphoneos-arm64':
        raise ValueError('Native receipt does not attest device-only build: ' + key)
for key in ('libxray_version', 'libxray_commit', 'libxray_source_sha256', 'xray_version',
            'awg_version', 'mihomo_version', 'naive_version', 'cronet_go_commit', 'go_version'):
    if not native.get(key):
        raise ValueError('Missing native pin: ' + key)
json.loads((receipts / 'mihomo-source-verification.json').read_text())


def git(*args):
    return subprocess.check_output(['git', '-C', str(root), *args])


# Include tracked and untracked, non-ignored source inputs; omit build caches.
# Each scope is required, including the local Go adapters and build helpers.
scopes = ('shared/src', 'shared/build.gradle.kts', 'gradle', 'gradlew',
          'gradle.properties', 'build.gradle.kts', 'settings.gradle.kts',
          'iosApp/GoBridge', 'tools/native', 'scripts/ci', 'iosApp/Shared',
          'iosApp/PacketTunnel', 'iosApp/NativeNotices', 'iosApp/Branding',
          'iosApp/Nimbo/Assets.xcassets', 'apps/ui/scripts/stage-xray.py',
          'apps/ui/src-tauri/xray-release.json')
fingerprints = {}
for scope in scopes:
    names = sorted(set(os.fsdecode(name) for name in
                       git('ls-files', '-z', '--cached', '--others', '--exclude-standard', '--', scope).split(b'\0') if name))
    if not names:
        raise ValueError('Missing source input: ' + scope)
    files = {}
    for name in names:
        path = root / name
        require_file(path)
        files[name] = digest(path)
    fingerprint = hashlib.sha256(json.dumps(files, sort_keys=True, separators=(',', ':')).encode()).hexdigest()
    fingerprints[scope] = {'sha256': fingerprint, 'files': files}

payload = {}
for path in sorted(stage.rglob('*')):
    relative = path.relative_to(stage).as_posix()
    if path.is_symlink():
        target = os.readlink(path)
        if Path(target).is_absolute() or not path.resolve().is_relative_to(stage.resolve()) or not path.exists():
            raise ValueError('Invalid payload symlink: ' + relative)
        payload[relative] = {'type': 'symlink', 'target': target,
                             'sha256': hashlib.sha256(os.fsencode(target)).hexdigest()}
    elif path.is_file():
        payload[relative] = {'type': 'file', 'sha256': digest(path), 'size': path.stat().st_size}
    elif not path.is_dir():
        raise ValueError('Unsupported payload entry: ' + relative)

geo_pin = json.loads((root / 'apps/ui/src-tauri/xray-release.json').read_text())
asset = next(item for item in geo_pin['assets'] if item['target'] == 'x86_64-unknown-linux-gnu')
for name in ('geoip.dat', 'geosite.dat'):
    if payload['PacketTunnelResources/' + name]['sha256'] != asset['files'][name]:
        raise ValueError('Geo resource differs from pinned helper: ' + name)
module_text = (root / 'iosApp/GoBridge/go.mod').read_text()
xray_module = re.search(r'^\s*github.com/xtls/xray-core\s+(\S+)', module_text, re.M)
if not xray_module:
    raise ValueError('Missing Xray Go module pin')
manifest = {
    'schema_version': 1, 'kind': 'nimbo-ios-xtool-dependencies',
    'version': sys.argv[3], 'build_number': int(sys.argv[4]),
    'git_commit': git('rev-parse', 'HEAD').decode().strip(),
    'git_dirty': bool(git('status', '--porcelain', '--untracked-files=normal', '--', *scopes)),
    'slices': ['iphoneos-arm64'], 'native_build': native,
    'core_pins': {'libxray': {key: native['libxray_' + key] for key in ('version', 'commit', 'source_sha256')},
                  'xray': {'version': native['xray_version'], 'module_version': xray_module[1]},
                  'awg': native['awg_version'], 'naive': native['naive_version'],
                  'cronet_go_commit': native['cronet_go_commit'],
                  'mihomo': json.loads((root / 'tools/native/mihomo-core/pins.json').read_text()),
                  'geo': {'version': geo_pin['version'], 'asset': asset}},
    'source_fingerprints': fingerprints,
    # The manifest excludes itself; the archive checksum covers its bytes.
    'payload_files': payload,
}
if not re.fullmatch(r'[0-9a-f]{40}', manifest['git_commit']):
    raise ValueError('Expected full Git commit')
(stage / 'manifest.json').write_text(json.dumps(manifest, indent=2, sort_keys=True) + '\n', encoding='utf-8')
PY

# BSD tar preserves framework symlinks by default (never pass -h).
COPYFILE_DISABLE=1 tar -czf "${WORK_DIR}/${ARCHIVE_NAME}" -C "${WORK_DIR}" dependencies
(
  cd "${WORK_DIR}"
  shasum -a 256 "${ARCHIVE_NAME}" > "${ARCHIVE_NAME}.sha256"
)
cp "${STAGE}/manifest.json" "${EXPORT_DIR}/manifest.json"
mv "${STAGE}" "${EXPORT_DIR}/dependencies"
mv "${WORK_DIR}/${ARCHIVE_NAME}" "${WORK_DIR}/${ARCHIVE_NAME}.sha256" "${EXPORT_DIR}/"
echo "Exported ${EXPORT_DIR}/${ARCHIVE_NAME}"
