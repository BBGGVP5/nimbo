"""Integration guards on Windows; --swift executes policy/config tests on macOS."""
from pathlib import Path
import argparse
import platform
import re
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]

def read(path):
    return (ROOT / path).read_text(encoding="utf-8-sig")

class TrafficStatisticsContracts(unittest.TestCase):
    def test_ad_blocking_is_only_in_routing_settings_on_every_platform(self):
        for path in [
            "app/src/main/java/com/danila/nimbo/ui/screens/TrafficDashboard.kt",
            "shared/src/commonMain/kotlin/com/danila/nimbo/shared/ui/NimboStatsScreen.kt",
            "shared/src/commonMain/kotlin/com/danila/nimbo/shared/ui/NimboTrafficDashboard.kt",
            "apps/ui/src/pages/stats/TrafficDashboard.tsx"]:
            self.assertNotIn("AdBlocking", read(path), path)
        for path, call in [
            ("app/src/main/java/com/danila/nimbo/ui/screens/RoutingScreen.kt", "AdBlockingSettingsCard(preferencesManager)"),
            ("shared/src/commonMain/kotlin/com/danila/nimbo/shared/ui/NimboRoutingScreen.kt", "AdBlockingSettingsCard(state, actions)"),
            ("apps/ui/src/pages/Routing.tsx", "<AdBlockingControl />")]:
            self.assertEqual(read(path).count(call), 1, path)

    def test_offline_suffix_lists_match_and_are_bounded(self):
        swift = read("iosApp/Shared/NimboAdBlocking.swift").split("static func applying", 1)[0]
        kotlin = read("shared/src/commonMain/kotlin/com/danila/nimbo/shared/routing/NimboAdBlocking.kt").split("fun xrayRule", 1)[0]
        suffixes = lambda code: re.findall(r'"([a-z0-9.-]+\.[a-z]+)"', code)
        self.assertEqual(suffixes(swift), suffixes(kotlin))
        self.assertEqual(len(suffixes(swift)), 20)
        self.assertEqual(len(set(suffixes(swift))), 20)
        for path, marker in [
            ("tools/native/mihomo-core/ad_blocking.go", "func applyAdBlocking"),
            ("crates/xray-config/src/ad_blocking.rs", "pub fn apply"),
            ("app/src/main/java/com/danila/nimbo/vpn/AdBlockingRules.kt", "fun overlay")]:
            self.assertEqual(suffixes(read(path).split(marker, 1)[0]), suffixes(swift), path)

    def test_overlay_reaches_both_cores_without_changing_sources(self):
        builder = read("iosApp/PacketTunnel/XrayConfiguration.swift")
        self.assertIn("configuration = NimboAdBlocking.applying(to: configuration, enabled: options.adBlockingEnabled)", builder)
        self.assertIn('if adBlocking { options["adBlocking"] = true }', read("iosApp/PacketTunnel/MihomoPacketBridge.swift"))
        provider = read("iosApp/PacketTunnel/PacketTunnelProvider.swift")
        self.assertIn("adBlocking: options.adBlockingEnabled", provider)
        policy = read("iosApp/Shared/NimboAdBlocking.swift")
        self.assertIn("guard enabled else { return original }", policy)
        self.assertIn('routing["rules"] = [rule] +', policy)
        self.assertIn('"protocol": "blackhole"', policy)
        self.assertNotIn("geosite:", policy)

    def test_default_off_preference_round_trip_and_kotlin_bridge(self):
        options = read("iosApp/Shared/NimboRoutingOptions.swift")
        self.assertIn("adBlockingEnabled: Bool = false", options)
        self.assertIn('stored["adBlocking"] as? Bool ?? false', options)
        self.assertIn('adBlockingEnabled: flag("adBlocking", default: false, defaults: defaults)', options)
        bridge = read("shared/src/iosMain/kotlin/com/danila/nimbo/shared/ui/IosComposeController.kt")
        self.assertIn('"bypassLocal", "sniffing", "adBlocking" -> defaults.setBool', bridge)
        self.assertIn('onSetAdBlocking = { enabled -> applyRoutingChange("adBlocking", enabled.toString()) }', bridge)

    def test_telemetry_is_optional_visible_and_generation_scoped(self):
        root = read("iosApp/Nimbo/RootView.swift")
        self.assertIn("let includeTelemetry = selectedTab == .stats", root)
        self.assertIn("metricsGeneration == ticket", root)
        self.assertIn("if includeTelemetry && selectedTab == .stats", root)
        self.assertIn("NimboClearIosTrafficTelemetry()", root)
        provider = read("iosApp/PacketTunnel/PacketTunnelProvider.swift")
        self.assertIn('request?["includeTelemetry"] as? Bool == true', provider)
        self.assertIn('try? self.mihomo.command("telemetry")', provider)
        native = read("iosApp/PacketTunnel/MihomoPacketBridge.swift")
        self.assertIn('request["generation"] = identity.generation', native)
        self.assertIn("identity == nil || currentIdentity == identity", native)
        self.assertNotIn("busiestTunnel()", read("iosApp/Nimbo/NimboTunnelMetrics.swift"))

    def test_existing_apple_builder_runner_includes_overlay_dependency(self):
        self.assertIn('"iosApp/Shared/NimboAdBlocking.swift"', read("iosApp/Tests/test_ping_contracts.py"))
        self.assertIn('"${ROOT_DIR}/iosApp/Shared/NimboAdBlocking.swift"', read("scripts/ci/build-libxray-awg-apple.sh"))

    def test_rule_mode_is_preflighted_and_domain_sniffing_is_runtime_only(self):
        controller = read("iosApp/Nimbo/VpnController.swift")
        admission = controller.split("func validateCore", 1)[1].split("func setCorePreference", 1)[0]
        self.assertIn("NimboMihomoControl.validateAdBlocking(full, enabled: NimboRoutingSettings.current.adBlockingEnabled)", admission)
        selection = controller.split("func selectServer", 1)[1]
        self.assertLess(selection.index("validateCore(data: data)"), selection.index("await self.disconnect"))
        control = read("iosApp/Nimbo/NimboMihomoControl.swift")
        self.assertIn('inspection(full)["declaredGraph"]', control)
        self.assertIn('requireMihomoRuleMode(graph?["mode"] as? String', control)
        builder = read("iosApp/PacketTunnel/XrayConfiguration.swift")
        self.assertIn("sniffing: options.sniffingEnabled || options.adBlockingEnabled", builder)
        self.assertIn("routeOnly: options.adBlockingEnabled", builder)
        self.assertNotIn("options.sniffingEnabled =", builder)

