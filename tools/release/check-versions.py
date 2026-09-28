#!/usr/bin/env python3
"""Fail a release build before packaging if product versions do not match its tag."""
import json
import argparse
import os
from pathlib import Path
import re
import sys
import tomllib

ROOT = Path(__file__).resolve().parents[2]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("tag", nargs="?", default=os.environ.get("TAG", "v1.3.0-beta.1"))
    parser.add_argument("--desktop-root", type=Path, default=ROOT)
    args = parser.parse_args()
    tag = args.tag
    desktop = args.desktop_root
    if not re.fullmatch(r"v\d+\.\d+\.\d+(?:-[A-Za-z0-9.-]+)?", tag):
        raise SystemExit("Invalid release tag")
    version = tag[1:]
    versions = {}
    for app in ("ui", "installer"):
        for filename in ("package.json", "src-tauri/tauri.conf.json"):
            path = f"apps/{app}/{filename}"
            versions[path] = json.loads((desktop / path).read_text(encoding="utf-8-sig"))["version"]
        path = f"apps/{app}/package-lock.json"
        lock = json.loads((desktop / path).read_text(encoding="utf-8-sig"))
        versions[path] = lock["version"]
        versions[path + ":root"] = lock["packages"][""]["version"]
    versions["Cargo.toml"] = tomllib.loads((desktop / "Cargo.toml").read_text())["workspace"]["package"]["version"]
    for package in tomllib.loads((desktop / "Cargo.lock").read_text())["package"]:
        if package["name"].startswith("nimbo-") and "source" not in package:
            versions["Cargo.lock:" + package["name"]] = package["version"]
    android = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8-sig")
    versions["Android"] = re.search(r'versionName\s*=\s*"([^"]+)"', android).group(1)
    shared = (ROOT / "shared/src/commonMain/kotlin/com/danila/nimbo/shared/updates/ReleaseDefaults.kt").read_text(encoding="utf-8")
    versions["Shared"] = re.search(r'const val VERSION = "([^"]+)"', shared).group(1)
    ios = (ROOT / "iosApp/project.yml").read_text(encoding="utf-8")
    versions["iOS display"] = re.search(r"(?m)^    NIMBO_DISPLAY_VERSION: (.+)$", ios).group(1).strip()
    marketing = re.search(r"(?m)^    MARKETING_VERSION: (.+)$", ios).group(1).strip()
    if marketing != version.split("-", 1)[0]:
        raise SystemExit(f"iOS numeric marketing version mismatch: {marketing}")
    for name, found in versions.items():
        if found != version:
            raise SystemExit(f"Version mismatch: {name}: {found}, expected {version}")
    if version == "1.2.0":
        code = int(re.search(r"versionCode\s*=\s*(\d+)", android).group(1))
        if code <= 16:
            raise SystemExit("Stable Android versionCode must exceed Beta 5 (16)")
    if version == "1.3.0-beta.1":
        code = int(re.search(r"versionCode\s*=\s*(\d+)", android).group(1))
        build = int(re.search(r"(?m)^    CURRENT_PROJECT_VERSION: (\d+)$", ios).group(1))
        if code != 18 or build != 180:
            raise SystemExit("Beta 1 requires Android code18 / iOS build180, above stable17 / 170")
    print(f"Verified {len(versions)} product version fields: {version}")


if __name__ == "__main__":
    main()
