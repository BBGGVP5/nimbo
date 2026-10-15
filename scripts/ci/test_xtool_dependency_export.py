#!/usr/bin/env python3
"""Exercise selection and export validation without Apple/Gradle/WSL builds."""
import hashlib
import json
import os
from pathlib import Path
import plistlib
import shutil
import subprocess
import sys
import tarfile
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
NATIVE = (ROOT / 'scripts/ci/build-libxray-awg-apple.sh').read_text(encoding='utf-8')
EXPORTER = ROOT / 'scripts/ci/export-ios-xtool-dependencies.sh'
EXPORT = EXPORTER.read_text(encoding='utf-8')
VALIDATION = compile(EXPORT.split("<<'PY'\n", 1)[1].split('\nPY\n', 1)[0], str(EXPORTER), 'exec')
BASH = shutil.which('bash')
if Path('C:/Program Files/Git/bin/bash.exe').exists():
    BASH = 'C:/Program Files/Git/bin/bash.exe'


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)


def write_plist(path, value):
    write(path, plistlib.dumps(value))


class ExportFixture:
    def __init__(self, temporary):
        self.root = Path(temporary) / 'source'
        self.stage = Path(temporary) / 'dependencies'
        self.stage.mkdir()
        self.commit = '0123456789abcdef' * 2 + '01234567'
        for name in ('shared/src/commonMain/Fixture.kt', 'shared/build.gradle.kts',
                     'gradle/libs.versions.toml', 'gradle/wrapper/gradle-wrapper.properties',
                     'gradlew', 'gradle.properties', 'build.gradle.kts', 'settings.gradle.kts',
                     'tools/native/fixture.go', 'scripts/ci/export-ios-xtool-dependencies.sh',
                     'iosApp/Shared/Fixture.swift', 'iosApp/PacketTunnel/Fixture.swift',
                     'iosApp/NativeNotices/fixture.txt', 'iosApp/Branding/Branding.xcassets/Contents.json',
                     'iosApp/Nimbo/Assets.xcassets/AppIcon.appiconset/Contents.json',
                     'apps/ui/scripts/stage-xray.py'):
            write(self.root / name, b'fixture source\n')
        write(self.root / 'iosApp/GoBridge/go.mod',
              b'module fixture\nrequire (\n\tgithub.com/xtls/xray-core v1.260327.1-0.20261010092107-701af60772cd\n)\n')
        write(self.root / 'tools/native/mihomo-core/pins.json', b'{"version": "v1.19.32"}')
        self.native = {
            'apple_slices': 'iphoneos-arm64', 'swift_awg_link_check': 'iphoneos-arm64',
            'libxray_version': '26.10.10', 'libxray_commit': 'a' * 40,
            'libxray_source_sha256': 'b' * 64, 'xray_version': '26.10.10',
            'awg_version': 'v3.1.20260828', 'mihomo_version': 'v1.19.32',
            'naive_version': '150.0.7871.63', 'cronet_go_commit': 'c' * 40,
            'go_version': 'go1.27.1',
        }
        self.write_receipt()
        write(self.stage / 'NativeBuildReceipts/mihomo-source-verification.json', b'{"verified": true}')
        for framework, binary, extra in (
            ('LibXray', 'libXray.a', {'HeadersPath': 'Headers'}),
            ('NimboShared', 'NimboShared.framework', {}),
        ):
            directory = self.stage / (framework + '.xcframework')
            library = {'LibraryIdentifier': 'ios-arm64', 'LibraryPath': binary,
                       'SupportedPlatform': 'ios', 'SupportedArchitectures': ['arm64'], **extra}
            write_plist(directory / 'Info.plist', {'AvailableLibraries': [library]})
            if framework == 'LibXray':
                for name in ('libXray.a', 'Headers/libXray.h', 'Headers/module.modulemap'):
                    write(directory / 'ios-arm64' / name, b'native fixture')
            else:
                for name in ('NimboShared', 'Headers/NimboShared.h', 'Modules/module.modulemap'):
                    write(directory / 'ios-arm64' / binary / name, b'Kotlin fixture')
        for name in ('Chromium-LICENSE.txt', 'cronet-go-LICENSE.txt', 'GPL-3.0.txt',
                     'NaiveProxy-LICENSE.txt', 'Naive-Sources.txt'):
            write(self.stage / 'NativeNotices' / name, b'notice fixture')
        geo_files = {}
        for name in ('geoip.dat', 'geosite.dat'):
            content = ('geo fixture ' + name).encode()
            write(self.stage / 'PacketTunnelResources' / name, content)
            geo_files[name] = hashlib.sha256(content).hexdigest()
        pin = {'version': '26.10.10', 'assets': [
            {'target': 'x86_64-unknown-linux-gnu', 'files': geo_files}]}
        write(self.root / 'apps/ui/src-tauri/xray-release.json', json.dumps(pin).encode())
        write(self.stage / 'BrandingResources/Assets.car', b'compiled branding fixture')
        write(self.stage / 'BrandingResources/AppIcon60x60@2x.png', b'compiled icon fixture')
        write_plist(self.stage / 'BrandingResources/BrandingPartialInfo.plist', {
            'CFBundleIcons': {'CFBundlePrimaryIcon': {'CFBundleIconName': 'AppIcon'}},
        })

    def write_receipt(self):
        write(self.stage / 'NativeBuildReceipts/libxray-build-info.txt',
              ''.join(f'{key}={value}\n' for key, value in self.native.items()).encode())

    def git(self, command):
        self.assert_git_command(command)
        args = command[3:]
        if args == ['rev-parse', 'HEAD']:
            return (self.commit + '\n').encode()
        if args[0] == 'status':
            return b' M shared/src/commonMain/Fixture.kt\n'
        if args[:5] != ['ls-files', '-z', '--cached', '--others', '--exclude-standard']:
            raise AssertionError('Unexpected git invocation: ' + repr(command))
        path = self.root / args[-1]
        paths = [path] if path.is_file() else sorted(p for p in path.rglob('*') if p.is_file())
        return b''.join(os.fsencode(p.relative_to(self.root).as_posix()) + b'\0' for p in paths)

    def assert_git_command(self, command):
        if command[:3] != ['git', '-C', str(self.root)]:
            raise AssertionError('Unexpected external tool: ' + repr(command))

    def validate(self):
        (self.stage / 'manifest.json').unlink(missing_ok=True)
        with patch.object(sys, 'argv', ['export', str(self.root), str(self.stage), '1.3.0-beta.2', '181']), \
                patch('subprocess.check_output', side_effect=self.git):
            exec(VALIDATION, {'__name__': '__main__'})
        return json.loads((self.stage / 'manifest.json').read_text())