def run_swift():
    if platform.system() != "Darwin":
        raise SystemExit("--swift requires an Apple host; no IPA validation is performed here.")
    sources = ["iosApp/Shared/" + name for name in ["NimboRoutingOptions.swift", "NimboAdBlocking.swift",
        "NimboTrafficTelemetry.swift", "NimboMihomoSessionPolicy.swift", "NimboPingPolicy.swift",
        "NimboPingCompletion.swift", "NimboHTTPProbe.swift", "NimboSOCKSTunnel.swift"]]
    sources += ["iosApp/PacketTunnel/NimboPingRoute.swift", "iosApp/PacketTunnel/XrayConfiguration.swift",
                "iosApp/Tests/PingConfigurationStubs.swift", "iosApp/Tests/TrafficPolicyTests.swift"]
    with tempfile.TemporaryDirectory(prefix="nimbo-traffic-") as tmp:
        binary = str(Path(tmp) / "TrafficPolicyTests")
        subprocess.run(["xcrun", "swiftc", "-swift-version", "5", "-parse-as-library", *sources, "-o", binary],
                       cwd=ROOT, check=True, timeout=180)
        subprocess.run([binary], check=True, timeout=30)

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--swift", action="store_true")
    args, remaining = parser.parse_known_args()
    result = unittest.main(argv=[__file__, *remaining], exit=False)
    if not result.result.wasSuccessful():
        raise SystemExit(1)
    if args.swift:
        run_swift()
