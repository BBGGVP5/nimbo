#!/usr/bin/env python3
"""Release-script regressions that do not invoke Gradle or Apple build tools."""
import os
import importlib.util
import pathlib
import shutil
import subprocess
import sys
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts/ci/build-unsigned-ios.sh"
BUILD = SCRIPT.read_text(encoding="utf-8")
BASH = shutil.which("bash")
if pathlib.Path("C:/Program Files/Git/bin/bash.exe").exists():
    BASH = "C:/Program Files/Git/bin/bash.exe"


class ReleaseTests(unittest.TestCase):
    def test_each_process_links_one_combined_go_runtime(self):
        spec = importlib.util.spec_from_file_location("ios_awg_contract", ROOT / "scripts/ci/check-ios-awg.py")
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        project = (ROOT / "iosApp/project.yml").read_text(encoding="utf-8")
        module.check_go_process_links(project)
        with self.assertRaises(AssertionError):
            module.check_go_process_links(project.replace(
                "      - framework: Vendor/LibXray.xcframework",
                "      - framework: Vendor/LibXray.xcframework\n        embed: false\n      - framework: Vendor/LibXray.xcframework", 1))
        with self.assertRaises(AssertionError):
            module.check_go_process_links(project.replace(
                "      - framework: Vendor/LibXray.xcframework", "      - framework: Vendor/AWG.xcframework", 1))

    def test_native_link_heap_override_is_one_argument(self):
        self.assertIsNotNone(BASH, "Bash is required for release-script tests")
        command = "./gradlew --no-daemon" + BUILD.split("./gradlew --no-daemon", 1)[1].split("\n\n", 1)[0]
        # Capture Bash's argument splitting using a function; never launch Gradle.
        command = command.replace("./gradlew", "capture_gradle", 1)
        result = subprocess.run(
            [BASH, "-c", 'capture_gradle() { printf "%s\\0" "$@"; };\n' + command],
            text=True, capture_output=True, cwd=ROOT,
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout.split("\0")[:-1], [
            "--no-daemon", "--max-workers=1",
            "-Dorg.gradle.jvmargs=-Xmx6g -XX:MaxMetaspaceSize=1g -Dfile.encoding=UTF-8 -XX:+HeapDumpOnOutOfMemoryError",
            "-PnimboIosOnly=true", ":shared:linkReleaseFrameworkIosArm64",
        ])

    def test_version_inputs_before_build_tools(self):
        self.assertIsNotNone(BASH, "Bash is required for release-script tests")
        # Execute the real input validation only. No build command is copied.
        preflight = BUILD.split('APP_BUNDLE_ID=', 1)[0]
        temporary_root = ROOT / "iosApp/build/release-readiness"
        temporary_root.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(dir=temporary_root) as directory:
            script = pathlib.Path(directory) / "preflight.sh"
            script.write_text(preflight, encoding="utf-8", newline="\n")
            cases = [
                (None, None, "1.2.0|170"),
                ("1.2.0", "170", "1.2.0|170"),
                ("1.2.0", "171", "1.2.0|171"),
                ("1.2.0", "9999", "1.2.0|9999"),
                ("1.2.0-beta.5", "170", None),
                ("1.2.0-rc.1", "170", None),
                ("v1.2.0", "170", None),
                ("../1.2.0", "170", None),
                ("1.2.0", "160", None),
                ("1.2.0", "16", None),
                ("1.2.0", "0", None),
                ("1.2.0", "0170", None),
                ("1.2.0", "10000", None),
                ("1.2.0", "1+170", None),
            ]
            for version, number, expected in cases:
                with self.subTest(version=version, number=number):
                    env = os.environ.copy()
                    for key, value in (("NIMBO_VERSION", version), ("NIMBO_BUILD_NUMBER", number)):
                        env.pop(key, None)
                        if value is not None:
                            env[key] = value
                    # Only uname is simulated, to exercise valid inputs on Windows.
                    result = subprocess.run(
                        [BASH, "-c", 'uname() { echo Darwin; }; source "$1"; '
                         'printf "%s|%s" "$VERSION" "$BUILD_NUMBER"', "preflight", script.as_posix()],
                        env=env, text=True, capture_output=True, cwd=ROOT,
                    )
                    if expected is None:
                        self.assertEqual(result.returncode, 2, result.stderr)
                        self.assertIn("NIMBO_", result.stderr)
                    else:
                        self.assertEqual(result.returncode, 0, result.stderr)
                        self.assertEqual(result.stdout, expected)

    def test_widget_removal_preserves_source_and_tunnel(self):
        # Run the exact inline project transform against a separate spec.
        transform = BUILD.split('"${PROJECT_SPEC}" <<\'PY\'\n', 1)[1].split("\nPY\n", 1)[0]
        original = (ROOT / "iosApp/project.yml").read_bytes()
        temporary_root = ROOT / "iosApp/build/release-readiness"
        temporary_root.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(dir=temporary_root) as directory:
            spec = pathlib.Path(directory) / "project.yml"
            spec.write_bytes(original)
            subprocess.run([sys.executable, "-c", transform, str(spec)], check=True)
            transformed = spec.read_text(encoding="utf-8")
        self.assertNotIn("NimboControlWidget", transformed)
        self.assertIn("  NimboPacketTunnel:", transformed)
        self.assertIn("Vendor/LibXray.xcframework", transformed)
        self.assertIn("schemes:", transformed)
        self.assertEqual((ROOT / "iosApp/project.yml").read_bytes(), original)

    def test_release_defaults_and_native_link_contract(self):
        project = (ROOT / "iosApp/project.yml").read_text(encoding="utf-8")
        self.assertRegex(project, r"(?m)^    CURRENT_PROJECT_VERSION: 170$")
        for key in ("MARKETING_VERSION", "NIMBO_DISPLAY_VERSION"):
            self.assertRegex(project, rf"(?m)^    {key}: 1\.2\.0$")
        checker = (ROOT / "iosApp/Nimbo/NimboUpdateChecker.swift").read_text(encoding="utf-8")
        self.assertIn('NimboUpdateChannel(rawValue: stored ?? "") ?? .stable', checker)
        self.assertIn('channel: NimboUpdateChannel = .stable', checker)
        self.assertIn("case beta", checker)
        native = (ROOT / "scripts/ci/build-libxray-awg-apple.sh").read_text(encoding="utf-8")
        self.assertLess(native.index("export GOTOOLCHAIN=local"), native.index("go env GOVERSION"))
        self.assertIn("-application-extension -emit-library", native)
        self.assertIn('"${out}/libXray.a" -lresolv -framework Security -framework CoreFoundation', native)
        for framework in ("Security", "CoreFoundation"):
            self.assertIn(f"- sdk: {framework}.framework", project)
        self.assertIn('/usr/bin/zip -qry "${PACKAGE_DIR}/${OUTPUT_NAME}" Payload', BUILD)
        self.assertIn('mv -f "${PACKAGE_DIR}/${OUTPUT_NAME}" "${OUTPUT_PATH}"', BUILD)
        self.assertIn('for bundle in "${TUNNEL_PATH}" "${WIDGET_PATH}"', BUILD)
        self.assertIn("build_number=${BUILD_NUMBER}\nchannel=stable", BUILD)

    def test_shell_files_have_unix_line_endings(self):
        paths = list((ROOT / "scripts/ci").glob("*.sh")) + list((ROOT / "scripts/ios").glob("*.sh"))
        for path in paths:
            with self.subTest(path=path.name):
                self.assertNotIn(b"\r", path.read_bytes())


if __name__ == "__main__":
    unittest.main()
