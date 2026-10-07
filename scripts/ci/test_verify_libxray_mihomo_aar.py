import importlib.util
from pathlib import Path
import struct
import unittest

spec = importlib.util.spec_from_file_location("verify_aar", Path(__file__).with_name("verify-libxray-mihomo-aar.py"))
verify = importlib.util.module_from_spec(spec)
spec.loader.exec_module(verify)


def elf(elf_class, machine, alignment=16384):
    data = bytearray(256)
    data[:6] = b"\x7fELF" + bytes((elf_class, 1))
    struct.pack_into("<H", data, 18, machine)
    struct.pack_into("<Q" if elf_class == 2 else "<I", data, 32 if elf_class == 2 else 28, 64)
    struct.pack_into("<HH", data, 54 if elf_class == 2 else 42, 56 if elf_class == 2 else 32, 1)
    struct.pack_into("<I", data, 64, 1)
    struct.pack_into("<Q" if elf_class == 2 else "<I", data, 64 + (48 if elf_class == 2 else 28), alignment)
    return data


class ElfValidationTest(unittest.TestCase):
    def test_four_abis(self):
        for abi, (elf_class, machine) in verify.ABIS.items():
            with self.subTest(abi=abi):
                verify.check_elf(elf(elf_class, machine), abi)

    def test_4k_alignment_rejected(self):
        with self.assertRaisesRegex(ValueError, "16 KiB"):
            verify.check_elf(elf(2, 183, 4096), "arm64-v8a")

    def test_wrong_architecture_rejected(self):
        with self.assertRaisesRegex(ValueError, "machine"):
            verify.check_elf(elf(2, 62), "arm64-v8a")

    def test_truncated_program_table_rejected(self):
        with self.assertRaisesRegex(ValueError, "header table"):
            verify.check_elf(elf(2, 183)[:80], "arm64-v8a")

    def test_non_power_of_two_alignment_rejected(self):
        with self.assertRaisesRegex(ValueError, "16 KiB"):
            verify.check_elf(elf(2, 183, 20000), "arm64-v8a")


class BuildInfoValidationTest(unittest.TestCase):
    def test_pinned_replaced_module_without_checksum_is_detected(self):
        metadata = """path example\ndep\tgithub.com/metacubex/mihomo\tv1.19.32\n=>\tC:/stage/dependencies/mihomo\t(devel)\n"""
        self.assertEqual(
            verify.dependency(metadata, "github.com/metacubex/mihomo", "v1.19.32"),
            "C:/stage/dependencies/mihomo",
        )

    def test_wrong_version_is_rejected(self):
        metadata = "dep\tgithub.com/metacubex/mihomo\tv1.19.30\th1:sum\n"
        self.assertIsNone(verify.dependency(metadata, "github.com/metacubex/mihomo", "v1.19.32"))


if __name__ == "__main__":
    unittest.main()
