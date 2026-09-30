#!/usr/bin/env python3
"""Verify a staged single-runtime AAR without installing or loading it."""
import argparse
import hashlib
import io
import json
from pathlib import Path
import struct
import subprocess
import tempfile
import zipfile

ABIS = {"arm64-v8a": (2, 183), "armeabi-v7a": (1, 40), "x86": (1, 3), "x86_64": (2, 62)}
EXPORTS = (
    "java.lang.String nimboMihomoInvoke(java.lang.String);",
    "java.lang.String nimboMihomoCancel(java.lang.String);",
    "java.lang.String nimboMihomoSetSocketProtector(libXray.DialerController);",
    "java.lang.String nimboMihomoSetFlowOwnerResolver(libXray.NimboMihomoFlowOwner);",
    "java.lang.String resolve(java.lang.String, java.lang.String, long, java.lang.String, long);",
    "java.lang.String nimboMihomoAndroidTunPlan();",
    "java.lang.String nimboMihomoStartAndroid(java.lang.String, long);",
)


def check_elf(data, abi):
    elf_class, machine = ABIS[abi]
    if data[:4] != b"\x7fELF" or data[4:6] != bytes((elf_class, 1)):
        raise ValueError(f"Invalid ELF class/endianness: {abi}")
    if struct.unpack_from("<H", data, 18)[0] != machine:
        raise ValueError(f"ELF machine mismatch: {abi}")
    offset = struct.unpack_from("<Q" if elf_class == 2 else "<I", data, 32 if elf_class == 2 else 28)[0]
    size, count = struct.unpack_from("<HH", data, 54 if elf_class == 2 else 42)
    expected_size = 56 if elf_class == 2 else 32
    if size != expected_size or count < 1 or offset + size * count > len(data):
        raise ValueError(f"Invalid program header table: {abi}")
    loads = 0
    for n in range(count):
        start = offset + n * size
        if struct.unpack_from("<I", data, start)[0] != 1:
            continue
        loads += 1
        alignment = struct.unpack_from("<Q" if elf_class == 2 else "<I", data, start + (48 if elf_class == 2 else 28))[0]
        if alignment < 16384 or alignment & (alignment - 1):
            raise ValueError(f"PT_LOAD is not 16 KiB compatible: {abi}")
    if not loads:
        raise ValueError(f"No loadable segments: {abi}")


def inspect_aar(path):
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)):
            raise ValueError("Duplicate AAR entries")
        native = {name for name in names if name.endswith(".so")}
        expected = {f"jni/{abi}/libgojni.so" for abi in ABIS}
        if native != expected:
            raise ValueError(f"Expected exactly one Go runtime per ABI; got {sorted(native)}")
        for abi in ABIS:
            check_elf(archive.read(f"jni/{abi}/libgojni.so"), abi)
        return archive.read("classes.jar")


def classes(jar_bytes):
    with zipfile.ZipFile(io.BytesIO(jar_bytes)) as archive:
        return sorted(name[:-6].replace("/", ".") for name in archive.namelist()
                      if name.startswith("libXray/") and name.endswith(".class") and "$" not in name)


def api(javap, jar, class_names):
    output = subprocess.run([javap, "-public", "-classpath", str(jar), *class_names],
                            check=True, capture_output=True, text=True, encoding="utf-8").stdout
    # Keep the declaring class in each key: moving a method to a different
    # generated class is an ABI break even if its printed signature survives.
    result, owner = set(), ""
    for line in output.splitlines():
        line = line.strip()
        if line.startswith("public ") and line.endswith("{"):
            owner = line
            result.add((owner, owner))
        elif line.endswith(";"):
            result.add((owner, line))
    return result, output


