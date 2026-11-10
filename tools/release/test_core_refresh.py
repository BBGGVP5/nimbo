"""Release-specific core coherence gates; no network access or VPN startup."""
import json
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]
VERSION = "26.11.9"
COMMIT = "5a0f7d8d1c28b34afb5727b04b26a10f6dbac7e3"
SOURCE_SHA = "10b0c62e4a69db89ba9a7dd155980dbd912d49b1c3dd7a1f93de2fededf90ca8"
CORE = "v1.260327.1-0.20261109052709-676eaf66f8f0"
# Desktop downloads release assets, so it must follow a tag that still exists
# upstream; mobile builds from the reviewed commit via Go modules.
DESKTOP_VERSION = "26.11.9"


class CoreRefreshTests(unittest.TestCase):
    def test_android_and_apple_use_the_reviewed_source(self):
        metadata = json.loads((ROOT / "app/src/main/assets/third_party/libxray.json").read_text(encoding="utf-8-sig"))
        self.assertEqual(metadata["version"], VERSION)
        self.assertEqual(metadata["commit"], COMMIT)
        self.assertEqual(metadata["sourceSha256"], SOURCE_SHA)
        self.assertEqual(metadata["xrayCore"], CORE)
        self.assertEqual(metadata["goVersion"], "1.27.3")
        lock = (ROOT / "iosApp/GoBridge/go.mod").read_text(encoding="utf-8")
        self.assertRegex(lock, rf"(?m)^\s*github.com/xtls/xray-core {re.escape(CORE)}$")
        self.assertRegex(lock, r"(?m)^go 1\.27\.3$")
        for module in ("nimbo/awgcore", "nimbo/mihomocore", "nimbo/naivecore"):
            self.assertIn(module + " v0.0.0", lock)
        apple = (ROOT / "scripts/ci/build-libxray-awg-apple.sh").read_text(encoding="utf-8")
        self.assertIn("LIBXRAY_COMMIT=" + COMMIT, apple)
        self.assertIn("LIBXRAY_SOURCE_SHA256=" + SOURCE_SHA, apple)
        self.assertIn("GO_VERSION=go1.27.3", apple)
        merged = (ROOT / "scripts/ci/prepare-mihomo-merged.py").read_text(encoding="utf-8")
        self.assertIn("go('env', 'GOVERSION').strip() != 'go1.27.3'", merged)
        self.assertIn("pinned_go('env','GOVERSION').strip() != pins['toolchain']", merged)

    def test_desktop_uses_a_reviewed_xray_release(self):
        metadata = json.loads((ROOT / "apps/ui/src-tauri/xray-release.json").read_text(encoding="utf-8-sig"))
        self.assertEqual(metadata["version"], DESKTOP_VERSION)
        self.assertEqual(metadata["tag"], "v" + DESKTOP_VERSION)
        self.assertEqual(len(metadata["assets"]), 6)
        for asset in metadata["assets"]:
            self.assertRegex(asset["sha256"], r"^[0-9a-f]{64}$")
            self.assertTrue(any(name in asset["files"] for name in ("xray", "xray.exe")))

    def test_other_release_lines_are_not_downgraded_or_replaced_by_alpha(self):
        mihomo = json.loads((ROOT / "tools/native/mihomo-core/pins.json").read_text(encoding="utf-8-sig"))
        self.assertEqual(mihomo["version"], "v1.19.33")
        for name, version in (("amneziawg", "v3.1.20260828"), ("naiveproxy", "v154.0.8037.49-5")):
            metadata = json.loads((ROOT / f"app/src/main/assets/third_party/{name}.json").read_text(encoding="utf-8-sig"))
            self.assertEqual(metadata["version"], version)

    def test_removed_upstream_ping_location_bindings_have_no_android_callers(self):
        names = ("PingBatchRequest", "PingBatchItemResponse", "getLocationURL", "setLocationURL",
                 "getLocationError", "setLocationError")
        for folder in ("app/src/main", "shared/src"):
            for path in (ROOT / folder).rglob("*.kt"):
                source = path.read_text(encoding="utf-8-sig")
                for name in names:
                    self.assertNotIn(name, source, str(path))


if __name__ == "__main__":
    unittest.main()