@unittest.skipUnless(BASH, 'Bash required for shell contracts')
class ShellTests(unittest.TestCase):
    def test_native_slice_selection_and_actual_receipt_lines(self):
        selection = 'build_slice iphoneos' + NATIVE.split('\nbuild_slice iphoneos', 1)[1]
        selection = selection.split('\nrm -rf "${DESTINATION}"', 1)[0]
        receipts = '\n'.join(line for line in NATIVE.splitlines()
                             if 'echo "apple_slices=' in line or 'echo "swift_awg_link_check=' in line)
        self.assertTrue(receipts)
        stubs = '''set -euo pipefail
WORK_DIR=/unused-fixture
build_slice() { printf 'slice:%s/%s/%s/%s\\n' "$1" "$2" "$3" "$4"; }
mkdir() { :; }
lipo() { printf 'lipo\\n'; }
xcodebuild() { printf 'xcode:%s\\n' "$@"; }
'''
        for opt_in in (None, '0', '1', 'true'):
            with self.subTest(opt_in=opt_in):
                env = os.environ.copy()
                env.pop('NIMBO_APPLE_DEVICE_ONLY', None)
                if opt_in is not None:
                    env['NIMBO_APPLE_DEVICE_ONLY'] = opt_in
                result = subprocess.run([BASH, '-c', stubs + selection + '\n' + receipts],
                                        env=env, text=True, capture_output=True, check=True)
                lines = result.stdout.splitlines()
                device_only = opt_in == '1'
                self.assertEqual(sum(line.startswith('slice:') for line in lines), 1 if device_only else 3)
                self.assertEqual(lines.count('xcode:-library'), 1 if device_only else 2)
                self.assertEqual('lipo' in lines, not device_only)
                slices = 'iphoneos-arm64' + ('' if device_only else ',iphonesimulator-arm64,iphonesimulator-x86_64')
                self.assertIn('apple_slices=' + slices, lines)
                self.assertIn('swift_awg_link_check=' + slices, lines)
                if device_only:
                    self.assertNotIn('iphonesimulator', result.stdout)

    def test_requires_macos_before_creating_output_or_running_build_tools(self):
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / 'untouched'
            result = subprocess.run(
                [BASH, '-c', 'uname() { echo Linux; }; source "$1" --output "$2"',
                 'platform-test', EXPORTER.as_posix(), output.as_posix()], text=True, capture_output=True)
            self.assertEqual(result.returncode, 2, result.stderr)
            self.assertIn('requires macOS and Xcode', result.stderr)
            self.assertFalse(output.exists())

    def test_gradle_memory_arguments_match_unsigned_build(self):
        command = 'bash ./gradlew --no-daemon' + EXPORT.split('bash ./gradlew --no-daemon', 1)[1].split('\nSHARED_FRAMEWORK=', 1)[0]
        command = command.replace('bash ./gradlew', 'capture_gradle', 1)
        result = subprocess.run([BASH, '-c', 'capture_gradle() { printf "%s\\0" "$@"; };\n' + command],
                                text=True, capture_output=True, check=True)
        self.assertEqual(result.stdout.split('\0')[:-1], [
            '--no-daemon', '--max-workers=1',
            '-Dorg.gradle.jvmargs=-Xmx6g -XX:MaxMetaspaceSize=1g -Dfile.encoding=UTF-8 -XX:+HeapDumpOnOutOfMemoryError',
            '-PnimboIosOnly=true', ':shared:linkReleaseFrameworkIosArm64',
        ])

    def test_shell_syntax(self):
        subprocess.run([BASH, '-n', str(ROOT / 'scripts/ci/build-libxray-awg-apple.sh'), str(EXPORTER)], check=True)


