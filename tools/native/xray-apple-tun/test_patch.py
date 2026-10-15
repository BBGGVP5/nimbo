import importlib.util
from pathlib import Path
import unittest
import sys
ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / 'scripts/ci'))
spec = importlib.util.spec_from_file_location("patcher", ROOT / "scripts/ci/prepare-xray-apple-tun.py")
patcher = importlib.util.module_from_spec(spec)
spec.loader.exec_module(patcher)

class PatchTests(unittest.TestCase):
    def test_unknown_source_is_rejected(self):
        with self.assertRaises(ValueError): patcher.validate_upstream_ownership("changed upstream source")

    def test_upstream_duplicate_is_accepted_without_a_second_patch(self):
        source = ('fd, err := unix.FcntlInt(uintptr(providedFd), unix.F_DUPFD_CLOEXEC, 0)\n'
                  'if err = unix.SetNonblock(fd, true); err != nil {\n\t\t\t_ = unix.Close(fd)\n'
                  'tunFile: os.NewFile(uintptr(fd), "utun")\n'
                  'return xerrors.Combine(routeErr, t.tunFile.Close())')
        self.assertIsNone(patcher.validate_upstream_ownership(source))
        for fragment in ('unix.F_DUPFD_CLOEXEC', '_ = unix.Close(fd)', 't.tunFile.Close()'):
            with self.subTest(fragment=fragment), self.assertRaises(ValueError):
                patcher.validate_upstream_ownership(source.replace(fragment, 'removed'))
        script = (ROOT / "scripts/ci/prepare-xray-apple-tun.py").read_text()
        self.assertNotIn('unix.Dup(fd)', script)
        self.assertNotIn('target.write_text', script)
        self.assertIn("verified.digest(original / 'proxy/tun/tun_darwin.go') != HASH", script)

    def test_build_runs_regression_before_shipping_slices(self):
        script = (ROOT / "scripts/ci/build-libxray-awg-apple.sh").read_text()
        self.assertLess(script.index('go mod verify'), script.index('prepare-xray-apple-tun.py'))
        self.assertLess(script.index('TestNimboBorrowedDescriptorReconnect'), script.index('build_slice iphoneos'))
        self.assertIn('-race -count=1 -timeout=90s', script)

if __name__ == "__main__": unittest.main()
