#!/usr/bin/env python3
"""Fail a release build before packaging if product versions do not match its tag."""
import json
import os
from pathlib import Path
import re
import sys
import tomllib

ROOT = Path(__file__).resolve().parents[2]


def main():
    tag = sys.argv[1] if len(sys.argv) > 1 else os.environ.get("TAG", "v1.2.0")
    if not re.fullmatch(r"v\d+\.\d+\.\d+(?:-[A-Za-z0-9.-]+)?", tag):
        raise SystemExit("Invalid release tag")
    version = tag[1:]
    versions = {}
    for app in ("ui", "installer"):
        for filename in ("package.json", "src-tauri/tauri.conf.json"):
            path = f"apps/{app}/{filename}"
            versions[path] = json.loads((ROOT / path).read_text(encoding="utf-8-sig"))["version"]
        path = f"apps/{app}/package-lock.json"
        lock = json.loads((ROOT / path).read_text(encoding="utf-8-sig"))
        versions[path] = lock["version"]
        versions[path + ":root"] = lock["packages"][""]["version"]
    versions["Cargo.toml"] = tomllib.loads((ROOT / "Cargo.toml").read_text())["workspace"]["package"]["version"]
    for package in tomllib.loads((ROOT / "Cargo.lock").read_text())["package"]:
        if package["name"].startswith("nimbo-") and "source" not in package:
            versions["Cargo.lock:" + package["name"]] = package["version"]
    android = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8-sig")
    versions["Android"] = re.search(r'versionName\s*=\s*"([^"]+)"', android).group(1)
    for name, found in versions.items():
        if found != version:
            raise SystemExit(f"Version mismatch: {name}: {found}, expected {version}")
    if version == "1.2.0":
        code = int(re.search(r"versionCode\s*=\s*(\d+)", android).group(1))
        if code <= 16:
            raise SystemExit("Stable Android versionCode must exceed Beta 5 (16)")
    print(f"Verified {len(versions)} product version fields: {version}")


if __name__ == "__main__":
    main()
