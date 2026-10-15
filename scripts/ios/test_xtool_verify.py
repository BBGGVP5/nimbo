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


def executable(entitlements, platform=2):
    xml = plistlib.dumps(entitlements)
    slot = struct.pack(">II", 0xFADE7171, 8 + len(xml)) + xml
    signature = struct.pack(">IIIII", 0xFADE0CC0, 20 + len(slot), 1, 5, 20) + slot
    version = struct.pack("<IIIIII", 0x32, 24, platform, 16 << 16, 27 << 16, 0)
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
