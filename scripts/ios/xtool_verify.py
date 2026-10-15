#!/usr/bin/env python3
"""Verify a re-signable xtool IPA without requiring Xcode or Apple credentials."""
from __future__ import annotations

import argparse
import hashlib
import json
import plistlib
import struct
import zipfile
from pathlib import Path, PurePosixPath


def macho(data: bytes) -> dict:
    if len(data) < 32:
        raise ValueError("Truncated Mach-O executable")
    magic, cpu, _, filetype, count, commands_size, _, _ = struct.unpack_from("<8I", data)
    if (magic, cpu, filetype) != (0xFEEDFACF, 0x100000C, 2):
        raise ValueError("Expected a thin arm64 iOS executable")
    if 32 + commands_size > len(data):
        raise ValueError("Truncated Mach-O load commands")
    platform = None
    minimum = None
    entitlements = {}
    position = 32
    for _ in range(count):
        command, size = struct.unpack_from("<II", data, position)
        if size < 8 or position + size > 32 + commands_size:
            raise ValueError("Invalid Mach-O command size")
        if command == 0x32:
            platform, minimum = struct.unpack_from("<II", data, position + 8)
        if command == 0x1D:
            offset, length = struct.unpack_from("<II", data, position + 8)
            signature = data[offset:offset + length]
            if len(signature) < 12:
                raise ValueError("Truncated code signature")
            blob_magic, blob_size, slots = struct.unpack_from(">III", signature)
            if blob_magic != 0xFADE0CC0 or blob_size > len(signature):
                raise ValueError("Invalid embedded code signature")
            for index in range(slots):
                kind, slot_offset = struct.unpack_from(">II", signature, 12 + index * 8)
                if kind == 5:
                    slot_magic, slot_size = struct.unpack_from(">II", signature, slot_offset)
                    if slot_magic != 0xFADE7171 or slot_offset + slot_size > blob_size:
                        raise ValueError("Invalid XML entitlements blob")
                    entitlements = plistlib.loads(signature[slot_offset + 8:slot_offset + slot_size])
        position += size
    if platform != 2:
        raise ValueError(f"Expected iOS device platform, got {platform}")
    return {"architecture": "arm64", "platform": "iOS", "entitlements": entitlements,
            "minimumOS": f"{minimum >> 16}.{(minimum >> 8) & 255}.{minimum & 255}"}


