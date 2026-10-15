"""Cross-platform release metadata regressions; never build or publish packages."""
import contextlib
import importlib.util
import io
import pathlib
import plistlib
import tempfile
import unittest
from unittest.mock import patch

ROOT = pathlib.Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("version_guard", ROOT / "tools/release/check-versions.py")
GUARD = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(GUARD)
ANDROID = "app/build.gradle.kts"
SHARED = "shared/src/commonMain/kotlin/com/danila/nimbo/shared/updates/ReleaseDefaults.kt"
INPUTS = ["Cargo.toml", "Cargo.lock", ANDROID, SHARED, "iosApp/project.yml"] + [
    f"apps/{app}/{name}"
    for app in ("ui", "installer")
    for name in ("package.json", "package-lock.json", "src-tauri/tauri.conf.json")
]


def check_versions(root):
    output = io.StringIO()
    with patch.object(GUARD, "ROOT", root), patch.object(GUARD.os, "environ", {}), \
            patch.object(GUARD.sys, "argv", ["check-versions.py"]), contextlib.redirect_stdout(output):
        GUARD.main()
    return output.getvalue()


class Beta2VersionTests(unittest.TestCase):
    def test_current_cross_platform_defaults_agree(self):
        self.assertIn("20 product version fields: 1.3.0-beta.2", check_versions(ROOT))

    def test_stale_platform_versions_and_build_numbers_are_rejected(self):
        mutations = [
            (ANDROID, 'versionName = "1.3.0-beta.2"', 'versionName = "1.3.0-beta.1"'),
            (ANDROID, "versionCode = 19", "versionCode = 18"),
            (SHARED, 'VERSION = "1.3.0-beta.2"', 'VERSION = "1.3.0-beta.1"'),
            ("iosApp/project.yml", "NIMBO_DISPLAY_VERSION: 1.3.0-beta.2", "NIMBO_DISPLAY_VERSION: 1.3.0-beta.1"),
            ("iosApp/project.yml", "CURRENT_PROJECT_VERSION: 181", "CURRENT_PROJECT_VERSION: 180"),
            ("iosApp/project.yml", "MARKETING_VERSION: 1.3.0", "MARKETING_VERSION: 1.2.0"),
            ("Cargo.toml", 'version = "1.3.0-beta.2"', 'version = "1.3.0-beta.1"'),
            ("Cargo.lock", 'version = "1.3.0-beta.2"', 'version = "1.3.0-beta.1"'),
        ] + [
            (f"apps/{app}/{name}", '"version": "1.3.0-beta.2"', '"version": "1.3.0-beta.1"')
            for app in ("ui", "installer")
            for name in ("package.json", "package-lock.json", "src-tauri/tauri.conf.json")
        ]
        for relative, before, after in mutations:
            with self.subTest(path=relative, stale=after), tempfile.TemporaryDirectory() as temporary:
                root = pathlib.Path(temporary)
                for name in INPUTS:
                    destination = root / name
                    destination.parent.mkdir(parents=True, exist_ok=True)
                    destination.write_bytes((ROOT / name).read_bytes())
                target = root / relative
                source = target.read_text(encoding="utf-8-sig")
                self.assertIn(before, source)
                target.write_text(source.replace(before, after, 1), encoding="utf-8")
                with self.assertRaises(SystemExit):
                    check_versions(root)

    def test_apple_build_defaults_and_plist_inheritance(self):
        script = (ROOT / "scripts/ci/build-unsigned-ios.sh").read_text(encoding="utf-8")
        self.assertIn('${NIMBO_VERSION:-1.3.0-beta.2}', script)
        self.assertIn('${NIMBO_BUILD_NUMBER:-181}', script)
        workflow = (ROOT / ".github/workflows/build-ios-unsigned.yml").read_text(encoding="utf-8")
        self.assertIn("default: 1.3.0-beta.2", workflow)
        self.assertIn("NIMBO_BUILD_NUMBER: '181'", workflow)
        self.assertNotIn("1.3.0-beta.1", workflow)
        for folder in ("Nimbo", "PacketTunnel", "ControlWidget"):
            with self.subTest(target=folder):
                info = plistlib.loads((ROOT / f"iosApp/{folder}/Info.plist").read_bytes())
                self.assertEqual(info["CFBundleShortVersionString"], "$(MARKETING_VERSION)")
                self.assertEqual(info["CFBundleVersion"], "$(CURRENT_PROJECT_VERSION)")
        installer = (ROOT / "apps/ui/src-tauri/windows/nimbo-installer.nsi").read_text(encoding="utf-8")
        self.assertIn('!define PRODUCT_VERSION "1.3.0-beta.2"', installer)
        self.assertIn('!define PRODUCT_FILE_VERSION "1.3.0.2"', installer)


if __name__ == "__main__":
    unittest.main()
