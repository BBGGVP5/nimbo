#!/usr/bin/env python3
"""Offline bridge/build-source and pinned protobuf staging regressions."""
import hashlib
import importlib.util
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('prepare_mihomo', Path(__file__).with_name('prepare-mihomo-merged.py'))
prepare = importlib.util.module_from_spec(spec)
spec.loader.exec_module(prepare)


class MergedMihomoSourceTests(unittest.TestCase):
    def test_patch_is_not_skipped_inside_parent_checkout(self):
        with tempfile.TemporaryDirectory() as directory:
            parent = Path(directory)
            subprocess.run(['git', 'init', '-q', str(parent)], check=True)
            staged = parent / 'scratch' / 'mihomo'
            staged.mkdir(parents=True)
            source = staged / 'source.go'
            source.write_text('package source\nfunc before() {}\n')
            patch = parent / 'pinned.patch'
            patch.write_text('''diff --git a/source.go b/source.go
--- a/source.go
+++ b/source.go
@@ -1,2 +1,2 @@
 package source
-func before() {}
+func after() {}
''')
            prepare.apply_pinned_patch(staged, patch)
            self.assertIn('func after() {}', source.read_text())

    def test_single_runtime_and_additive_exports(self):
        build = (ROOT / 'scripts/ci/build-libxray-awg-apple.sh').read_text()
        bridge = (ROOT / 'iosApp/GoBridge/nimbo_mihomo_cgo.go').read_text()
        self.assertEqual(build.count('-buildmode=c-archive'), 1)
        self.assertIn('cp "${BRIDGE_DIR}/"*.go "${SOURCE_DIR}/cgo_bridge/"', build)
        for name in ['Invoke', 'Cancel', 'SetSocketProtector', 'StartIOS', 'Free']:
            symbol = 'NimboMihomo' + name + 'V1'
            self.assertIn('//export ' + symbol, bridge)
            self.assertIn(symbol, build)
        self.assertNotIn('func main()', bridge)
        self.assertIn('CGoFree(value)', bridge)
        self.assertIn('C.strnlen(input, mihomoMaxRequest+1)', bridge)
        self.assertIn('context) == 1', bridge)

    def test_root_lock_and_native_gate(self):
        lock = (ROOT / 'iosApp/GoBridge/go.mod').read_text()
        for pin in ['nimbo/mihomocore v0.0.0', 'github.com/metacubex/mihomo v1.19.31',
                    'replace nimbo/mihomocore => ../../tools/native/mihomo-core',
                    'replace google.golang.org/protobuf => ../../tools/native/mihomo-core/.build/protobuf',
                    'github.com/amnezia-vpn/amneziawg-go/v3 v3.1.20260828',
                    'github.com/xtls/xray-core v1.260327.1-0.20260908222543-52a412d9e2f5']:
            self.assertIn(pin, lock)
        adapter = (ROOT / 'iosApp/GoBridge/nimbo_mihomo.go').read_text()
        self.assertIn('return mihomocore.Invoke(input)', adapter)
        self.assertIn('return mihomocore.StartIOS(input, borrowedFD)', adapter)
        self.assertIn('request.Operation != "cancel"', adapter)
        native = (ROOT / 'tools/native/mihomo-core/runtime.go').read_text()
        gate = native.split('func StartIOS(', 1)[1]
        self.assertIn('"PLATFORM_UNAVAILABLE"', gate)

    def test_no_conflict_suppression_or_floating_resolution(self):
        for name in ['scripts/ci/build-libxray-awg-apple.sh', 'scripts/ci/prepare-mihomo-merged.py']:
            code = (ROOT / name).read_text()
            for forbidden in ['GOLANG_PROTOBUF_REGISTRATION_CONFLICT', 'conflictPolicy=warn', '@latest', 'go get']:
                self.assertNotIn(forbidden, code)
        build = (ROOT / 'scripts/ci/build-libxray-awg-apple.sh').read_text()
        self.assertIn('cmp go.sum "${BRIDGE_DIR}/go.sum"', build)
        self.assertIn('test-libxray-cabi.py', build)
        self.assertIn('test-libxray-mihomo-cabi.py', build)
        self.assertIn('nimbo/awgcore nimbo/mihomocore ./cgo_bridge', build)

    def test_lifecycle_patch_is_narrow_and_applied_fail_closed(self):
        patch = ROOT / 'tools/native/mihomo-core/mihomo-session-lifecycle.patch'
        source = patch.read_text()
        for target in ['adapter/provider/healthcheck.go', 'adapter/provider/provider.go',
                       'adapter/outboundgroup/groupbase.go']:
            self.assertIn('diff --git a/' + target + ' b/' + target, source)
        self.assertEqual(source.count('diff --git '), 4)
        reality_patch = (ROOT / 'tools/native/mihomo-core/mihomo-reality-client-version.patch').read_text()
        self.assertEqual(reality_patch.count('diff --git '), 1)
        self.assertIn('diff --git a/component/tls/reality.go b/component/tls/reality.go', reality_patch)
        self.assertIn('hello.SessionId[0] = 26', reality_patch)
        self.assertIn('binary.BigEndian.PutUint32(hello.SessionId[4:]', reality_patch)
        build_helper = (ROOT / 'scripts/ci/prepare-mihomo-merged.py').read_text()
        self.assertIn('stage_mihomo(Path(verified[0][\'Dir\'])', build_helper)
        self.assertIn("'-replace=github.com/metacubex/mihomo=' + mihomo_source.as_posix()", build_helper)
        with tempfile.TemporaryDirectory() as directory:
            tree = Path(directory) / 'tree'
            tree.mkdir()
            target = tree / 'owned.go'
            target.write_text('package lifecycle\nfunc before() {}\n')
            patch_file = Path(directory) / 'owned.patch'
            patch_file.write_text('''diff --git a/owned.go b/owned.go
--- a/owned.go
+++ b/owned.go
@@ -1,2 +1,2 @@
 package lifecycle
-func before() {}
+func after() {}
''')
            prepare.apply_pinned_patch(tree, patch_file)
            self.assertEqual(target.read_text(), 'package lifecycle\nfunc after() {}\n')
            with self.assertRaisesRegex(RuntimeError, 'Could not validate pinned Mihomo patch'):
                prepare.apply_pinned_patch(tree, patch_file)

    def test_only_verified_protobuf_directive_patch_and_reject_tampering(self):
        with tempfile.TemporaryDirectory() as directory:
            original = Path(directory) / 'original'
            original.mkdir()
            (original / 'go.mod').write_bytes(b'module google.golang.org/protobuf\n\ngo 1.20\n')
            (original / 'source.go').write_bytes(b'package protobuf\n')
            pin = {'originalGoModSHA256': prepare.digest(original / 'go.mod'),
                   'patchedGoModSHA256': hashlib.sha256((original / 'go.mod').read_bytes().replace(b'go 1.20', b'go 1.22')).hexdigest()}
            destination = Path(directory) / 'patched'
            prepare.stage_protobuf(original, destination, pin)
            prepare.stage_protobuf(original, destination, pin)
            self.assertEqual((original / 'source.go').read_bytes(), (destination / 'source.go').read_bytes())
            (destination / 'source.go').write_bytes(b'package changed\n')
            with self.assertRaisesRegex(RuntimeError, 'Unexpected protobuf source modification'):
                prepare.stage_protobuf(original, destination, pin)
            (destination / 'added.go').write_bytes(b'package added\n')
            with self.assertRaisesRegex(RuntimeError, 'file set changed'):
                prepare.stage_protobuf(original, destination, pin)


if __name__ == '__main__':
    unittest.main()
