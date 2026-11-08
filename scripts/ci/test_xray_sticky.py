"""Pinned source-patch and packaging contracts; no system VPN or cache edits."""
from pathlib import Path
import unittest
import tempfile

from prepare_xray_sticky import patch_router, patch_observer, apply_sticky_patch, PINNED_HASHES

ROOT = Path(__file__).resolve().parents[2]


class StickyPatchTests(unittest.TestCase):
    def test_router_patch_is_exact_and_keeps_normal_strategy(self):
        source = '\tcase "leastping":\n\t\treturn &Balancer{\n\t\t\tselectors:   br.OutboundSelector,\n\t\t\tstrategy:    &LeastPingStrategy{},'
        output = patch_router(source)
        self.assertIn('var strategy BalancingStrategy = &LeastPingStrategy{}', output)
        for condition in ['br.Tag == "nimbo-auto-sticky-v1"', 'len(br.OutboundSelector) == 1',
                          'br.OutboundSelector[0] == "proxy/"', 'br.FallbackTag == "proxy/0"']:
            self.assertIn(condition, output)
        self.assertIn('strategy:    strategy,', output)
        with self.assertRaises(ValueError):
            patch_router(output)
        with self.assertRaises(ValueError):
            patch_router(source + source)

    def test_observation_snapshot_is_locked_and_deep_copied(self):
        source = 'func (o *Observer) GetObservation(ctx context.Context) (proto.Message, error) {\n\treturn &ObservationResult{Status: o.status}, nil\n}'
        output = patch_observer(source)
        self.assertIn('o.statusLock.Lock()', output)
        self.assertIn('defer o.statusLock.Unlock()', output)
        self.assertIn('proto.Clone(&ObservationResult{Status: o.status})', output)
        self.assertLess(output.index('o.statusLock.Lock()'), output.index('proto.Clone'))
        with self.assertRaises(ValueError):
            patch_observer(output)

    def test_unexpected_source_is_rejected_before_any_write(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for relative in PINNED_HASHES:
                target = root / relative
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(b'unknown upstream')
            before = {p.relative_to(root): p.read_bytes() for p in root.rglob('*') if p.is_file()}
            with self.assertRaisesRegex(ValueError, 'Unexpected pinned Xray source'):
                apply_sticky_patch(root)
            after = {p.relative_to(root): p.read_bytes() for p in root.rglob('*') if p.is_file()}
            self.assertEqual(before, after)

    def test_module_cache_is_never_a_patch_target(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory) / 'pkg/mod/fixture'
            root.mkdir(parents=True)
            with self.assertRaisesRegex(ValueError, 'Never patch a Go module cache'):
                apply_sticky_patch(root)
            self.assertEqual(list(root.iterdir()), [])

    def test_apple_package_checks_real_core_before_archive(self):
        source = (ROOT / 'scripts/ci/build-libxray-awg-apple.sh').read_text(encoding='utf-8-sig')
        command = "go test -mod=readonly -race -count=1 -timeout=120s -run '^TestNimboSticky' github.com/xtls/xray-core/app/router github.com/xtls/xray-core/app/observatory"
        self.assertIn(command, source)
        self.assertLess(source.index(command), source.index('build_slice()'))
        stage = (ROOT / 'scripts/ci/prepare-xray-apple-tun.py').read_text(encoding='utf-8-sig')
        self.assertIn('apply_sticky_patch(destination)', stage)
        self.assertLess(stage.index('apply_sticky_patch(destination)'), stage.index("'-replace='"))


if __name__ == '__main__':
    unittest.main(verbosity=2)
