import importlib.util
from pathlib import Path
import unittest
ROOT = Path(__file__).resolve().parents[3]
spec = importlib.util.spec_from_file_location("patcher", ROOT / "scripts/ci/prepare-xray-apple-tun.py")
patcher = importlib.util.module_from_spec(spec)
spec.loader.exec_module(patcher)

class PatchTests(unittest.TestCase):
    def test_unknown_source_is_rejected(self):
        with self.assertRaises(AssertionError): patcher.patch("changed upstream source")

    def test_build_runs_regression_before_shipping_slices(self):
        script = (ROOT / "scripts/ci/build-libxray-awg-apple.sh").read_text()
        self.assertLess(script.index('go mod verify'), script.index('prepare-xray-apple-tun.py'))
        self.assertLess(script.index('TestNimboBorrowedDescriptorReconnect'), script.index('build_slice iphoneos'))
        self.assertIn('-race -count=1 -timeout=90s', script)

if __name__ == "__main__": unittest.main()
