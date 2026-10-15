#!/usr/bin/env python3
"""Offline generator tests; synthetic dependency bytes are test fixtures only.

Run with ``python -B -m unittest discover -s scripts/ios -p test_xtool_prepare.py -v``.
No Swift, xtool, Xcode, WSL, network access, or production builds are invoked.
"""

from __future__ import annotations

import contextlib
import io
import json
from pathlib import Path
import plistlib
import shutil
import struct
import tempfile
import unittest

import yaml

import xtool_prepare as prepare


CANONICAL = Path(__file__).resolve().parents[2]


def plist(path: Path, value: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(plistlib.dumps(value))


class PrepareTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="nimbo xtool ")
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.repo = self.root / "repo with spaces"
        self.ios = self.repo / "iosApp"
        self.ios.mkdir(parents=True)
        self.dependencies = self.root / "dependencies with spaces"
        self.dependencies.mkdir()
        self.output = self.root / "output with spaces"
        self.project = yaml.safe_load((CANONICAL / "iosApp/project.yml").read_text(encoding="utf-8"))
        # Work on copies of the actual four target source trees and metadata.
        for name in ("Nimbo", "PacketTunnel", "Shared", "ControlWidget", "LiveActivity", "Branding", "NativeNotices"):
            shutil.copytree(CANONICAL / "iosApp" / name, self.ios / name)
        self.save_project()
        for name in prepare.BINARIES:
            self.make_binary(name)
        for name in ("geoip.dat", "geosite.dat"):
            path = self.dependencies / "PacketTunnelResources" / name
            path.parent.mkdir(exist_ok=True)
            path.write_bytes(b"unit-test geo fixture: " + name.encode())
        shutil.copytree(self.ios / "NativeNotices", self.dependencies / "NativeNotices")
        branding = self.dependencies / "BrandingResources"
        branding.mkdir()
        (branding / "Assets.car").write_bytes(b"unit-test compiled catalog fixture")
        (branding / "AppIcon60x60@2x.png").write_bytes(b"unit-test icon fixture")
        self.partial = {"CFBundleIcons": {"CFBundlePrimaryIcon": {
            "CFBundleIconName": "AppIcon", "CFBundleIconFiles": ["AppIcon60x60"]}},
            "CFBundleIcons~ipad": {"CFBundlePrimaryIcon": {"CFBundleIconName": "AppIcon"}}}
        plist(branding / "BrandingPartialInfo.plist", self.partial)

    def save_project(self):
        (self.ios / "project.yml").write_text(yaml.safe_dump(self.project, allow_unicode=True, sort_keys=False), encoding="utf-8")

    def make_binary(self, name):
        root = self.dependencies / f"{name}.xcframework"
        framework = root / "ios-arm64" / f"{name}.framework"
        framework.mkdir(parents=True)
        # ar magic is sufficient for the generator's static/dynamic check.
        (framework / name).write_bytes(b"!<arch>\nunit-test static archive fixture")
        plist(framework / "Info.plist", {"CFBundleExecutable": name})
        plist(root / "Info.plist", {"XCFrameworkFormatVersion": "1.0", "AvailableLibraries": [{
            "LibraryIdentifier": "ios-arm64", "LibraryPath": f"{name}.framework",
            "SupportedPlatform": "ios", "SupportedArchitectures": ["arm64"]}]})

    def generate(self):
        result = prepare.prepare(self.repo, self.dependencies, self.output)
        self.assertEqual(result, self.output)
        return json.loads((self.output / "xtool-prepare.json").read_text(encoding="utf-8"))

    def fails(self, message):
        with self.assertRaisesRegex(prepare.PreparationError, message):
            prepare.prepare(self.repo, self.dependencies, self.output)
        self.assertFalse(self.output.exists(), "Validation must finish before creating output")

    def test_actual_project_membership_and_byte_identical_sources(self):
        manifest = self.generate()
        for name, target in self.project["targets"].items():
            expected = set()
            for selection in target["sources"]:
                path = self.ios / selection["path"]
                if selection.get("buildPhase") == "resources":
                    continue
                expected.update(p.relative_to(self.ios).as_posix() for p in (path.rglob("*.swift") if path.is_dir() else [path]))
            actual = set(manifest["targets"][name]["sources"])
            self.assertEqual(actual, expected, name)
            copied = self.output / "Sources" / name
            self.assertEqual({p.relative_to(copied).as_posix() for p in copied.rglob("*.swift")}, expected)
            for relative in expected:
                self.assertEqual((copied / relative).read_bytes(), (self.ios / relative).read_bytes())
        app = manifest["targets"]["Nimbo"]["sources"]
        tunnel = manifest["targets"]["NimboPacketTunnel"]["sources"]
        self.assertIn("Nimbo/NimboCoreSelection.swift", app)
        self.assertNotIn("PacketTunnel/NimboCoreSelection.swift", app)
        self.assertIn("PacketTunnel/NimboCoreSelection.swift", tunnel)
        self.assertNotIn("Nimbo/NimboCoreSelection.swift", tunnel)
        widget = manifest["targets"]["NimboControlWidget"]["sources"]
        self.assertIn("Nimbo/NimboCoreSelection.swift", widget)
        self.assertNotIn("Shared/NimboDiagnostics.swift", widget)
        self.assertEqual([p for p in manifest["targets"]["NimboLiveActivity"]["sources"] if p.startswith("Shared/")],
                         ["Shared/NimboLiveActivityAttributes.swift", "Shared/NimboLiveActivityPolicy.swift"])

    def test_schema_products_static_dependencies_and_sdk_links(self):
        manifest = self.generate()
        package = (self.output / "Package.swift").read_text(encoding="utf-8")
        config = yaml.safe_load((self.output / "xtool.yml").read_text(encoding="utf-8"))
        self.assertEqual(set(config), {"version", "bundleID", "product", "infoPath", "resources", "entitlementsPath", "extensions"})
        self.assertEqual(config["version"], 1)
        self.assertEqual(config["product"], "Nimbo")
        self.assertEqual(config["bundleID"], "com.nimbo.resignable")
        self.assertEqual({e["product"] for e in config["extensions"]}, set(prepare.TARGETS[1:]))
        self.assertNotIn(".executable", package)
        self.assertNotIn("type: .static", package, "xtool selects automatic library products")
        for name in prepare.TARGETS:
            self.assertIn(f'.library(name: "{name}", targets: ["{name}"])', package)
            target = manifest["targets"][name]
            sdks = [d["sdk"] for d in self.project["targets"][name]["dependencies"] if "sdk" in d]
            self.assertEqual(target["link_sdks"], sdks)
            chunk = package.split(f'.target(\n            name: "{name}"', 1)[1].split("\n        ),", 1)[0]
            for sdk in sdks:
                expected = f'.linkedFramework("{sdk[:-10]}")' if sdk.endswith(".framework") else f'.linkedLibrary("{sdk[3:-4]}")'
                self.assertIn(expected, chunk)
        for name in prepare.BINARIES:
            self.assertIn(f'.binaryTarget(name: "{name}", path: "Dependencies/{name}.xcframework")', package)
            self.assertTrue((self.output / f"Dependencies/{name}.xcframework/Info.plist").is_file())
        self.assertEqual(manifest["targets"]["Nimbo"]["binary_targets"], ["NimboShared", "LibXray"])
        self.assertEqual(manifest["targets"]["NimboPacketTunnel"]["binary_targets"], ["LibXray"])
        for extension in config["extensions"]:
            self.assertLessEqual(set(extension), {"product", "bundleID", "infoPath", "resources", "entitlementsPath"})

    def test_minimum_ios_plists_and_extension_compile_link_flags(self):
        manifest = self.generate()
        package = (self.output / "Package.swift").read_text(encoding="utf-8")
        self.assertIn('platforms: [.iOS("16.0")]', package)
        for name, minimum in {"Nimbo": "16.0", "NimboPacketTunnel": "16.0", "NimboLiveActivity": "16.2", "NimboControlWidget": "18.0"}.items():
            target = manifest["targets"][name]
            self.assertEqual(target["minimum_ios"], minimum)
            info = prepare.read_plist(self.output / target["info_path"])
            self.assertEqual(info["MinimumOSVersion"], minimum)
            self.assertEqual(info["CFBundleExecutable"], name)
            self.assertEqual(info["CFBundleIdentifier"], target["bundle_id"])
            chunk = package.split(f'.target(\n            name: "{name}"', 1)[1].split("\n        ),", 1)[0]
            self.assertIn(f'"-Xfrontend", "-target", "-Xfrontend", "arm64-apple-ios{minimum}"', chunk)
            self.assertIn(f'"-target", "arm64-apple-ios{minimum}"', chunk)
            self.assertIn('"-Xlinker", "-ObjC"', chunk)
            self.assertEqual('"-application-extension"' in chunk, name != "Nimbo")
        self.assertTrue(any("wrapper executables" in limitation for limitation in manifest["limitations"]))

    def test_disk_plists_properties_and_nested_substitutions(self):
        self.project["settings"]["base"]["NIMBO_APP_BUNDLE_ID"] = "org.example.fixture"
        self.project["settings"]["base"]["NIMBO_BUILD_REVISION"] = 'rev-"quoted" & <xml> \\ (Тест)'
        self.project["targets"]["Nimbo"]["info"]["properties"]["Fixture"] = {
            "identifier": "${PRODUCT_BUNDLE_IDENTIFIER}", "product": "$(PRODUCT_NAME)",
            "values": ["$(CURRENT_PROJECT_VERSION)", "$(NIMBO_BUILD_REVISION)"]}
        self.save_project()
        manifest = self.generate()
        app = prepare.read_plist(self.output / "Support/Nimbo-Info.plist")
        self.assertTrue(app["UIFileSharingEnabled"], "Preserve disk plist keys absent from project.yml")
        self.assertTrue(app["LSSupportsOpeningDocumentsInPlace"])
        self.assertEqual(app["NimboPacketTunnelBundleIdentifier"], "org.example.fixture.PacketTunnel")
        self.assertEqual(app["Fixture"], {"identifier": "org.example.fixture", "product": "Nimbo",
                                         "values": ["181", 'rev-"quoted" & <xml> \\ (Тест)']})
        tunnel = prepare.read_plist(self.output / "Support/NimboPacketTunnel-Info.plist")
        self.assertEqual(tunnel["NSExtension"]["NSExtensionPrincipalClass"], "NimboPacketTunnel.PacketTunnelProvider")
        self.assertEqual(manifest["targets"]["NimboLiveActivity"]["bundle_id"], "org.example.fixture.LiveActivity")
        self.assertFalse(any(token in json.dumps(app) for token in ("$(", "${")))

    def test_entitlements_remain_distinct_and_overlay_disk_properties(self):
        path = self.ios / "Nimbo/Nimbo.entitlements"
        disk = prepare.read_plist(path)
        disk["fixture-identifier"] = "$(PRODUCT_BUNDLE_IDENTIFIER)"
        disk["fixture-xml"] = '<tag> & "quoted"'
        plist(path, disk)
        self.generate()
        app = prepare.read_plist(self.output / "Support/Nimbo.entitlements")
        tunnel = prepare.read_plist(self.output / "Support/NimboPacketTunnel.entitlements")
        widget = prepare.read_plist(self.output / "Support/NimboControlWidget.entitlements")
        self.assertEqual(app["fixture-identifier"], "com.nimbo.resignable")
        self.assertEqual(app["fixture-xml"], '<tag> & "quoted"')
        key = "com.apple.developer.networking.networkextension"
        self.assertEqual(app[key], ["packet-tunnel-provider"])
        self.assertEqual(tunnel[key], ["packet-tunnel-provider"])
        self.assertEqual(widget, {"com.apple.developer.networking.vpn.api": ["allow-vpn"]})
        self.assertFalse((self.output / "Support/NimboLiveActivity.entitlements").exists())

    def test_root_resources_compiled_branding_fonts_notices_and_geo(self):
        manifest = self.generate()
        package = (self.output / "Package.swift").read_text(encoding="utf-8")
        self.assertNotIn("resources:", package)
        self.assertNotIn(".process(", package)
        self.assertFalse(list(self.output.rglob("*.xcassets")))
        config = yaml.safe_load((self.output / "xtool.yml").read_text(encoding="utf-8"))
        for product in [config, *config["extensions"]]:
            self.assertIn("Resources/Branding/Assets.car", product["resources"])
            self.assertIn("Resources/Branding/AppIcon60x60@2x.png", product["resources"])
            self.assertNotIn("Support/BrandingPartialInfo.plist", product["resources"])
            for path in product["resources"]:
                self.assertTrue((self.output / path).exists())
        self.assertIn("Resources/Nimbo/NativeNotices", config["resources"])
        self.assertIn("Resources/Nimbo/manrope_regular.ttf", config["resources"])
        tunnel = next(e for e in config["extensions"] if e["product"] == "NimboPacketTunnel")
        self.assertIn("Resources/NimboPacketTunnel/geoip.dat", tunnel["resources"])
        self.assertIn("Resources/NimboPacketTunnel/geosite.dat", tunnel["resources"])
        app_info = prepare.read_plist(self.output / config["infoPath"])
        for key, value in self.partial.items():
            self.assertEqual(app_info[key], value)
        self.assertEqual(manifest["targets"]["Nimbo"]["source_asset_catalogs"],
                         ["Branding/Branding.xcassets", "Nimbo/Assets.xcassets"])

    def test_static_framework_resources_are_root_entries_only_for_dependent_products(self):
        framework = self.dependencies / "NimboShared.xcframework/ios-arm64/NimboShared.framework"
        path = framework / "Resources/compose-resources/fixture.txt"
        path.parent.mkdir(parents=True)
        path.write_text("resource fixture", encoding="utf-8")
        bundle = framework / "Dependency.bundle/Info.plist"
        plist(bundle, {"fixture": True})
        manifest = self.generate()
        self.assertEqual(manifest["binary_resource_payloads"], [
            {"binary_target": "NimboShared", "source": "Dependencies/NimboShared.xcframework/ios-arm64/NimboShared.framework/Dependency.bundle", "destination": "Dependency.bundle"},
            {"binary_target": "NimboShared", "source": "Dependencies/NimboShared.xcframework/ios-arm64/NimboShared.framework/Resources/compose-resources", "destination": "compose-resources"}])
        for payload in manifest["binary_resource_payloads"]:
            self.assertTrue((self.output / payload["source"]).is_dir())
            for name, target in manifest["targets"].items():
                self.assertEqual(payload["source"] in target["root_resources"], name == "Nimbo")
        config = yaml.safe_load((self.output / "xtool.yml").read_text(encoding="utf-8"))
        for payload in manifest["binary_resource_payloads"]:
            self.assertIn(payload["source"], config["resources"])
            self.assertTrue(all(payload["source"] not in extension["resources"] for extension in config["extensions"]))

    def test_libxray_resource_root_entries_reach_app_and_tunnel(self):
        payload = self.dependencies / "LibXray.xcframework/ios-arm64/LibXray.framework/Runtime.bundle/Info.plist"
        plist(payload, {"fixture": True})
        manifest = self.generate()
        resource = manifest["binary_resource_payloads"][0]["source"]
        config = yaml.safe_load((self.output / "xtool.yml").read_text(encoding="utf-8"))
        for product in [config, *config["extensions"]]:
            self.assertEqual(resource in product["resources"], product["product"] in {"Nimbo", "NimboPacketTunnel"})

    def test_binary_root_resource_cannot_replace_product_info(self):
        path = self.dependencies / "NimboShared.xcframework/ios-arm64/NimboShared.framework/Resources/Info.plist"
        plist(path, {"fixture": True})
        self.fails("Colliding root resource names")

    def test_reproducible_across_output_and_input_locations(self):
        self.generate()
        second_repo = self.root / "relocated repository"
        second_dependencies = self.root / "relocated dependencies"
        second_output = self.root / "second output"
        shutil.copytree(self.repo, second_repo)
        shutil.copytree(self.dependencies, second_dependencies)
        prepare.prepare(second_repo, second_dependencies, second_output)
        first = {p.relative_to(self.output).as_posix(): p.read_bytes() for p in self.output.rglob("*") if p.is_file()}
        second = {p.relative_to(second_output).as_posix(): p.read_bytes() for p in second_output.rglob("*") if p.is_file()}
        self.assertEqual(first, second)

    def test_missing_binary_and_empty_geo_fail_before_output_creation(self):
        archive = self.dependencies / "LibXray.xcframework/ios-arm64/LibXray.framework/LibXray"
        archive.unlink()
        self.fails("Missing required input")
        archive.write_bytes(b"!<arch>\nfixture")
        (self.dependencies / "PacketTunnelResources/geoip.dat").write_bytes(b"")
        self.fails("empty required file")

    def test_missing_exported_notice_fails(self):
        notice = next((self.dependencies / "NativeNotices").iterdir())
        notice.unlink()
        self.fails("Missing required input")

    def test_missing_branding_catalog_partial_and_icons_fail(self):
        for relative in ("Assets.car", "BrandingPartialInfo.plist", "AppIcon60x60@2x.png"):
            with self.subTest(relative=relative):
                path = self.dependencies / "BrandingResources" / relative
                contents = path.read_bytes()
                path.unlink()
                self.fails("Missing required input|requires AppIcon")
                path.write_bytes(contents)

    def test_dynamic_and_simulator_only_frameworks_rejected(self):
        archive = self.dependencies / "LibXray.xcframework/ios-arm64/LibXray.framework/LibXray"
        archive.write_bytes(b"\xcf\xfa\xed\xfe dynamic Mach-O fixture")
        self.fails("Expected a static archive")
        archive.write_bytes(b"!<arch>\nfixture")
        path = self.dependencies / "LibXray.xcframework/Info.plist"
        info = prepare.read_plist(path)
        info["AvailableLibraries"][0]["SupportedPlatformVariant"] = "simulator"
        plist(path, info)
        self.fails("Missing iOS device arm64 slice")

    def test_universal_static_archive_detection(self):
        path = self.root / "universal-static"
        archive = b"!<arch>\nunit-test fixture"
        offset = 8 + 20
        path.write_bytes(b"\xca\xfe\xba\xbe" + struct.pack(">I", 1) + struct.pack(">IIIII", 0x100000C, 0, offset, len(archive), 0) + archive)
        prepare.assert_static_archive(path)
        path.write_bytes(path.read_bytes()[:-1])
        with self.assertRaisesRegex(prepare.PreparationError, "Invalid archive slice"):
            prepare.assert_static_archive(path)

    def test_unknown_and_cyclic_plist_substitutions(self):
        for value, message in (("$(UNKNOWN_FIXTURE)", "Unresolved build setting"),
                               ("$(FIXTURE_CYCLE)", "Cyclic build setting")):
            self.project["targets"]["Nimbo"]["info"]["properties"]["Fixture"] = value
            self.project["settings"]["base"]["FIXTURE_CYCLE"] = "${FIXTURE_CYCLE}"
            self.save_project()
            self.fails(message)

    def test_malformed_plist_and_missing_icon_reference_fail(self):
        path = self.dependencies / "BrandingResources/BrandingPartialInfo.plist"
        path.write_text("<plist><dict><invalid", encoding="utf-8")
        self.fails("Invalid plist")
        self.partial["CFBundleIcons"]["CFBundlePrimaryIcon"]["CFBundleIconFiles"] = ["AppIconMissing"]
        plist(path, self.partial)
        self.fails("Missing compiled icon referenced")

    def test_unrecognized_build_hooks_are_not_silently_dropped(self):
        self.project["targets"]["Nimbo"]["postBuildScripts"] = [{"name": "Extra work", "script": "exit 0"}]
        self.save_project()
        self.fails("Unsupported post-build scripts")

    def test_unsupported_selections_and_missing_selected_source_fail(self):
        self.project["targets"]["Nimbo"]["sources"][0]["excludes"] = ["NimboApp.swift"]
        self.save_project()
        self.fails("Unsupported source selection options")
        del self.project["targets"]["Nimbo"]["sources"][0]["excludes"]
        self.project["targets"]["NimboLiveActivity"]["sources"][1]["path"] = "Shared/Missing.swift"
        self.save_project()
        self.fails("Missing required input")

    def test_traversal_and_duplicate_yaml_keys_fail(self):
        self.project["targets"]["Nimbo"]["sources"][0]["path"] = "../outside"
        self.save_project()
        self.fails("Unsafe relative path")
        with (self.ios / "project.yml").open("a", encoding="utf-8") as stream:
            stream.write("\nname: duplicate\n")
        self.fails("Duplicate YAML key")

    def test_root_resource_collision_fails(self):
        path = self.ios / "Nimbo/Extra/Assets.car"
        path.parent.mkdir()
        path.write_bytes(b"conflicting catalog fixture")
        self.fails("Colliding root resource names")

    def test_disagreeing_minimum_and_incomplete_extensions_fail(self):
        target = self.project["targets"]["NimboControlWidget"]
        target["settings"]["base"]["IPHONEOS_DEPLOYMENT_TARGET"] = "16.0"
        self.save_project()
        self.fails("Inconsistent/invalid iOS deployment target")
        target["settings"]["base"]["IPHONEOS_DEPLOYMENT_TARGET"] = "18.0"
        self.project["targets"]["Nimbo"]["dependencies"].pop(0)
        self.save_project()
        self.fails("embed all three extensions")

    def test_existing_output_and_input_overlap_preserved(self):
        self.output.mkdir()
        sentinel = self.output / "keep.txt"
        sentinel.write_bytes(b"preserve me")
        with self.assertRaisesRegex(prepare.PreparationError, "already exists"):
            prepare.prepare(self.repo, self.dependencies, self.output)
        self.assertEqual(sentinel.read_bytes(), b"preserve me")
        for output in (self.repo / "generated", self.dependencies / "generated"):
            with self.assertRaisesRegex(prepare.PreparationError, "disjoint"):
                prepare.prepare(self.repo, self.dependencies, output)
            self.assertFalse(output.exists())

    def test_swift_literal_escaping(self):
        value = 'Quotes " and slash \\ interpolation \\(danger)\n\t\r\x01 русский'
        self.assertEqual(prepare.swift_string(value), '"Quotes \\" and slash \\\\ interpolation \\\\(danger)\\n\\t\\r\\u{1} русский"')

    def test_cli_failure_is_actionable_and_does_not_create_output(self):
        error = io.StringIO()
        with contextlib.redirect_stderr(error):
            code = prepare.main(["--repo-root", str(self.repo), "--dependencies-root", str(self.root / "missing"), "--output", str(self.output)])
        self.assertEqual(code, 1)
        self.assertIn("Missing required input", error.getvalue())
        self.assertFalse(self.output.exists())


if __name__ == "__main__":
    unittest.main()