def verify(ipa: Path, dependencies: Path, version: str, build: str, source_commit: str | None = None) -> dict:
    bundles = {
        "Nimbo.app": ("com.nimbo.resignable", "APPL", "16.0"),
        "Nimbo.app/PlugIns/NimboPacketTunnel.appex": (
            "com.nimbo.resignable.PacketTunnel", "XPC!", "16.0"),
        "Nimbo.app/PlugIns/NimboLiveActivity.appex": (
            "com.nimbo.resignable.LiveActivity", "XPC!", "16.2"),
        "Nimbo.app/PlugIns/NimboControlWidget.appex": (
            "com.nimbo.resignable.ControlWidget", "XPC!", "18.0"),
    }
    result = {}
    with zipfile.ZipFile(ipa) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)):
            raise ValueError("Duplicate ZIP entries")
        if any(PurePosixPath(name).is_absolute() or ".." in PurePosixPath(name).parts for name in names):
            raise ValueError("Unsafe ZIP path")
        if archive.testzip() is not None:
            raise ValueError("ZIP checksum failure")
        for bundle, (bundle_id, package_type, minimum) in bundles.items():
            prefix = "Payload/" + bundle + "/"
            info = plistlib.loads(archive.read(prefix + "Info.plist"))
            for key, expected in {
                "CFBundleIdentifier": bundle_id,
                "CFBundlePackageType": package_type,
                "CFBundleShortVersionString": version.split("-", 1)[0],
                "CFBundleVersion": build,
                "MinimumOSVersion": minimum,
            }.items():
                if str(info.get(key)) != expected:
                    raise ValueError(f"{bundle}: {key}: {info.get(key)!r} != {expected!r}")
            if "$(" in json.dumps(info):
                raise ValueError(f"{bundle}: unresolved Xcode build setting")
            if source_commit and info.get("NimboBuildRevision") != source_commit[:12]:
                raise ValueError(f"{bundle}: source revision differs")
            executable = info["CFBundleExecutable"]
            if PurePosixPath(executable).name != executable:
                raise ValueError("Invalid executable name")
            binary = macho(archive.read(prefix + executable))
            if tuple(map(int, binary["minimumOS"].split("."))) != tuple(map(int, (minimum + ".0").split("."))):
                raise ValueError(f"{bundle}: Mach-O minimum {binary['minimumOS']} differs from plist {minimum}")
            if bundle.endswith("Nimbo.app") or bundle.endswith("NimboPacketTunnel.appex"):
                if "packet-tunnel-provider" not in binary["entitlements"].get(
                        "com.apple.developer.networking.networkextension", []):
                    raise ValueError(f"{bundle}: missing packet-tunnel-provider entitlement")
            if bundle.endswith("NimboControlWidget.appex"):
                if "allow-vpn" not in binary["entitlements"].get(
                        "com.apple.developer.networking.vpn.api", []):
                    raise ValueError("Control Widget: missing VPN management entitlement")
            if bundle.endswith("NimboPacketTunnel.appex"):
                extension = info.get("NSExtension", {})
                if extension.get("NSExtensionPointIdentifier") != "com.apple.networkextension.packet-tunnel":
                    raise ValueError("Packet Tunnel extension type is missing")
                if not extension.get("NSExtensionPrincipalClass", "").endswith(".PacketTunnelProvider"):
                    raise ValueError("Packet Tunnel provider class is missing")
            elif package_type == "XPC!":
                if info.get("NSExtension", {}).get("NSExtensionPointIdentifier") != "com.apple.widgetkit-extension":
                    raise ValueError(f"{bundle}: WidgetKit extension type is missing")
            branding = dependencies / "BrandingResources" / "Assets.car"
            if archive.read(prefix + "Assets.car") != branding.read_bytes():
                raise ValueError(f"{bundle}: compiled branding differs from dependency export")
            result[bundle] = {"bundleID": bundle_id, "minimumOS": minimum, **binary}
        app_info = plistlib.loads(archive.read("Payload/Nimbo.app/Info.plist"))
        if app_info.get("NimboDisplayVersion") != version:
            raise ValueError("App display version differs")
        if not app_info.get("CFBundleIcons", {}).get("CFBundlePrimaryIcon"):
            raise ValueError("App icon metadata is missing")
        icons = list((dependencies / "BrandingResources").glob("AppIcon*.png"))
        if not icons:
            raise ValueError("Export contains no app icon PNGs")
        for icon in icons:
            if archive.read("Payload/Nimbo.app/" + icon.name) != icon.read_bytes():
                raise ValueError(f"App icon differs: {icon.name}")
        fonts = ("golos_regular", "golos_medium", "golos_semibold", "golos_bold",
                 "manrope_regular", "manrope_medium", "manrope_semibold", "manrope_bold", "manrope_extrabold")
        for font in fonts:
            candidates = ["Payload/Nimbo.app/" + prefix + font + ".ttf" for prefix in ("", "Fonts/")]
            font_path = next((name for name in candidates if name in names), None)
            if font_path is None or archive.read(font_path)[:4] not in (b"\x00\x01\x00\x00", b"OTTO"):
                raise ValueError(f"Missing or invalid native font: {font}")
        for name in ("geoip.dat", "geosite.dat"):
            actual = archive.read("Payload/Nimbo.app/PlugIns/NimboPacketTunnel.appex/" + name)
            if not actual or actual != (dependencies / "PacketTunnelResources" / name).read_bytes():
                raise ValueError(f"Missing or altered tunnel database: {name}")
        for name in ("Chromium-LICENSE.txt", "cronet-go-LICENSE.txt", "GPL-3.0.txt",
                     "NaiveProxy-LICENSE.txt", "Naive-Sources.txt"):
            actual = archive.read("Payload/Nimbo.app/NativeNotices/" + name)
            if actual != (dependencies / "NativeNotices" / name).read_bytes():
                raise ValueError(f"Missing or altered native notice: {name}")
    return {"ipa": str(ipa.resolve()), "sha256": hashlib.sha256(ipa.read_bytes()).hexdigest(),
            "version": version, "build": build, "bundles": result,
            "sourceCommit": source_commit,
            "deviceExecutionTested": False, "appleProvisioningTested": False}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("ipa", type=Path)
    parser.add_argument("--dependencies-root", required=True, type=Path)
    parser.add_argument("--version", default="1.3.0-beta.2")
    parser.add_argument("--build", default="181")
    parser.add_argument("--source-commit")
    parser.add_argument("--receipt", type=Path)
    args = parser.parse_args()
    result = verify(args.ipa, args.dependencies_root, args.version, args.build, args.source_commit)
    output = json.dumps(result, ensure_ascii=False, indent=2)
    if args.receipt:
        args.receipt.write_text(output + "\n", encoding="utf-8")
    print(output)


if __name__ == "__main__":
    main()
