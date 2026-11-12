#!/usr/bin/env python3
"""Set the Nimbo product version everywhere it is written, in one step.

    python tools/release/bump-version.py 1.3.1
    python tools/release/bump-version.py 1.4.0-beta.1 --android-code 22 --ios-build 195
    python tools/release/bump-version.py 1.3.1 --dry-run

Android versionCode and the iOS build number go up by one unless given.
Edits are textual so file formatting stays as it is; check-versions.py then
verifies the result the same way the release workflow does.
"""
import argparse
import json
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
VERSION_RE = re.compile(r"\d+\.\d+\.\d+(?:-[A-Za-z0-9.-]+)?")


class Edits:
    def __init__(self, dry_run):
        self.dry_run = dry_run
        self.changed = []

    def sub(self, rel, pattern, repl, count=1, flags=0):
        path = ROOT / rel
        text = path.read_text(encoding="utf-8-sig")
        new, n = re.subn(pattern, repl, text, count=count, flags=flags)
        if n == 0:
            raise SystemExit(f"{rel}: pattern not found: {pattern}")
        if new != text:
            self.changed.append(rel)
            if not self.dry_run:
                bom = path.read_bytes().startswith(b"\xef\xbb\xbf")
                path.write_bytes((b"\xef\xbb\xbf" if bom else b"") + new.encode("utf-8"))

    def read(self, rel, pattern):
        match = re.search(pattern, (ROOT / rel).read_text(encoding="utf-8-sig"), re.M)
        if not match:
            raise SystemExit(f"{rel}: pattern not found: {pattern}")
        return match.group(1)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("version")
    parser.add_argument("--android-code", type=int)
    parser.add_argument("--ios-build", type=int)
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()
    version = args.version.removeprefix("v")
    if not VERSION_RE.fullmatch(version):
        raise SystemExit("Version must look like 1.3.1 or 1.4.0-beta.1")
    numeric = version.split("-", 1)[0]
    e = Edits(args.dry_run)

    gradle = "app/build.gradle.kts"
    old_code = int(e.read(gradle, r"versionCode\s*=\s*(\d+)"))
    code = args.android_code or old_code + 1
    project = "iosApp/project.yml"
    old_build = int(e.read(project, r"^    CURRENT_PROJECT_VERSION: (\d+)$"))
    build = args.ios_build or old_build + 1
    if code <= old_code or build <= old_build:
        raise SystemExit(f"Build numbers must grow: Android {old_code}->{code}, iOS {old_build}->{build}")

    # Desktop: npm manifests, lockfiles and Tauri configs.
    for app in ("ui", "installer"):
        e.sub(f"apps/{app}/package.json", r'("version":\s*")[^"]+(")', rf"\g<1>{version}\g<2>")
        e.sub(f"apps/{app}/src-tauri/tauri.conf.json", r'("version":\s*")[^"]+(")', rf"\g<1>{version}\g<2>")
        # Top-level "version" and the root package entry (packages[""]).
        e.sub(f"apps/{app}/package-lock.json", r'(\A\{\s*"name":\s*"[^"]+",\s*"version":\s*")[^"]+(")',
              rf"\g<1>{version}\g<2>")
        e.sub(f"apps/{app}/package-lock.json", r'("packages":\s*\{\s*"":\s*\{\s*"name":\s*"[^"]+",\s*"version":\s*")[^"]+(")',
              rf"\g<1>{version}\g<2>")

    # Rust workspace and its own (path) crates in Cargo.lock.
    e.sub("Cargo.toml", r'(\[workspace\.package\]\s*\nversion\s*=\s*")[^"]+(")', rf"\g<1>{version}\g<2>")
    e.sub("crates/mihomo/Cargo.toml", r'(?m)^(version\s*=\s*")[^"]+(")', rf"\g<1>{version}\g<2>")
    e.sub("Cargo.lock", r'(\[\[package\]\]\nname = "nimbo-[^"]+"\nversion = ")[^"]+("\n(?!source))',
          rf"\g<1>{version}\g<2>", count=0)

    # Windows installer script defaults.
    nsi = "apps/ui/src-tauri/windows/nimbo-installer.nsi"
    e.sub(nsi, r'(!define PRODUCT_VERSION ")[^"]+(")', rf"\g<1>{version}\g<2>")
    old_file = e.read(nsi, r'!define PRODUCT_FILE_VERSION "([^"]+)"')
    tail = int(old_file.rsplit(".", 1)[1]) + 1 if old_file.startswith(numeric + ".") else 0
    e.sub(nsi, r'(!define PRODUCT_FILE_VERSION ")[^"]+(")', rf"\g<1>{numeric}.{tail}\g<2>")

    # Android and shared Kotlin.
    e.sub(gradle, r"(versionCode\s*=\s*)\d+", rf"\g<1>{code}")
    e.sub(gradle, r'(versionName\s*=\s*")[^"]+(")', rf"\g<1>{version}\g<2>")
    e.sub("shared/src/commonMain/kotlin/com/danila/nimbo/shared/updates/ReleaseDefaults.kt",
          r'(const val VERSION = ")[^"]+(")', rf"\g<1>{version}\g<2>")

    # iOS project and unsigned-build defaults.
    e.sub(project, r"(?m)^(    CURRENT_PROJECT_VERSION: )\d+$", rf"\g<1>{build}")
    e.sub(project, r"(?m)^(    MARKETING_VERSION: ).+$", rf"\g<1>{numeric}")
    e.sub(project, r"(?m)^(    NIMBO_DISPLAY_VERSION: ).+$", rf"\g<1>{version}")
    ios_sh = "scripts/ci/build-unsigned-ios.sh"
    e.sub(ios_sh, r'(VERSION="\$\{NIMBO_VERSION:-)[^}]+(\}")', rf"\g<1>{version}\g<2>")
    e.sub(ios_sh, r'(BUILD_NUMBER="\$\{NIMBO_BUILD_NUMBER:-)\d+(\}")', rf"\g<1>{build}\g<2>")
    e.sub(ios_sh, r"\(default: \d+\.\d+\.\d+[^)]*\)", f"(default: {version})")
    e.sub(ios_sh, r"\(default: \d+\)", f"(default: {build})")
    wf = ".github/workflows/build-ios-unsigned.yml"
    e.sub(wf, r"(?m)^(        default: )\d+\.\d+\.\d+\S*$", rf"\g<1>{version}")
    e.sub(wf, r"(?m)^(        default: ')\d+(')$", rf"\g<1>{build}\g<2>")
    e.sub(wf, r"(inputs\.build_number \|\| ')\d+(')", rf"\g<1>{build}\g<2>")
    e.sub(wf, r"(inputs\.version \|\| ')[^']+(')", rf"\g<1>{version}\g<2>", count=0)

    # The checker's default tag follows the current version.
    e.sub("tools/release/check-versions.py", r'(os\.environ\.get\("TAG", "v)[^"]+("\))', rf"\g<1>{version}\g<2>")

    print(f"Nimbo {version}: Android versionCode {old_code} -> {code}, iOS build {old_build} -> {build}")
    for rel in dict.fromkeys(e.changed):
        print(("would change " if args.dry_run else "changed ") + rel)
    if not args.dry_run:
        result = subprocess.run([sys.executable, str(ROOT / "tools/release/check-versions.py"), "v" + version])
        if result.returncode:
            raise SystemExit(result.returncode)
        print("Next: write docs/releases/%s.md and RELEASE_NOTES_%s.md" % (version, version))


if __name__ == "__main__":
    main()
