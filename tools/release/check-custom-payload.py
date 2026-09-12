#!/usr/bin/env python3
"""Check the actual compiled custom installer, without installing or running it."""
import hashlib
import json
import mmap
from pathlib import Path
import struct
import sys

ROOT = Path(__file__).resolve().parents[2]
TARGETS = {
    "windows-x64": ("x86_64-pc-windows-msvc", "x64", 0x8664),
    "windows-x86": ("i686-pc-windows-msvc", "x86", 0x14C),
    "windows-arm64": ("aarch64-pc-windows-msvc", "arm64", 0xAA64),
    "linux-x64": ("x86_64-unknown-linux-gnu", "x64", 62),
}


def machine(data, windows):
    if windows:
        assert data[:2] == b"MZ", "Expected PE executable"
        offset = struct.unpack_from("<I", data, 0x3C)[0]
        assert data[offset:offset + 4] == b"PE\0\0", "Invalid PE header"
        return struct.unpack_from("<H", data, offset + 4)[0]
    assert data[:4] == b"\x7fELF" and data[5] == 1, "Expected little-endian ELF"
    return struct.unpack_from("<H", data, 18)[0]


def main():
    selected = sys.argv[1:] or list(TARGETS)
    version = json.loads((ROOT / "apps/ui/package.json").read_text())["version"]
    for platform in selected:
        target, arch, expected_machine = TARGETS[platform]
        windows = platform.startswith("windows")
        suffix = ".exe" if windows else ""
        family = "windows" if windows else "linux"
        installer = ROOT / f"target/release/bundle/custom/{family}/NimboSetup_{version}_{arch}{suffix}"
        awg = ROOT / f"apps/ui/src-tauri/resources/awg/{platform}/nimbo-awg{suffix}"
        manifest = json.loads(Path(str(awg) + ".manifest.json").read_text())
        assert manifest["target"] == target and manifest["version"] == "v3.1.20260828"
        awg_bytes = awg.read_bytes()
        assert hashlib.sha256(awg_bytes).hexdigest() == manifest["sha256"], "AWG digest mismatch"
        parts = [("AWG", awg_bytes)]
        for name in ("nimbo-ui", "nimbo-svc"):
            parts.append((name, (ROOT / f"target/{target}/release/{name}{suffix}").read_bytes()))
        with installer.open("rb") as stream, mmap.mmap(stream.fileno(), 0, access=mmap.ACCESS_READ) as compiled:
            assert machine(compiled, windows) == expected_machine, "Wrong installer architecture"
            for name, data in parts:
                assert machine(data, windows) == expected_machine, f"Wrong {name} architecture"
                assert compiled.find(data) >= 0, f"Installer is missing the exact {name} payload"
        print(f"{platform}: installer contains matching application, helper and AWG 3.1 payloads")


if __name__ == "__main__":
    main()
