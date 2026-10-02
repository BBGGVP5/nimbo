#!/usr/bin/env python3
"""Inspect actual Linux bundles without installing them or launching the UI."""
import argparse
import hashlib
import io
import json
from pathlib import Path
import subprocess
import tarfile
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[2]
MACHINES = {"x86_64-unknown-linux-gnu": 62, "aarch64-unknown-linux-gnu": 183}
HELPER = "usr/bin/nimbo-svc"
MANIFEST_SUFFIX = "resources/helper/linux/nimbo-svc.manifest.json"
ARCHIVE_PATH = "usr/lib/Nimbo/resources/helper/linux/nimbo-svc.zip"


def run(*args):
    return subprocess.check_output([str(arg) for arg in args], timeout=120)


def verify_helper(data, mode, manifest):
    assert manifest["target"] in MACHINES, "Unsupported helper target"
    assert len(data) >= 24 and data[:6] == b"\x7fELF\x02\x01", "Expected ELF64 LE helper"
    assert int.from_bytes(data[18:20], "little") == MACHINES[manifest["target"]], "Wrong ELF architecture"
    assert mode & 0o777 == 0o755, f"Unexpected helper mode: {mode:o}"
    assert hashlib.sha256(data).hexdigest() == manifest["sha256"], "Helper digest mismatch"


def tar_entries(data):
    with tarfile.open(fileobj=io.BytesIO(data)) as archive:
        return {entry.name.removeprefix("./").lstrip("/"): (entry.mode, archive.extractfile(entry).read())
                for entry in archive if entry.isfile()}


def cpio_entries(data):
    """Read rpm2cpio's newc stream; no filesystem extraction or third-party modules."""
    entries = {}
    offset = 0
    while offset + 110 <= len(data):
        header = data[offset:offset + 110]
        assert header[:6] in (b"070701", b"070702"), "Invalid RPM cpio header"
        mode = int(header[14:22], 16)
        size = int(header[54:62], 16)
        name_size = int(header[94:102], 16)
        offset += 110
        assert name_size > 0 and offset + name_size <= len(data), "Truncated cpio name"
        name = data[offset:offset + name_size - 1].decode("utf-8")
        offset = (offset + name_size + 3) & ~3
        assert offset + size <= len(data), "Truncated cpio payload"
        if name == "TRAILER!!!":
            return entries
        if mode & 0o170000 == 0o100000:
            entries[name.removeprefix("./").lstrip("/")] = (mode, data[offset:offset + size])
        offset = (offset + size + 3) & ~3
    raise AssertionError("Missing cpio trailer")


def extracted_appimage_helper(package, directory):
    offset = int(run(package.resolve(), "--appimage-offset").strip())
    assert offset > 0, "Invalid AppImage SquashFS offset"
    subprocess.run(["unsquashfs", "-o", str(offset), "-d", str(directory), "-f", str(package), ARCHIVE_PATH],
                   check=True, stdout=subprocess.DEVNULL, timeout=120)
    helper = directory / HELPER
    helper.parent.mkdir(parents=True, exist_ok=True)
    helper.write_bytes(archive_helper((directory / ARCHIVE_PATH).read_bytes()))
    helper.chmod(0o755)
    return helper


def archive_helper(data):
    assert len(data) <= 64 * 1024 * 1024, "Helper archive too large"
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        assert archive.namelist() == ["nimbo-svc"], "Invalid helper archive inventory"
        assert archive.getinfo("nimbo-svc").file_size <= 64 * 1024 * 1024, "Helper payload too large"
        return archive.read("nimbo-svc")


def load_manifest(target):
    stage = ROOT / "apps/ui/src-tauri/resources/helper/linux/nimbo-svc"
    manifest = json.loads(stage.with_name(stage.name + ".manifest.json").read_text())
    version = json.loads((ROOT / "apps/ui/package.json").read_text())["version"]
    assert manifest["target"] == target and manifest["version"] == version, "Stale helper metadata"
    verify_helper(stage.read_bytes(), stage.stat().st_mode, manifest)
    verify_helper(archive_helper(stage.with_name(stage.name + ".zip").read_bytes()), 0o755, manifest)
    return manifest


def inspect_package(package, manifest):
    if package.suffix == ".AppImage":
        with tempfile.TemporaryDirectory(prefix="nimbo-appimage-check-") as temporary:
            helper = extracted_appimage_helper(package, Path(temporary) / "payload")
            verify_helper(helper.read_bytes(), helper.stat().st_mode, manifest)
    else:
        entries = (tar_entries(run("dpkg-deb", "--fsys-tarfile", package)) if package.suffix == ".deb"
                   else tar_entries(run("bsdtar", "-cf", "-", "--format=pax", f"@{package}")))
        assert HELPER in entries, f"Helper missing from {package.name}"
        mode, data = entries[HELPER]
        verify_helper(data, mode, manifest)
        receipts = [json.loads(data) for name, (_, data) in entries.items() if name.endswith(MANIFEST_SUFFIX)]
        assert receipts == [manifest], "Bundled helper manifest missing or inconsistent"
        verify_helper(archive_helper(entries[ARCHIVE_PATH][1]), 0o755, manifest)
    result = {"file": package.name, "bytes": package.stat().st_size,
              "sha256": hashlib.sha256(package.read_bytes()).hexdigest(), "helper": manifest}
    print(json.dumps(result))
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--target", required=True, choices=MACHINES)
    parser.add_argument("--bundle-dir", type=Path, default=ROOT / "target/release/bundle")
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()
    manifest = load_manifest(args.target)
    packages = []
    for directory, extension in (("appimage", "AppImage"), ("deb", "deb"), ("rpm", "rpm")):
        matches = list((args.bundle_dir / directory).glob(f"*.{extension}"))
        assert len(matches) == 1, f"Expected exactly one {extension} package"
        packages.append(inspect_package(matches[0], manifest))
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(json.dumps(packages, indent=2) + "\n")


if __name__ == "__main__":
    main()
