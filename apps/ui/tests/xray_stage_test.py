import hashlib
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

spec = importlib.util.spec_from_file_location("stage_xray", Path(__file__).parents[1] / "scripts/stage-xray.py")
stage = importlib.util.module_from_spec(spec)
spec.loader.exec_module(stage)


class XrayStagingTests(unittest.TestCase):
    def test_manifest_has_reviewed_six_platforms(self):
        manifest = json.loads(stage.MANIFEST.read_text())
        self.assertEqual(manifest["version"], "26.9.9")
        self.assertEqual(len(manifest["assets"]), 6)
        self.assertEqual(len({a["target"] for a in manifest["assets"]}), 6)
        for asset in manifest["assets"]:
            self.assertEqual(len(asset["sha256"]), 64)
            self.assertEqual(len(asset["files"]), 3)

    def test_fresh_stale_verified_and_tampered_payloads(self):
        files = {name: ("new " + name).encode() for name in ["xray.exe", "geoip.dat", "geosite.dat"]}
        buffer = io.BytesIO()
        with zipfile.ZipFile(buffer, "w") as archive:
            for name, data in files.items():
                archive.writestr(name, data)
        payload = buffer.getvalue()
        manifest = {"version": "26.9.9", "tag": "v26.9.9", "assets": [{
            "target": "test", "os": "windows", "archive": "test.zip",
            "sha256": hashlib.sha256(payload).hexdigest(),
            "files": {name: hashlib.sha256(data).hexdigest() for name, data in files.items()},
        }]}
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            pin = root / "pins.json"
            pin.write_text(json.dumps(manifest))
            directory = root / "stage"
            with patch.object(stage, "MANIFEST", pin), patch.object(stage.urllib.request, "urlopen", return_value=io.BytesIO(payload)) as network:
                # Fresh staging downloads exact tag and verifies all three files.
                stage.stage("test", directory)
                self.assertIn("/v26.9.9/", network.call_args.args[0])
                self.assertTrue(stage.verify(directory, manifest["assets"][0]))
                network.reset_mock()
                stage.stage("test", directory)
                network.assert_not_called()
                # Old payload cannot survive a verification-only gate.
                (directory / "xray.exe").write_bytes(b"old 26.7.28")
                with self.assertRaises(ValueError):
                    stage.stage("test", directory, verify_only=True)
                self.assertEqual((directory / "xray.exe").read_bytes(), b"old 26.7.28")
                network.return_value = io.BytesIO(payload)
                stage.stage("test", directory)
                self.assertEqual((directory / "xray.exe").read_bytes(), files["xray.exe"])
                # A mismatched archive never changes existing staged bytes.
                (directory / "xray.exe").write_bytes(b"prior payload")
                network.return_value = io.BytesIO(b"bad archive")
                with self.assertRaises(ValueError):
                    stage.stage("test", directory)
                self.assertEqual((directory / "xray.exe").read_bytes(), b"prior payload")

    def test_unsupported_target_fails_before_network(self):
        with patch.object(stage.urllib.request, "urlopen") as network:
            with self.assertRaises(ValueError):
                stage.stage("not-a-target")
            network.assert_not_called()


if __name__ == "__main__":
    unittest.main()
