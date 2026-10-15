import importlib.util
import plistlib
import struct
import tempfile
import unittest
import zipfile
from pathlib import Path

SPEC = importlib.util.spec_from_file_location("xtool_verify", Path(__file__).with_name("xtool_verify.py"))
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


def executable(entitlements, platform=2, minimum="16.0"):
    xml = plistlib.dumps(entitlements)
    slot = struct.pack(">II", 0xFADE7171, 8 + len(xml)) + xml
    signature = struct.pack(">IIIII", 0xFADE0CC0, 20 + len(slot), 1, 5, 20) + slot
    major, minor = map(int, minimum.split("."))
    version = struct.pack("<IIIIII", 0x32, 24, platform, (major << 16) | (minor << 8), 27 << 16, 0)
    offset = 32 + len(version) + 16
    command = struct.pack("<IIII", 0x1D, 16, offset, len(signature))
    header = struct.pack("<8I", 0xFEEDFACF, 0x100000C, 0, 2, 2, 40, 0, 0)
    return header + version + command + signature


class ExecutableContracts(unittest.TestCase):
    def test_reads_embedded_entitlements(self):
        entitlements = {"com.apple.developer.networking.networkextension": ["packet-tunnel-provider"]}
        self.assertEqual(MODULE.macho(executable(entitlements))["entitlements"], entitlements)

    def test_rejects_simulator_and_truncation(self):
        for data in (executable({}, platform=7), executable({})[:40], b"!<arch>\n"):
            with self.subTest(length=len(data)), self.assertRaises((ValueError, struct.error)):
                MODULE.macho(data)


class ArchiveContracts(unittest.TestCase):
    def test_complete_package_and_missing_vpn_entitlement(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "BrandingResources").mkdir()
            (root / "BrandingResources/Assets.car").write_bytes(b"fixture branding")
            (root / "BrandingResources/AppIcon60x60@2x.png").write_bytes(b"fixture icon")
            (root / "PacketTunnelResources").mkdir()
            for name in ("geoip.dat", "geosite.dat"):
                (root / "PacketTunnelResources" / name).write_bytes(name.encode())
            (root / "NativeNotices").mkdir()
            notices = ("Chromium-LICENSE.txt", "cronet-go-LICENSE.txt", "GPL-3.0.txt",
                       "NaiveProxy-LICENSE.txt", "Naive-Sources.txt")
            for name in notices:
                (root / "NativeNotices" / name).write_bytes(name.encode())
            for omit_entitlement in (False, True):
                ipa = root / (str(omit_entitlement) + ".ipa")
                with zipfile.ZipFile(ipa, "w") as archive:
                    for name, minimum, suffix in (
                        ("Nimbo", "16.0", ""), ("NimboPacketTunnel", "16.0", ".PacketTunnel"),
                        ("NimboLiveActivity", "16.2", ".LiveActivity"),
                        ("NimboControlWidget", "18.0", ".ControlWidget"),
                    ):
                        prefix = "Payload/Nimbo.app/" + ("PlugIns/" + name + ".appex/" if suffix else "")
                        info = {"CFBundleIdentifier": "com.nimbo.resignable" + suffix,
                                "CFBundlePackageType": "XPC!" if suffix else "APPL",
                                "CFBundleShortVersionString": "1.3.0", "CFBundleVersion": "181",
                                "CFBundleExecutable": name, "MinimumOSVersion": minimum}
                        entitlements = {}
                        if suffix == ".PacketTunnel":
                            info["NSExtension"] = {"NSExtensionPointIdentifier": "com.apple.networkextension.packet-tunnel",
                                                   "NSExtensionPrincipalClass": name + ".PacketTunnelProvider"}
                        elif suffix:
                            info["NSExtension"] = {"NSExtensionPointIdentifier": "com.apple.widgetkit-extension"}
                        else:
                            info["NimboDisplayVersion"] = "1.3.0-beta.2"
                            info["CFBundleIcons"] = {"CFBundlePrimaryIcon": {"CFBundleIconName": "AppIcon"}}
                        if suffix in ("", ".PacketTunnel") and not omit_entitlement:
                            entitlements["com.apple.developer.networking.networkextension"] = ["packet-tunnel-provider"]
                        if suffix == ".ControlWidget":
                            entitlements["com.apple.developer.networking.vpn.api"] = ["allow-vpn"]
                        archive.writestr(prefix + "Info.plist", plistlib.dumps(info))
                        archive.writestr(prefix + name, executable(entitlements, minimum=minimum))
                        archive.writestr(prefix + "Assets.car", b"fixture branding")
                        if suffix == ".PacketTunnel":
                            for resource in ("geoip.dat", "geosite.dat"):
                                archive.writestr(prefix + resource, resource.encode())
                        if not suffix:
                            archive.writestr(prefix + "AppIcon60x60@2x.png", b"fixture icon")
                            for font in ("golos_regular", "golos_medium", "golos_semibold", "golos_bold",
                                         "manrope_regular", "manrope_medium", "manrope_semibold", "manrope_bold", "manrope_extrabold"):
                                archive.writestr(prefix + font + ".ttf", b"\x00\x01\x00\x00fixture font")
                            for notice in notices:
                                archive.writestr(prefix + "NativeNotices/" + notice, notice.encode())
                if omit_entitlement:
                    with self.assertRaisesRegex(ValueError, "missing packet-tunnel-provider"):
                        MODULE.verify(ipa, root, "1.3.0-beta.2", "181")
                else:
                    self.assertEqual(len(MODULE.verify(ipa, root, "1.3.0-beta.2", "181")["bundles"]), 4)

    def test_plain_test_ipa_is_not_accepted_as_nimbo(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            ipa = root / "probe.ipa"
            with zipfile.ZipFile(ipa, "w") as archive:
                archive.writestr("Payload/Nimbo.app/Info.plist", plistlib.dumps({
                    "CFBundleIdentifier": "com.example.NimboXtoolProbe"}))
            with self.assertRaisesRegex(ValueError, "CFBundleIdentifier"):
                MODULE.verify(ipa, root, "1.3.0-beta.2", "181")

    def test_rejects_unsafe_archive_paths(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            ipa = root / "unsafe.ipa"
            with zipfile.ZipFile(ipa, "w") as archive:
                archive.writestr("Payload/../../unexpected", b"data")
            with self.assertRaisesRegex(ValueError, "Unsafe ZIP"):
                MODULE.verify(ipa, root, "1.3.0-beta.2", "181")


if __name__ == "__main__":
    unittest.main()
