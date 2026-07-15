#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd -- "$script_dir/../.." && pwd)"
bundle_dir="$repo_root/target/release/bundle"

if [[ "$(uname -s)" != "Linux" ]]; then
  echo "Linux release artifacts must be built on Linux, in WSL, or on a Linux CI runner." >&2
  exit 1
fi

echo "Building AppImage, DEB and RPM for $(rustc -vV | awk '/^host:/ { print $2 }')..."
(cd "$repo_root/apps/ui" && npm ci && npm run build:linux)

echo "Building the custom Nimbo installer..."
(cd "$repo_root/apps/installer" && npm ci && npm run build:custom:linux)

artifacts=(
  "$bundle_dir/appimage"/*.AppImage
  "$bundle_dir/deb"/*.deb
  "$bundle_dir/rpm"/*.rpm
  "$bundle_dir/custom/linux"/NimboSetup_*
)

for artifact in "${artifacts[@]}"; do
  if [[ ! -f "$artifact" ]]; then
    echo "Missing Linux release artifact: $artifact" >&2
    exit 1
  fi
done

printf 'Linux release artifacts:\n'
printf ' - %s\n' "${artifacts[@]}"