def dependency(metadata, module, version):
    """Return the build-info entry for a module, including local replacements."""
    lines = metadata.splitlines()
    for index, line in enumerate(lines):
        fields = line.strip().split()
        if len(fields) >= 3 and fields[:3] == ["dep", module, version]:
            replacement = None
            if index + 1 < len(lines):
                next_fields = lines[index + 1].strip().split()
                if len(next_fields) >= 2 and next_fields[0] == "=>":
                    replacement = next_fields[1]
            return replacement
    return None


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("aar", type=Path)
    parser.add_argument("--baseline", required=True, type=Path)
    parser.add_argument("--javap", required=True)
    parser.add_argument("--go", default="go")
    args = parser.parse_args()
    new_jar = inspect_aar(args.aar)
    with zipfile.ZipFile(args.baseline) as baseline:
        old_jar = baseline.read("classes.jar")
    old_classes, new_classes = classes(old_jar), classes(new_jar)
    if not set(old_classes) <= set(new_classes):
        raise ValueError("Existing LibXray Java class removed")
    with tempfile.TemporaryDirectory(prefix="nimbo-aar-api-") as temp:
        old, new = Path(temp) / "old.jar", Path(temp) / "new.jar"
        old.write_bytes(old_jar)
        new.write_bytes(new_jar)
        old_api, _ = api(args.javap, old, old_classes)
        new_api, output = api(args.javap, new, new_classes)
        if not old_api <= new_api:
            raise ValueError(f"Existing public Java API changed: {sorted(old_api - new_api)}")
        for symbol in EXPORTS:
            if symbol not in output:
                raise ValueError(f"Missing new Mihomo JNI export: {symbol}")
        # Inspect, never execute, target-architecture ELF build metadata. The
        # upstream builder temporarily edits go.mod for gomobile; verify what
        # actually reached every shipped native library, not only restored files.
        with zipfile.ZipFile(args.aar) as archive:
            for abi in ABIS:
                native = Path(temp) / f"{abi}.so"
                native.write_bytes(archive.read(f"jni/{abi}/libgojni.so"))
                metadata = subprocess.run([args.go, "version", "-m", str(native)],
                                          check=True, capture_output=True, text=True).stdout
                for module, version in (
                    ("github.com/xtls/xray-core", "v1.260327.1-0.20260930074004-b26a91de4f32"),
                    ("github.com/metacubex/mihomo", "v1.19.31"),
                ):
                    replacement = dependency(metadata, module, version)
                    if replacement is None and module == "github.com/metacubex/mihomo":
                        raise ValueError(f"Missing or changed compiled core pin in {abi}: {module}")
                    if replacement is None and not any(
                        line.strip().split()[:3] == ["dep", module, version]
                        for line in metadata.splitlines()
                    ):
                        raise ValueError(f"Missing or changed compiled core pin in {abi}: {module}")
                    if module == "github.com/metacubex/mihomo":
                        expected_source = (args.aar.parent.parent / "dependencies" / "mihomo").resolve()
                        actual_source = Path(replacement).resolve() if replacement else None
                        if actual_source != expected_source:
                            raise ValueError(f"Compiled Mihomo source is not the verified staged pin in {abi}")
                if "go1.27.1" not in metadata or "with_gvisor" not in metadata:
                    raise ValueError(f"Unexpected toolchain/TUN build tag in {abi}")
                for tag in ("no_tailscale", "no_zerotier", "no_easytier"):
                    if tag not in metadata:
                        raise ValueError(f"Missing lean-core build tag in {abi}: {tag}")
                for module in ("github.com/metacubex/tailscale", "github.com/metacubex/zerotier-go", "github.com/easytier/easytier/easytier-go"):
                    if any(line.strip().split()[:2] == ["dep", module] for line in metadata.splitlines()):
                        raise ValueError(f"Mesh dependency still compiled in {abi}: {module}")
                if "\tdep\tnimbo/mihomocore\tv0.0.0" not in metadata:
                    raise ValueError(f"Merged native adapter missing in {abi}")
    print(json.dumps({"aar": str(args.aar.resolve()), "sha256": hashlib.sha256(args.aar.read_bytes()).hexdigest(),
                      "abis": list(ABIS), "existingApiPreserved": True, "mihomoBridgePresent": True,
                      "deviceRuntimeTested": False}, indent=2))


if __name__ == "__main__":
    main()
