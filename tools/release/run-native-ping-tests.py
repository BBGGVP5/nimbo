#!/usr/bin/env python3
"""Run isolated loopback Xray regressions with the manifest-pinned Linux CLI.

Only downloads a verified official build and starts local fixture processes.
No system proxy, TUN, DNS, routes, services or real subscription data are used.
"""
import hashlib
import io
import json
import os
from pathlib import Path
import platform
import subprocess
import tempfile
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[2]
MAX_ARCHIVE = 96 * 1024 * 1024
MAX_BINARY = 128 * 1024 * 1024


def checked(data, expected):
    if hashlib.sha256(data).hexdigest() != expected:
        raise ValueError("Pinned native probe CLI digest mismatch")
    return data


def main():
    if platform.system() != "Linux":
        raise SystemExit("This isolated gate runs on Linux only")
    arch = {"x86_64": "x86_64", "aarch64": "aarch64"}.get(platform.machine())
    if not arch:
        raise SystemExit("Unsupported native probe gate architecture")
    manifest = json.loads((ROOT / "apps/ui/src-tauri/xray-release.json").read_text(encoding="utf-8"))
    asset = next(a for a in manifest["assets"] if a["os"] == "linux" and a["arch"] == arch)
    url = "https://github.com/XTLS/Xray-core/releases/download/" + manifest["tag"] + "/" + asset["archive"]
    with urllib.request.urlopen(url, timeout=60) as response:
        data = response.read(MAX_ARCHIVE + 1)
    if len(data) > MAX_ARCHIVE:
        raise ValueError("Native probe archive exceeds limit")
    checked(data, asset["sha256"])
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        if archive.getinfo("xray").file_size > MAX_BINARY:
            raise ValueError("Native probe executable exceeds limit")
        binary = checked(archive.read("xray"), asset["files"]["xray"])
    with tempfile.TemporaryDirectory(prefix="nimbo-native-ping-gate-") as directory:
        executable = Path(directory) / "xray"
        executable.write_bytes(binary)
        executable.chmod(0o700)
        subprocess.run(["cargo", "test", "-p", "nimbo-ui", "--lib",
                        "diagnostics::integration::real_cli", "--", "--ignored"],
                       cwd=ROOT, env={**os.environ, "NIMBO_DIAGNOSTIC_TEST_XRAY": str(executable)}, check=True)


if __name__ == "__main__":
    main()
