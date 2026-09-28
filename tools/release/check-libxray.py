#!/usr/bin/env python3
"""Verify the actual Android binary and the combined Apple libXray source pins.

Run from any directory: python tools/release/check-libxray.py
Optional --aar checks another AAR (useful for rejecting stale release inputs).
Uses only Python's standard library; no Android SDK or downloaded source needed.
"""
import argparse
import hashlib
import io
import json
from pathlib import Path
import re
import struct
import zipfile

ROOT = Path(__file__).resolve().parents[2]
VERSION = "26.9.9"
COMMIT = "50b95979f5db551bd273165cf469e5daaf791341"
SOURCE_SHA = "070a5b573f5a907d31dc23064c89a8cac2cbf9a8baf7df64c42b9cac78b50d4b"
AAR_SHA = "df0cabde00c20c08b9ece66e83fddeabcf9aa8061057674a647eb34508c72be3"
ARCHIVE_SHA = "4998a8b56e4a78a164b5359d5690036f83da3b575465cea57ddf29c0149c345f"
CORE = "v1.260327.1-0.20260908222543-52a412d9e2f5"
AWG = "v3.1.20260828"
ABIS = {"armeabi-v7a": (1, 40), "arm64-v8a": (2, 183),
        "x86": (1, 3), "x86_64": (2, 62)}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def elf_segments(data, abi):
    require(data[:4] == b"\x7fELF" and data[5] == 1, f"{abi}: expected little-endian ELF")
    bits, machine = ABIS[abi]
    require(data[4] == bits and struct.unpack_from("<H", data, 18)[0] == machine,
            f"{abi}: wrong ELF architecture")
    if bits == 2:
        offset = struct.unpack_from("<Q", data, 32)[0]
        size, count = struct.unpack_from("<HH", data, 54)
        fmt = "<IIQQQQQQ"
    else:
        offset = struct.unpack_from("<I", data, 28)[0]
        size, count = struct.unpack_from("<HH", data, 42)
        fmt = "<IIIIIIII"
    segments = []
    for i in range(count):
        entry = struct.unpack_from(fmt, data, offset + size * i)
        if entry[0] != 1:
            continue
        file_offset, address = entry[2:4] if bits == 2 else entry[1:3]
        alignment = entry[7]
        require(alignment >= 16384 and alignment & (alignment - 1) == 0
                and (address - file_offset) % 16384 == 0,
                f"{abi}: PT_LOAD segment is not 16 KiB aligned")
        segments.append({"offset": file_offset, "vaddr": address, "alignment": alignment})
    require(bool(segments), f"{abi}: missing PT_LOAD segments")
    return segments


