"""Keep runtime, packaging gates and mobile metadata on one reviewed source pin."""
import hashlib
import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]


class MihomoRuntimePins(unittest.TestCase):
    def test_provenance_uses_declared_builder_not_directory_existence(self):
        for name in ["apps/ui/src-tauri/mihomo_build.rs", "apps/service/build.rs"]:
            code = (ROOT / name).read_text(encoding="utf-8-sig")
            self.assertIn('["builderSHA256"].as_str().is_none()', code)
            self.assertNotIn('join("adapter-source").is_dir()', code)
        portable = (ROOT / "scripts/ci/build-mihomo-desktop.py").read_text()
        self.assertIn("builderSHA256=digest(Path(__file__))", portable)

    def test_all_active_runtime_and_packaging_gates_agree(self):
        pin = json.loads((ROOT / "tools/native/mihomo-core/pins.json").read_text())
        for name in [
            "tools/native/mihomo-core/runtime.go", "crates/mihomo/src/profiles.rs",
            "apps/ui/src-tauri/mihomo_build.rs", "apps/service/build.rs",
            "apps/installer/src-tauri/mihomo_bundle.rs", "apps/ui/src-tauri/native/stage-mihomo.ps1",
            "iosApp/Shared/NimboMihomoSessionPolicy.swift",
            "shared/src/commonMain/kotlin/com/danila/nimbo/shared/mihomo/MihomoGraphValidator.kt",
        ]:
            with self.subTest(path=name):
                code = (ROOT / name).read_text(encoding="utf-8-sig")
                self.assertIn(pin["version"], code)
                self.assertIn(pin["commit"], code)
        for name in ["app/src/main/assets/third_party/mihomo/pins.json",
                     "apps/ui/src-tauri/resources/mihomo/windows-x64/pins.json"]:
            self.assertEqual(json.loads((ROOT / name).read_text(encoding="utf-8-sig")), pin)

    def test_windows_payload_and_android_notices_are_not_version_labels_only(self):
        folder = ROOT / "apps/ui/src-tauri/resources/mihomo/windows-x64"
        manifest = json.loads((folder / "build-manifest.json").read_text(encoding="utf-8-sig"))
        pin = json.loads((folder / "pins.json").read_text(encoding="utf-8-sig"))
        self.assertEqual(manifest["coreVersion"], pin["version"])
        self.assertEqual(manifest["coreCommit"], pin["commit"])
        self.assertEqual(hashlib.sha256((folder / "nimbo-mihomo.exe").read_bytes()).hexdigest(), manifest["sha256"])
        source = ROOT / "tools/native/mihomo-core/source-license-manifest.json"
        mobile = ROOT / "app/src/main/assets/third_party/mihomo/source-license-manifest.json"
        self.assertEqual(json.loads(source.read_text()), json.loads(mobile.read_text()))
        for module in json.loads(mobile.read_text())["modules"]:
            for notice in module.get("notices", []):
                file = mobile.parent / notice["path"]
                # Go emits a host-byte notice manifest. Git normalizes text
                # notices in a Linux checkout; only this line-ending change is
                # allowed here. Release staging still requires exact bytes.
                data = file.read_bytes(); lf = data.replace(b"\r\n", b"\n")
                digests = {hashlib.sha256(value).hexdigest() for value in (data, lf, lf.replace(b"\n", b"\r\n"))}
                self.assertIn(notice["sha256"], digests, str(file))


if __name__ == "__main__":
    unittest.main()
