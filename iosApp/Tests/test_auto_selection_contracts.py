"""Focused Windows source contracts; --swift executes production pure policy on macOS."""
import argparse
import pathlib
import platform
import subprocess
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]
POLICY = "iosApp/Shared/NimboAutoSelectionPolicy.swift"
CACHE = "iosApp/Nimbo/NimboAutoPingCache.swift"
SERVICE = "iosApp/Nimbo/NimboPingService.swift"
MONITOR = "iosApp/Nimbo/NimboPingNetworkMonitor.swift"


def read(path):
    return (ROOT / path).read_text(encoding="utf-8-sig")


class AutoSelectionContracts(unittest.TestCase):
    def test_staging_uses_configuration_evidence_not_legacy_ints(self):
        source = read("iosApp/Nimbo/NimboStagingPayload.swift")
        self.assertIn("NimboAutoPingCache.shared.rankedIndices", source)
        self.assertIn("servers.map(\\.rawConfiguration)", source)
        self.assertNotIn("UserDefaults", source)
        self.assertNotIn("selectionRank", source)
        self.assertIn("maximumBalancerNodes = 16", source)

    def test_cache_has_no_identity_persistence_or_logging(self):
        source = read(CACHE)
        self.assertIn("SHA256.hash(data: Data(configuration.utf8))", source)
        for token in ("defaults.set(", "print(", "Logger", "write(", "defaults.synchronize("):
            self.assertNotIn(token, source)
        policy = read(POLICY)
        for token in ("maximumEntries = 8192", "maximumAge: TimeInterval = 300", "generation == ticket.generation",
                      "attempts[ticket.configurationIdentity] == ticket.attempt", "!cancelled", "pathEstablished",
                      "age >= 0", "age <= Self.maximumAge", "seen.insert(identity).inserted"):
            self.assertIn(token, policy)

    def test_only_configured_http_is_rankable(self):
        policy = read(POLICY)
        self.assertIn("sample.provenance == .configuredRouteHTTP", policy)
        source = read(SERVICE)
        self.assertIn("case .nimbo: return .configuredRouteHTTP", source)
        self.assertIn("case .httpGet, .httpHead: return .activeRouteHTTP", source)
        self.assertIn("case .tcp: return .tcp", source)
        self.assertIn("case .icmp: return .icmp", source)
        # Existing native contract is the basis for per-config attribution.
        native = read("iosApp/GoBridge/nimbo_diagnostic.go")
        self.assertIn("CheckRedirect", native)
        projection = read("iosApp/GoBridge/nimbo_diagnostic_projection.go")
        self.assertIn("DIAGNOSTIC_AMBIGUOUS_ROUTE", projection)
        adapter = read("iosApp/Nimbo/NimboDiagnosticProbe.swift")
        self.assertIn('object["requestID"] as? String == requestID', adapter)
        self.assertIn('object["serverID"] as? String == serverID', adapter)

    def test_tickets_precede_probe_and_obsolete_results_are_rejected(self):
        source = read(SERVICE)
        one = source.split("func measureOne(", 1)[1].split("func measureAll(", 1)[0]
        self.assertLess(one.index("begin(configuration:"), one.index("await diagnosticProbe"))
        batch = source.split("func measureAll(", 1)[1].split("private func preflight(", 1)[0]
        self.assertLess(batch.index("begin(configuration:"), batch.index("await preflight"))
        self.assertIn("generation == NimboAutoPingCache.shared.generation", source)
        self.assertIn("provenance: .tcp", batch)
        self.assertIn("cancelled: Task.isCancelled || !lease.valid", source)
        self.assertIn("return !Task.isCancelled && lease.valid ? results : nil", batch)

    def test_first_path_callback_and_session_notifications_retire_generation(self):
        source = read(MONITOR)
        handler = source.split("monitor.pathUpdateHandler =", 1)[1]
        self.assertLess(handler.index("invalidate(pathEstablished: true)"), handler.index("guard let previous"))
        self.assertLess(handler.index("invalidate(pathEstablished: true)"), handler.index("DispatchQueue.main.async"))
        self.assertIn(".NEVPNStatusDidChange, object: nil, queue: nil", source)
        self.assertIn("NimboAutoPingCache.shared.invalidate()", source)

    def test_existing_ping_harness_links_new_production_sources(self):
        harness = read("iosApp/Tests/test_ping_contracts.py")
        self.assertIn(POLICY, harness)
        self.assertIn(CACHE, harness)
        reconnect = read("scripts/ci/test-ios-reconnect.py")
        self.assertIn('"Shared/NimboAutoSelectionPolicy.swift"', reconnect)
        self.assertIn('"Nimbo/NimboAutoPingCache.swift"', reconnect)

    def test_abandoned_tickets_have_matching_attempt_cleanup(self):
        policy = read(POLICY)
        discard = policy.split("func discard(", 1)[1].split("func rankedIndices(", 1)[0]
        self.assertIn("epoch == ticket.generation", discard)
        self.assertIn("attempts[ticket.configurationIdentity] == ticket.attempt", discard)
        self.assertNotIn("samples.remove", discard)
        service = read(SERVICE)
        self.assertIn("defer { NimboAutoPingCache.shared.discard(ticket) }", service)
        self.assertIn("defer { for ticket in tickets.values { NimboAutoPingCache.shared.discard(ticket) } }", service)
        tests = read("iosApp/Tests/AutoSelectionPolicyTests.swift")
        self.assertIn("queued.entryCount == 0", tests)
        self.assertIn("store.discard(discarded)", tests)

    def test_policy_snapshot_is_bounded_and_progress_guard_is_constant_cost(self):
        cache = read(CACHE)
        self.assertNotIn(".dictionaryRepresentation()", cache)
        self.assertIn("maximumPolicyValueBytes = 256 * 1024", cache)
        self.assertIn("var generation: UUID { policy.generation }", cache)
        self.assertIn("values: [String: String]", cache)
        for key in ("connection.vpnCore", "routing.modules", "routing.profiles", "routing.profile-id",
                    "routing.bypassLocal", "routing.sniffing", "routing.adBlocking", "routing.dns",
                    "ping.protocol", "ping.url", "ping.timeoutMs"):
            self.assertIn('"com.nimbo.' + key + '"', cache)
        self.assertIn("policyUsable != (usable && identity != nil)", cache)
        self.assertIn("autoEvidenceTests()", read("iosApp/Tests/PingDiagnosticTests.swift"))

    def test_runtime_sticky_selection_is_native_owned_explicit_opt_in(self):
        # JSON opt-in only: these source contracts do not execute patched Xray.
        # The parent owns native strategy installation, build guard and integration.
        source = read("iosApp/PacketTunnel/XrayConfiguration.swift")
        self.assertIn('automaticBalancerTag = "nimbo-auto-sticky-v1"', source)
        self.assertIn('"strategy": ["type": "leastPing"]', source)
        self.assertIn('"fallbackTag": "\\(proxyTagPrefix)0"', source)
        self.assertIn('"probeInterval": automatic ? "30s" : "90s"', source)
        self.assertIn('balancerTag: source.automatic ? automaticBalancerTag : balancerTag', source)
        self.assertIn('if source.balanced {', source)
        self.assertIn('observatorySettings(automatic: source.automatic)', source)
        self.assertNotIn('"fallbackTag": "direct"', source)
        self.assertNotIn("OverrideBalancer", read(SERVICE))

    def test_imported_native_config_precedes_generated_envelope(self):
        source = read("iosApp/PacketTunnel/XrayConfiguration.swift")
        classify = source.split("private static func configurationObject(", 1)[1].split("private static func convertShareText(", 1)[0]
        self.assertLess(classify.index('object["outbounds"] is [Any]'), classify.index('object?["shareLinks"]'))
        self.assertIn("return (object, false, false)", classify)
        self.assertIn('let explicitNimboFlag = (object?["nimbo"] as? [String: Any])?["balancer"] as? Bool', classify)
        self.assertIn("let requested = explicitNimboFlag ?? true", classify)
        self.assertIn("balanced && explicitNimboFlag == true", classify)
        self.assertIn("return (try convertShareText(sourceText, bridge: bridge), false, false)", classify)

    def test_sticky_builder_fixtures_are_wired_into_existing_swift_harness(self):
        fixtures = "iosApp/Tests/AutoStickyConfigurationTests.swift"
        harness = read("iosApp/Tests/test_ping_contracts.py")
        self.assertIn(fixtures, harness)
        self.assertIn("try AutoStickyConfigurationTests.run()", read("iosApp/Tests/PingPolicyTests.swift"))
        source = read(fixtures)
        for token in ('prepare(envelope(16), converted: converted(16))', 'tag: marker, interval: "30s"',
                      '(envelope(1), 1)', '(converted(1), 1)', 'automatic: false', 'automatic: nil',
                      'tag: "balancer", interval: "90s"', 'bridge.conversionCount == 0',
                      'native.merge(envelope(16))', '"fallbackTag"] as? String == "proxy/0"',
                      'assertDNSAndTransports', 'try nativeUserRouting()', 'try moduleAndProfileRouting()'):
            self.assertIn(token, source)


def native_tests():
    if platform.system() != "Darwin":
        raise SystemExit("--swift requires macOS; no Swift execution claimed on Windows")
    with tempfile.TemporaryDirectory(prefix="nimbo-auto-policy-") as tmp:
        executable = str(pathlib.Path(tmp) / "AutoSelectionPolicyTests")
        subprocess.run(["xcrun", "swiftc", "-swift-version", "5", "-parse-as-library", POLICY,
                        "iosApp/Tests/AutoSelectionPolicyTests.swift", "-o", executable],
                       cwd=ROOT, check=True, timeout=120)
        subprocess.run([executable], check=True, timeout=30)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--swift", action="store_true")
    args = parser.parse_args()
    result = unittest.TextTestRunner(verbosity=2).run(unittest.defaultTestLoader.loadTestsFromTestCase(AutoSelectionContracts))
    if not result.wasSuccessful():
        raise SystemExit(1)
    if args.swift:
        native_tests()
