#!/usr/bin/env bash
set -euo pipefail

TOOLS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
usage() {
  echo 'Usage: build-xtool.sh SOURCE_ROOT DEPENDENCIES_ROOT NEW_WORKSPACE SOURCE_COMMIT OUTPUT_DIRECTORY'
  echo 'Set NIMBO_XTOOL_BIN to the xtool executable and put Swift 6.4+ on PATH.'
}
if [[ "${1:-}" == --help ]]; then usage; exit 0; fi
[[ $# == 5 ]] || { usage >&2; exit 2; }
SOURCE_ROOT="$1"
DEPENDENCIES_ROOT="$2"
WORKSPACE="$3"
SOURCE_COMMIT="$4"
OUTPUT_DIRECTORY="$5"
[[ "${SOURCE_COMMIT}" =~ ^[0-9a-f]{40}$ ]] || { echo 'Expected full source commit' >&2; exit 2; }
XTOOL_BIN="${NIMBO_XTOOL_BIN:-xtool}"
LDID_BIN="${NIMBO_LDID_BIN:-ldid}"
command -v "${LDID_BIN}" >/dev/null || { echo 'Procursus ldid is required for per-extension entitlements' >&2; exit 2; }
export APPIMAGE_EXTRACT_AND_RUN=1
read -r VERSION BUILD_NUMBER < <(python3 - "${DEPENDENCIES_ROOT}/manifest.json" <<'PY'
import json
import re
import sys
manifest = json.load(open(sys.argv[1]))
version, build = manifest['version'], str(manifest['build_number'])
if not re.fullmatch(r'\d+\.\d+\.\d+(?:-beta\.\d+)?', version) or not build.isdigit():
    raise ValueError('Invalid dependency version/build')
print(version, build)
PY
)
mkdir -p "${OUTPUT_DIRECTORY}"
OUTPUT_DIRECTORY="$(cd "${OUTPUT_DIRECTORY}" && pwd)"
IPA_NAME="Nimbo_v${VERSION}_ios_resignable.ipa"
for name in "${IPA_NAME}" "${IPA_NAME}.sha256" xtool-build-verification.json; do
  [[ ! -e "${OUTPUT_DIRECTORY}/${name}" ]] || { echo "Output exists: ${name}" >&2; exit 2; }
done
swift --version
"${XTOOL_BIN}" --version
"${XTOOL_BIN}" sdk status
python3 - "${TOOLS_DIR}" "${DEPENDENCIES_ROOT}" "${SOURCE_ROOT}" "${SOURCE_COMMIT}" <<'PY'
from pathlib import Path
import sys
sys.path.insert(0, sys.argv[1])
from xtool_dependencies import verify
result = verify(Path(sys.argv[2]), Path(sys.argv[3]), sys.argv[4])
print('Verified source and dependencies for', result['commit'])
PY
python3 "${TOOLS_DIR}/xtool_prepare.py" --repo-root "${SOURCE_ROOT}" \
  --dependencies-root "${DEPENDENCIES_ROOT}" --output "${WORKSPACE}"
python3 - "${WORKSPACE}" "${SOURCE_COMMIT}" <<'PY'
from pathlib import Path
import plistlib
import sys
root = Path(sys.argv[1])
for path in (root / 'Support').glob('*-Info.plist'):
    info = plistlib.loads(path.read_bytes())
    info['NimboBuildRevision'] = sys.argv[2][:12]
    path.write_bytes(plistlib.dumps(info, sort_keys=True))
PY
(
  cd "${WORKSPACE}"
  "${XTOOL_BIN}" dev build --configuration release --ipa
)
python3 "${TOOLS_DIR}/xtool_sign.py" "${WORKSPACE}/xtool/Nimbo.ipa" \
  --workspace "${WORKSPACE}" --output "${WORKSPACE}/xtool/Nimbo-per-bundle.ipa" \
  --ldid "${LDID_BIN}" --receipt "${OUTPUT_DIRECTORY}/xtool-entitlements.json"
python3 "${TOOLS_DIR}/xtool_verify.py" "${WORKSPACE}/xtool/Nimbo-per-bundle.ipa" \
  --dependencies-root "${DEPENDENCIES_ROOT}" --version "${VERSION}" --build "${BUILD_NUMBER}" \
  --source-commit "${SOURCE_COMMIT}" \
  --receipt "${OUTPUT_DIRECTORY}/xtool-build-verification.json"
cp "${WORKSPACE}/xtool/Nimbo-per-bundle.ipa" "${OUTPUT_DIRECTORY}/${IPA_NAME}"
python3 - "${OUTPUT_DIRECTORY}/${IPA_NAME}" <<'PY'
import hashlib
from pathlib import Path
import sys
path = Path(sys.argv[1])
digest = hashlib.sha256(path.read_bytes()).hexdigest()
Path(str(path) + '.sha256').write_text(digest + '  ' + path.name + '\n')
print('Verified IPA:', path)
PY
