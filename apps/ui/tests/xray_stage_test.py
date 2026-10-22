import hashlib
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest
import urllib.error
from unittest.mock import patch
import zipfile

spec = importlib.util.spec_from_file_location("stage_xray", Path(__file__).parents[1] / "scripts/stage-xray.py")
stage = importlib.util.module_from_spec(spec)
spec.loader.exec_module(stage)


class XrayStagingTests(unittest.TestCase):
    def test_manifest_has_reviewed_six_platforms(self):
        manifest = json.loads(stage.MANIFEST.read_text())
        self.assertEqual(manifest["version"], "26.10.21")
        self.assertEqual(len(manifest["assets"]), 6)
        self.assertEqual(len({a["target"] for a in manifest["assets"]}), 6)
        self.assertTrue(manifest["mirror"].startswith("https://github.com/BBGGVP5/nimbo-cores/releases/download/"))
        geo = manifest["geo"]["urls"]
        self.assertEqual(set(geo), {"geoip.dat", "geosite.dat"})
        for asset in manifest["assets"]:
            self.assertEqual(len(asset["sha256"]), 64)
            self.assertEqual(len(asset["files"]), 3)
        # One geo pin for every platform: the databases Android ships.
        android = Path(__file__).parents[3] / "app/src/main/assets"
        for name in geo:
            expected = hashlib.sha256((android / name).read_bytes()).hexdigest()
            self.assertEqual({a["files"][name] for a in manifest["assets"]}, {expected})

    def test_fresh_stale_verified_and_tampered_payloads(self):
        binary = b"new xray.exe"
        geo = {name: ("new " + name).encode() for name in ["geoip.dat", "geosite.dat"]}
        buffer = io.BytesIO()
        with zipfile.ZipFile(buffer, "w") as archive:
            archive.writestr("xray.exe", binary)
            # Bundled upstream geo must be ignored in favour of the separate pin.
            for name in geo:
                archive.writestr(name, b"upstream " + name.encode())
        payload = buffer.getvalue()
        mirror = "https://mirror.test/core"
        manifest = {"version": "26.10.21", "tag": "v26.10.21", "mirror": mirror,
                    "geo": {"urls": {name: "https://geo.test/" + name for name in geo}},
                    "assets": [{
                        "target": "test", "os": "windows", "archive": "test.zip",
                        "sha256": hashlib.sha256(payload).hexdigest(),
                        "files": {name: hashlib.sha256(data).hexdigest()
                                  for name, data in {"xray.exe": binary, **geo}.items()},
                    }]}
        upstream = "https://github.com/XTLS/Xray-core/releases/download/v26.10.21/test.zip"
        served = {}

        def network(url, timeout):
            if url not in served:
                raise urllib.error.HTTPError(url, 404, "Not Found", None, None)
            return io.BytesIO(served[url])

        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            pin = root / "pins.json"
            pin.write_text(json.dumps(manifest))
            directory = root / "stage"
            with patch.object(stage, "MANIFEST", pin), patch.object(stage.urllib.request, "urlopen", side_effect=network) as calls:
                # Missing mirror falls back to the exact upstream tag and geo source.
                served.update({upstream: payload, **{"https://geo.test/" + n: d for n, d in geo.items()}})
                stage.stage("test", directory)
                requested = [call.args[0] for call in calls.call_args_list]
                self.assertEqual(requested[:2], [mirror + "/test.zip", upstream])
                self.assertIn(mirror + "/geoip.dat", requested)
                self.assertEqual((directory / "geoip.dat").read_bytes(), geo["geoip.dat"])
                self.assertTrue(stage.verify(directory, manifest["assets"][0]))
                calls.reset_mock()
                stage.stage("test", directory)
                calls.assert_not_called()
                # Old payload cannot survive a verification-only gate.
                (directory / "xray.exe").write_bytes(b"old 26.7.28")
                with self.assertRaises(ValueError):
                    stage.stage("test", directory, verify_only=True)
                self.assertEqual((directory / "xray.exe").read_bytes(), b"old 26.7.28")
                # The mirror is preferred; a tampered mirror copy falls back to upstream.
                served.clear()
                served.update({mirror + "/test.zip": b"tampered", upstream: payload,
                               **{mirror + "/" + n: d for n, d in geo.items()}})
                stage.stage("test", directory)
                self.assertEqual((directory / "xray.exe").read_bytes(), binary)
                # A mismatched archive everywhere never changes existing staged bytes.
                (directory / "xray.exe").write_bytes(b"prior payload")
                served[upstream] = b"bad archive"
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
