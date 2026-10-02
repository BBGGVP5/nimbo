import hashlib
import io
import tarfile
import unittest
import zipfile

from check_linux_payload import archive_helper, cpio_entries, tar_entries, verify_helper


def elf(machine):
    data = bytearray(64)
    data[:6] = b"\x7fELF\x02\x01"
    data[18:20] = machine.to_bytes(2, "little")
    return bytes(data)


def cpio_entry(name, data, mode=0o100755):
    encoded = name.encode() + b"\0"
    fields = [1, mode, 0, 0, 1, 0, len(data), 0, 0, 0, 0, len(encoded), 0]
    header = b"070701" + "".join(f"{field:08x}" for field in fields).encode()
    entry = header + encoded
    entry += b"\0" * (-len(entry) % 4)
    entry += data
    return entry + b"\0" * (-len(data) % 4)


class LinuxPayloadTests(unittest.TestCase):
    def test_archive_exact_bytes_and_inventory(self):
        for names in (["nimbo-svc"], ["../nimbo-svc"], ["nimbo-svc", "extra"]):
            stream = io.BytesIO()
            with zipfile.ZipFile(stream, "w") as archive:
                for name in names:
                    archive.writestr(name, elf(62))
            if names == ["nimbo-svc"]:
                self.assertEqual(archive_helper(stream.getvalue()), elf(62))
            else:
                with self.assertRaises(AssertionError):
                    archive_helper(stream.getvalue())

    def test_both_elf_targets_and_wrong_permissions_digest_architecture(self):
        for target, machine in (("x86_64-unknown-linux-gnu", 62), ("aarch64-unknown-linux-gnu", 183)):
            data = elf(machine)
            manifest = {"target": target, "sha256": hashlib.sha256(data).hexdigest()}
            verify_helper(data, 0o100755, manifest)
            for invalid_data, mode in ((data, 0o644), (data + b"changed", 0o755), (elf(0), 0o755)):
                with self.assertRaises(AssertionError):
                    verify_helper(invalid_data, mode, manifest)

    def test_deb_tar_contents_without_filesystem_extraction(self):
        stream = io.BytesIO()
        with tarfile.open(fileobj=stream, mode="w") as archive:
            entry = tarfile.TarInfo("./usr/bin/nimbo-svc")
            entry.mode, entry.size = 0o755, 64
            archive.addfile(entry, io.BytesIO(elf(62)))
        self.assertEqual(tar_entries(stream.getvalue())["usr/bin/nimbo-svc"], (0o755, elf(62)))

    def test_rpm_newc_contents_preserve_mode_and_bytes(self):
        data = cpio_entry("./usr/bin/nimbo-svc", elf(183)) + cpio_entry("TRAILER!!!", b"", 0)
        self.assertEqual(cpio_entries(data)["usr/bin/nimbo-svc"], (0o100755, elf(183)))

    def test_rpm_truncated_missing_trailer_and_corrupt_header_are_rejected(self):
        data = cpio_entry("./usr/bin/nimbo-svc", elf(62))
        for invalid in (data, data[:-20], b"broken" + data[6:]):
            with self.assertRaises(AssertionError):
                cpio_entries(invalid)


if __name__ == "__main__":
    unittest.main()
