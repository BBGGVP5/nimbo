import hashlib
import importlib.util
import json
from pathlib import Path
import tarfile
import tempfile
import unittest

SPEC = importlib.util.spec_from_file_location("xtool_dependencies", Path(__file__).with_name("xtool_dependencies.py"))
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class DependencyContracts(unittest.TestCase):
    def fixture(self, root):
        source = root / "source"
        source.mkdir()
        (source / "input.swift").write_bytes(b"actual source")
        dependencies = root / "dependencies"
        dependencies.mkdir()
        (dependencies / "payload.a").write_bytes(b"compiled archive")
        manifest = {
            "kind": "nimbo-ios-xtool-dependencies", "schema_version": 1,
            "git_commit": "a" * 40, "git_dirty": False, "slices": ["iphoneos-arm64"],
            "version": "1.3.0-beta.2", "build_number": 181, "core_pins": {},
            "payload_files": {"payload.a": {"type": "file", "sha256": MODULE.digest(dependencies / "payload.a")}},
            "source_fingerprints": {"source": {"files": {"input.swift": MODULE.digest(source / "input.swift")}}},
        }
        (dependencies / "manifest.json").write_text(json.dumps(manifest))
        return source, dependencies

    def test_verifies_payload_and_source_together(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source, dependencies = self.fixture(root)
            self.assertEqual(MODULE.verify(dependencies, source, "a" * 40)["payloadFilesVerified"], 1)
            (source / "input.swift").write_bytes(b"new source")
            with self.assertRaisesRegex(ValueError, "different source"):
                MODULE.verify(dependencies, source, "a" * 40)

    def test_rejects_changed_payload_and_wrong_commit(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source, dependencies = self.fixture(root)
            with self.assertRaisesRegex(ValueError, "requested clean source"):
                MODULE.verify(dependencies, source, "b" * 40)
            (dependencies / "payload.a").write_bytes(b"stale archive")
            with self.assertRaisesRegex(ValueError, "altered dependency"):
                MODULE.verify(dependencies, source, "a" * 40)

    def test_unpack_checks_checksum_and_refuses_existing_output(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source, dependencies = self.fixture(root)
            archive = root / "dependencies.tar.gz"
            with tarfile.open(archive, "w:gz") as stream:
                stream.add(dependencies, arcname="dependencies")
            checksum = root / "dependencies.sha256"
            checksum.write_text("0" * 64)
            output = root / "unpacked"
            with self.assertRaisesRegex(ValueError, "SHA-256 mismatch"):
                MODULE.unpack(archive, checksum, output)
            self.assertFalse(output.exists())
            checksum.write_text(MODULE.digest(archive) + "  dependencies.tar.gz\n")
            extracted = MODULE.unpack(archive, checksum, output)
            MODULE.verify(extracted, source, "a" * 40)
            with self.assertRaisesRegex(ValueError, "already exists"):
                MODULE.unpack(archive, checksum, output)

    def test_rejects_manifest_traversal(self):
        with tempfile.TemporaryDirectory() as directory:
            for name in ("../input", "/etc/passwd", "a\\..\\input"):
                with self.subTest(name=name), self.assertRaises(ValueError):
                    MODULE.scoped(Path(directory), name)


if __name__ == "__main__":
    unittest.main()