def inspect_aar(path):
    data = path.read_bytes()
    require(hashlib.sha256(data).hexdigest() == AAR_SHA, "AAR SHA-256 mismatch: stale or modified libXray binary")
    bundled = {}
    with zipfile.ZipFile(io.BytesIO(data)) as aar:
        native = {n for n in aar.namelist() if n.startswith("jni/") and n.endswith(".so")}
        require(native == {f"jni/{abi}/libgojni.so" for abi in ABIS}, "Unexpected Android native ABI set")
        with zipfile.ZipFile(io.BytesIO(aar.read("classes.jar"))) as classes:
            for cls in ("libXray/LibXray.class", "libXray/DialerController.class"):
                require(cls in classes.namelist(), f"Missing Java binding {cls}")
            api = classes.read("libXray/LibXray.class")
            for method in (b"invoke", b"registerDialerController", b"registerListenerController", b"LibXrayAPIVersion"):
                require(method in api, f"Missing Java binding {method!r}")
        for abi in ABIS:
            library = aar.read(f"jni/{abi}/libgojni.so")
            bundled[abi] = {"file": "libgojni.so", "sha256": hashlib.sha256(library).hexdigest(),
                            "loadSegments": elf_segments(library, abi)}
    return bundled


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--aar", type=Path, default=ROOT / "app/libs/libxray.aar")
    args = parser.parse_args()
    bundled = inspect_aar(args.aar)
    metadata = json.loads((ROOT / "app/src/main/assets/third_party/libxray.json").read_text())
    for key, expected in {"version": VERSION, "commit": COMMIT, "aarSha256": AAR_SHA,
                          "archiveSha256": ARCHIVE_SHA, "sourceSha256": SOURCE_SHA,
                          "goVersion": "1.27.1", "xrayCore": CORE, "apiVersion": 3}.items():
        require(metadata.get(key) == expected, f"libxray.json: stale {key}")
    require(metadata["bundled"] == bundled, "Native library hashes/segments differ from metadata")
    require((ROOT / "app/src/main/assets/third_party/libxray.LICENSE").is_file(), "Missing libXray license")
    gradle = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
    declaration = next((line for line in gradle.splitlines() if '"LIBXRAY_VERSION"' in line), "")
    require(re.findall(r"\d+\.\d+\.\d+", declaration) == [VERSION], "Stale Android LIBXRAY_VERSION")
    android = (ROOT / "app/src/main/java/com/danila/nimbo/vpn/XrayCoreProtocol.kt").read_text(encoding="utf-8")
    require(re.search(r"const val API_VERSION = 3\b", android), "Android invoke requires API 3")
    require(re.search(r'fun runXrayFromJson\(configJson: String\).*?method = "runXray".*?payload = JSONObject\(\)\.put\("xrayJson", configJson\)', android, re.S),
            "Android run envelope must use runXray/payload.xrayJson")
    swift = (ROOT / "iosApp/PacketTunnel/LibXrayBridge.swift").read_text(encoding="utf-8")
    require(re.search(r"private static let apiVersion = 3\b", swift), "Swift invoke requires API 3")
    require(re.search(r'func run\(configurationJSON: String\).*?method: "runXray".*?payload: \["xrayJson": configurationJSON\]', swift, re.S),
            "Swift run envelope must use runXray/payload.xrayJson")
    require("defer { CGoFree(responsePointer) }" in swift, "Swift must free every C response")
    script = (ROOT / "scripts/ci/build-libxray-awg-apple.sh").read_text()
    for key, value in {"LIBXRAY_COMMIT": COMMIT, "LIBXRAY_SOURCE_SHA256": SOURCE_SHA,
                       "GO_VERSION": "go1.27.1", "AWG_VERSION": AWG}.items():
        require(f"{key}={value}\n" in script, f"Stale Apple {key}")
    require(f"libxray_version={VERSION}" in script, "Stale Apple kernel version")
    require(script.count("-buildmode=c-archive") == 1 and 'cp "${BRIDGE_DIR}/"*.go' in script,
            "Apple must compile libXray and AWG in one Go archive per slice")
    diagnostic_exports = (ROOT / "iosApp/GoBridge/nimbo_diagnostic_cgo.go").read_text(encoding="utf-8")
    symbol_gate = re.search(r"for symbol in (.+); do", script)
    require(symbol_gate is not None, "Missing compiled Apple archive symbol gate")
    for symbol in ("NimboDiagnosticRun", "NimboDiagnosticCancel"):
        require(f"//export {symbol}" in diagnostic_exports, f"Missing diagnostic C ABI {symbol}")
        require(symbol in symbol_gate.group(1).split(), f"Apple archive must verify {symbol}")
    lock = (ROOT / "iosApp/GoBridge/go.mod").read_text()
    for pattern in (r"^go 1\.27\.1$", rf"^\s*github.com/xtls/xray-core {re.escape(CORE)}$",
                    rf"^\s*github.com/amnezia-vpn/amneziawg-go/v3 {re.escape(AWG)}(?: // indirect)?$",
                    r"^replace nimbo/awgcore => ../../tools/native/awg-core$"):
        require(re.search(pattern, lock, re.M), f"Combined Go module pin missing: {pattern}")
    ipa = (ROOT / "scripts/ci/build-unsigned-ios.sh").read_text()
    require(f"libxray_version={VERSION}" in ipa and f"libxray_source_sha256={SOURCE_SHA}" in ipa,
            "Stale IPA kernel metadata")
    print(f"libXray {VERSION}: verified AAR {AAR_SHA}; four ABIs/16 KiB; API 3; Apple source and combined AWG pins")


if __name__ == "__main__":
    main()
