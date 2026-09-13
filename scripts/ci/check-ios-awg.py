#!/usr/bin/env python3
"""Cross-platform iOS integration contracts; --swift also runs native tests."""
import argparse
import pathlib
import re
import shutil
import subprocess
import sys
import tempfile

ROOT = pathlib.Path(__file__).resolve().parents[2]


def read(path):
    return (ROOT / path).read_text(encoding="utf-8")


def check(condition, message):
    if not condition:
        raise AssertionError(message)


def check_go_process_links(project):
    # App diagnostics and the VPN extension are distinct OS processes. Both link
    # the same combined archive once; neither may load a second Go runtime.
    targets = project.split("\ntargets:\n", 1)[1].split("\nschemes:", 1)[0]
    for name in ("Nimbo", "NimboPacketTunnel"):
        block = re.search(rf"(?ms)^  {name}:\n(.*?)(?=^  \w+:\n|\Z)", targets)
        check(block is not None, f"Missing process target {name}")
        text = block.group(1)
        check(text.count("Vendor/LibXray.xcframework") == 1,
              f"{name} must link exactly one combined Go runtime")
        for sdk in ("libresolv.tbd", "Security.framework", "CoreFoundation.framework"):
            check(f"- sdk: {sdk}" in text, f"{name} missing native linker dependency {sdk}")
    check(project.count("Vendor/LibXray.xcframework") == 2,
          "Only the app and tunnel processes may link the combined Go archive")
    check("AWG.xcframework" not in project, "Never link a second independent Go runtime")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--swift", action="store_true")
    args = parser.parse_args()
    project = read("iosApp/project.yml")
    provider = read("iosApp/PacketTunnel/PacketTunnelProvider.swift")
    build = read("scripts/ci/build-libxray-awg-apple.sh")
    bridge = read("iosApp/GoBridge/nimbo_awg.go")
    swift_bridge = read("iosApp/PacketTunnel/AmneziaWGBridge.swift")
    lock = read("iosApp/GoBridge/go.mod")
    check_go_process_links(project)
    for symbol in ("NimboAWGStart", "NimboAWGStop", "NimboAWGStats"):
        check(f"//export {symbol}" in bridge and symbol in swift_bridge, f"Missing C ABI: {symbol}")
    check("defer { CGoFree(pointer) }" in swift_bridge, "C response ownership must be released")
    check('"127.0.0.1:\\(port)"' in swift_bridge and '"users"' in swift_bridge, "SOCKS egress must use local authentication")
    check("self.awg.start(awgConfiguration)" in provider, "AWG must start before Xray")
    check("tunnelProtocol.includeAllNetworks = false" in read("iosApp/Nimbo/VpnController.swift"), "Provider sockets need physical-network exemption")
    check(provider.count("setTunnelNetworkSettings(") == 1, "Never reinstall live TUN settings")
    check("self.awg.suspend()" in provider and "try awg.restart()" in provider, "Sleep/wake lifecycle is required")
    check("NimboAWGConfiguration.parseIfPresent(payload)" in read("iosApp/Nimbo/NimboSubscriptionRepository.swift"), "Raw import must dispatch before share-link conversion")
    check('@SerialName("amneziawg")' in read("shared/src/commonMain/kotlin/com/danila/nimbo/shared/subscription/SubscriptionModels.kt"), "Compose must decode native AWG profiles")
    check("v3.1.20260828" in lock, "AWG version must be pinned")
    check("v0.0.0-20260122175437-89a5d21be8f0" in lock, "Combined module must retain Xray's compatible gVisor")
    check("070a5b573f5a907d31dc23064c89a8cac2cbf9a8baf7df64c42b9cac78b50d4b" in build, "Source checksum is required")
    check("-mod=readonly" in build and "go mod verify" in build, "Locked dependency verification is required")
    check("go get" not in build and "@latest" not in build, "Build must not resolve floating dependencies")
    subprocess.run([sys.executable, str(ROOT / "scripts/ci/test-ios-release.py")], check=True, cwd=ROOT)
    subprocess.run(["go", "test", str(ROOT / "iosApp/GoBridge/nimbo_awg_stats.go"),
                    str(ROOT / "iosApp/GoBridge/nimbo_awg_test.go")], check=True, cwd=ROOT)
    bash = shutil.which("bash")
    if pathlib.Path("C:/Program Files/Git/bin/bash.exe").exists():
        bash = "C:/Program Files/Git/bin/bash.exe"
    if bash:
        for path in ("scripts/ci/build-libxray-awg-apple.sh", "scripts/ci/prepare-libxray-apple.sh", "scripts/ci/build-unsigned-ios.sh"):
            subprocess.run([bash, "-n", path], check=True, cwd=ROOT)
    if args.swift:
        with tempfile.TemporaryDirectory(prefix="nimbo-awg-swift-") as directory:
            executable = str(pathlib.Path(directory) / "awg-tests")
            subprocess.run(["swiftc", str(ROOT / "iosApp/Shared/NimboAWGConfiguration.swift"),
                            str(ROOT / "iosApp/Tests/AWGConfigurationTests.swift"), "-o", executable], check=True)
            subprocess.run([executable], check=True)
    print("iOS AWG source/build contracts passed" + ("; Swift execution passed" if args.swift else "; Swift/device execution not run"))


if __name__ == "__main__":
    main()
