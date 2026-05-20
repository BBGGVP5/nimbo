#!/usr/bin/env bash
set -euo pipefail

targets=()
build_all=0

usage() {
  cat <<'EOF'
Usage:
  ./build-custom-linux-installer.sh [--target x86_64-unknown-linux-gnu[,aarch64-unknown-linux-gnu]] [--all]

Examples:
  ./build-custom-linux-installer.sh
  ./build-custom-linux-installer.sh --target x86_64-unknown-linux-gnu
  ./build-custom-linux-installer.sh --target x86_64-unknown-linux-gnu,aarch64-unknown-linux-gnu
  ./build-custom-linux-installer.sh --all

Note:
  --all tries x86_64 and aarch64 Linux targets. Cross-building Tauri/WebKitGTK
  needs the matching system toolchain and dev packages for every target.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --target|-t)
      if [[ $# -lt 2 ]]; then
        echo "--target requires a value" >&2
        exit 1
      fi
      IFS=',' read -r -a targets <<< "$2"
      shift 2
      ;;
    --all)
      build_all=1
      shift
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage
      exit 1
      ;;
  esac
done

if [[ "$(uname -s)" != "Linux" ]]; then
  echo "This custom Linux installer must be built on Linux or inside a Linux CI/VM/WSL environment." >&2
  exit 1
fi

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd -- "$script_dir/../.." && pwd)"
ui_dir="$repo_root/apps/ui"
installer_dir="$repo_root/apps/installer"
out_dir="$repo_root/target/release/bundle/custom/linux"

if [[ $build_all -eq 1 ]]; then
  targets=("x86_64-unknown-linux-gnu" "aarch64-unknown-linux-gnu")
elif [[ ${#targets[@]} -eq 0 ]]; then
  host_target="$(rustc -vV | awk '/^host:/ { print $2 }')"
  targets=("$host_target")
fi

clean_targets=()
for target in "${targets[@]}"; do
  target="$(echo "$target" | xargs)"
  [[ -n "$target" ]] && clean_targets+=("$target")
done
targets=("${clean_targets[@]}")

if [[ ${#targets[@]} -eq 0 ]]; then
  echo "No targets selected." >&2
  exit 1
fi

version="$(sed -nE 's/.*"version"[[:space:]]*:[[:space:]]*"([^"]+)".*/\1/p' "$installer_dir/src-tauri/tauri.conf.json" | head -n 1)"
if [[ -z "$version" ]]; then
  echo "Failed to read installer version." >&2
  exit 1
fi

arch_suffix() {
  case "$1" in
    x86_64-unknown-linux-gnu) echo "x64" ;;
    aarch64-unknown-linux-gnu) echo "arm64" ;;
    armv7-unknown-linux-gnueabihf) echo "armv7" ;;
    *) echo "$1" | sed -E 's/-unknown-linux-gnu(eabihf)?$//' ;;
  esac
}

ensure_node_modules() {
  local dir="$1"
  if [[ ! -d "$dir/node_modules" ]]; then
    (cd "$dir" && npm ci)
  fi
}

mkdir -p "$out_dir"

ensure_node_modules "$ui_dir"
(cd "$ui_dir" && npm run build)

# Force tauri-build to re-embed the fresh frontend dist even when cargo's
# incremental cache would otherwise keep an old generated context.
touch "$ui_dir/src-tauri/tauri.conf.json"
[[ -f "$ui_dir/dist/index.html" ]] && touch "$ui_dir/dist/index.html"

for target_triple in "${targets[@]}"; do
  echo "Building Linux app payload for $target_triple..."
  (cd "$repo_root" && cargo build -p nimbo-ui --release --features custom-protocol --target "$target_triple")
done

ensure_node_modules "$installer_dir"

created_installers=()
for target_triple in "${targets[@]}"; do
  echo "Building custom Linux installer for $target_triple..."
  (cd "$installer_dir" && npx tauri build --no-bundle --target "$target_triple")

  built="$repo_root/target/$target_triple/release/nimbo-installer"
  if [[ ! -f "$built" ]]; then
    echo "Custom Linux installer binary was not found: $built" >&2
    exit 1
  fi

  suffix="$(arch_suffix "$target_triple")"
  out_file="$out_dir/NimboSetup_${version}_${suffix}"
  cp -f "$built" "$out_file"
  chmod +x "$out_file"
  created_installers+=("$out_file")
done

echo "Custom Linux installer created:"
for file in "${created_installers[@]}"; do
  echo " - $file"
done