class ManifestTests(unittest.TestCase):
    def test_layout_provenance_and_payload_hashes(self):
        with tempfile.TemporaryDirectory() as temporary:
            fixture = ExportFixture(temporary)
            manifest = fixture.validate()
            self.assertEqual(manifest['git_commit'], fixture.commit)
            self.assertTrue(manifest['git_dirty'])
            self.assertEqual(manifest['version'], '1.3.0-beta.2')
            self.assertEqual(manifest['build_number'], 181)
            self.assertEqual(manifest['slices'], ['iphoneos-arm64'])
            self.assertEqual(manifest['core_pins']['xray']['version'], '26.10.10')
            self.assertEqual(manifest['core_pins']['mihomo']['version'], 'v1.19.32')
            self.assertTrue({'shared/src', 'shared/build.gradle.kts', 'gradle', 'iosApp/GoBridge',
                             'tools/native', 'scripts/ci', 'iosApp/Branding',
                             'iosApp/Nimbo/Assets.xcassets'} <= manifest['source_fingerprints'].keys())
            expected_directories = {'LibXray.xcframework', 'NimboShared.xcframework', 'PacketTunnelResources',
                                    'NativeNotices', 'NativeBuildReceipts', 'BrandingResources'}
            self.assertEqual({name.split('/')[0] for name in manifest['payload_files']}, expected_directories)
            expected = {p.relative_to(fixture.stage).as_posix() for p in fixture.stage.rglob('*')
                        if p.is_file() and p.name != 'manifest.json'}
            self.assertEqual(set(manifest['payload_files']), expected)
            for name, metadata in manifest['payload_files'].items():
                self.assertEqual(metadata['sha256'], hashlib.sha256((fixture.stage / name).read_bytes()).hexdigest())
            before = manifest['source_fingerprints']['shared/src']['sha256']
            write(fixture.root / 'shared/src/commonMain/Fixture.kt', b'changed source')
            self.assertNotEqual(fixture.validate()['source_fingerprints']['shared/src']['sha256'], before)

    def test_missing_required_payloads_fail(self):
        for name in ('LibXray.xcframework/ios-arm64/Headers/module.modulemap',
                     'NimboShared.xcframework/ios-arm64/NimboShared.framework/NimboShared',
                     'NativeNotices/Naive-Sources.txt', 'NativeBuildReceipts/mihomo-source-verification.json',
                     'PacketTunnelResources/geoip.dat', 'BrandingResources/Assets.car',
                     'BrandingResources/AppIcon60x60@2x.png', 'BrandingResources/BrandingPartialInfo.plist'):
            with self.subTest(name=name), tempfile.TemporaryDirectory() as temporary:
                fixture = ExportFixture(temporary)
                (fixture.stage / name).unlink()
                with self.assertRaises(ValueError):
                    fixture.validate()
                self.assertFalse((fixture.stage / 'manifest.json').exists())

    def test_rejects_simulator_framework_or_inaccurate_receipt(self):
        with tempfile.TemporaryDirectory() as temporary:
            fixture = ExportFixture(temporary)
            plist = fixture.stage / 'LibXray.xcframework/Info.plist'
            original = plist.read_bytes()
            data = plistlib.loads(original)
            data['AvailableLibraries'][0]['SupportedPlatformVariant'] = 'simulator'
            write_plist(plist, data)
            with self.assertRaisesRegex(ValueError, 'only iphoneos arm64'):
                fixture.validate()
            plist.write_bytes(original)
            fixture.native['swift_awg_link_check'] += ',iphonesimulator-arm64'
            fixture.write_receipt()
            with self.assertRaisesRegex(ValueError, 'device-only build'):
                fixture.validate()

    def test_rejects_geo_tampering_and_missing_source(self):
        with tempfile.TemporaryDirectory() as temporary:
            fixture = ExportFixture(temporary)
            path = fixture.stage / 'PacketTunnelResources/geosite.dat'
            original = path.read_bytes()
            path.write_bytes(b'tampered')
            with self.assertRaisesRegex(ValueError, 'differs from pinned helper'):
                fixture.validate()
            path.write_bytes(original)
            (fixture.root / 'gradle/libs.versions.toml').unlink()
            (fixture.root / 'gradle/wrapper/gradle-wrapper.properties').unlink()
            with self.assertRaisesRegex(ValueError, 'Missing source input: gradle'):
                fixture.validate()

    @unittest.skipUnless(BASH, 'Bash required for real tar contract')
    def test_tar_and_manifest_preserve_symlinks(self):
        with tempfile.TemporaryDirectory() as temporary:
            fixture = ExportFixture(temporary)
            directory = fixture.stage / 'NimboShared.xcframework/ios-arm64/NimboShared.framework'
            try:
                (directory / 'CurrentHeaders').symlink_to('Headers', target_is_directory=True)
            except OSError as error:
                self.skipTest('Symlink creation unavailable: ' + str(error))
            manifest = fixture.validate()
            name = (directory / 'CurrentHeaders').relative_to(fixture.stage).as_posix()
            self.assertEqual(manifest['payload_files'][name], {
                'type': 'symlink', 'target': 'Headers',
                'sha256': hashlib.sha256(b'Headers').hexdigest(),
            })
            command = next(line for line in EXPORT.splitlines() if line.startswith('COPYFILE_DISABLE=1 tar '))
            subprocess.run([BASH, '-c', command], check=True, cwd=temporary, env={
                **os.environ, 'WORK_DIR': '.', 'ARCHIVE_NAME': 'fixture.tar.gz',
            })
            with tarfile.open(Path(temporary) / 'fixture.tar.gz') as archive:
                info = archive.getmember('dependencies/' + name)
                self.assertTrue(info.issym())
                self.assertEqual(info.linkname, 'Headers')


if __name__ == '__main__':
    unittest.main()
