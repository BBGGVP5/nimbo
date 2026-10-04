#!/usr/bin/env python3
"""Source ownership contracts. --swift also executes portable core admission.

Native packet fixtures run in Go; Apple archive/Swift links and device acceptance
are separate gates. This file never pretends source checks execute a VPN.
"""
import argparse
import re
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]


def source(name):
    return (ROOT / name).read_text(encoding="utf-8-sig")


class PacketFlowContracts(unittest.TestCase):
    def test_session_identity_and_path_return_are_production_policies(self):
        policy = source("iosApp/Shared/NimboMihomoSessionPolicy.swift")
        bridge = source("iosApp/PacketTunnel/MihomoPacketBridge.swift")
        self.assertIn("hasBound && current != next", policy)
        self.assertIn("pathState.update", bridge)
        self.assertIn("NimboMihomoSessionPolicy.readyIdentity", bridge)
        self.assertIn("SHA256.hash(data: source)", bridge)
        self.assertIn("NimboMihomoSessionPolicy.matchesEnvelope", bridge)
        self.assertIn("NimboMihomoSessionPolicy.isRunning", bridge)
        self.assertIn("NimboMihomoSessionPolicy.exactUTF8(source)", bridge)
        self.assertIn("NimboMihomoSessionPolicy.exactUTF8(source)", source("iosApp/Nimbo/NimboMihomoControl.swift"))
        self.assertNotIn("self.index != 0 && self.index != next", bridge)
        native = source("tools/native/mihomo-core/runtime.go")
        for name in ["coreVersion", "coreCommit"]:
            self.assertEqual(re.search('static let '+name+' = "([^"]+)"', policy).group(1),
                             re.search('const '+name+' = "([^"]+)"', native).group(1))
        link = source("scripts/ci/build-libxray-awg-apple.sh")
        self.assertIn("NimboMihomoSessionPolicy.swift", link)

    def test_failed_recovery_cleans_up_and_wake_is_generation_bound(self):
        provider = source("iosApp/PacketTunnel/PacketTunnelProvider.swift")
        failure = provider.split("private func failActiveTunnel", 1)[1].split("private func", 1)[0]
        for fragment in ["lifecycleGeneration &+= 1", "cancelPings()", "stopWatchdog()", "stopPathMonitor()",
                         "mihomo.stop()", "clearRetainedStartupState()", "cancelTunnelWithError(error)"]:
            self.assertIn(fragment, failure)
        wake = provider.split("override func wake()", 1)[1].split("private func handlePing", 1)[0]
        self.assertIn("generation == self.lifecycleGeneration", wake)
        self.assertIn('mihomo.command("networkChanged")', wake)
        self.assertIn("self.failActiveTunnel", wake)
        watchdog = provider.split("private func startWatchdog()", 1)[1].split("private func stopWatchdog()", 1)[0]
        self.assertIn("else {", watchdog)
        self.assertIn("self.failActiveTunnel(PacketTunnelError.coreStoppedUnexpectedly)", watchdog)

    def test_actual_native_dispatch_not_legacy_fixture_matcher(self):
        code = source("tools/native/mihomo-core/packet_runtime.go")
        for fragment in ["sing.NewListenerHandler", "Tunnel: tunnel.Tunnel", "singTun.ListenerHandler", 'tun.NewStack("gvisor"', "s.ctx", "DnsAddrPorts: dns"]:
            self.assertIn(fragment, code)
        for forbidden in ["mobileSession", "routeProxy(", "singTun.New(", "net.Listen(", "CalculateInterfaceName"]:
            self.assertNotIn(forbidden, code)

    def test_generation_and_stop_outside_blocking_operation_mutex(self):
        code = source("tools/native/mihomo-core/packet_api.go")
        self.assertGreaterEqual(code.count("generation != singleton.generation"), 1)
        self.assertIn("singleton.session == s", code)
        self.assertIn("context.WithTimeout(s.ctx", code)
        self.assertNotIn("singleton.op.Lock()", code)
        self.assertIn('runtime.GOOS != "ios"', code)
        runtime = source("tools/native/mihomo-core/runtime.go")
        self.assertIn("packetOwner        bool", runtime)
        self.assertIn("s.packet, err = startPacketRuntime(s)", runtime)

    def test_binary_abi_limits_and_single_runtime(self):
        bridge = source("iosApp/GoBridge/nimbo_mihomo_cgo.go")
        build = source("scripts/ci/build-libxray-awg-apple.sh")
        for symbol in ["StartIOSPacketFlow", "WriteIOSPacket", "ReadIOSPacket"]:
            symbol = "NimboMihomo" + symbol + "V1"
            self.assertIn("//export " + symbol, bridge)
            self.assertIn(symbol, build)
        for fragment in ["length > 1500", "capacity < 1500", "C.GoBytes", "C.memcpy"]:
            self.assertIn(fragment, bridge)
        self.assertEqual(build.count("-buildmode=c-archive"), 1)
        self.assertIn("-tags=ios,with_gvisor", build)
        self.assertIn("MihomoPacketBridge.swift", build)

    def test_system_settings_and_public_packet_flow_match(self):
        bridge = source("iosApp/PacketTunnel/MihomoPacketBridge.swift")
        settings = source("iosApp/PacketTunnel/PacketTunnelNetwork.swift")
        for fragment in ["flow.readPackets", "flow.writePackets", "readOutstanding", "self.generation == ticket", "outputQueue.sync {}", "NimboMihomoFreeV1(pointer)"]:
            self.assertIn(fragment, bridge)
        for fragment in ['"172.19.0.1"', '"172.19.0.2"', '"fdfe:dcba:9876::1"', '"fdfe:dcba:9876::2"', "settings.mtu = 1500"]:
            self.assertIn(fragment, settings)
        for forbidden in ["utunDescriptorInfo", "borrowedFD", "LibXrayBridge", "path.status == .satisfied"]:
            self.assertNotIn(forbidden, bridge)

    def test_real_physical_egress_not_an_always_true_protector(self):
        bridge = source("iosApp/PacketTunnel/MihomoPacketBridge.swift")
        for fragment in ["IP_BOUND_IF", "IPV6_BOUND_IF", "getsockname", "if_nametoindex", 'hasPrefix("utun")', "protectorInstalled = false"]:
            self.assertIn(fragment, bridge)
        protector = bridge.split("func protect(", 1)[1].split("func close()", 1)[0]
        self.assertNotIn("return true", protector)
        self.assertNotIn("NimboMihomoInvoke", protector)

    def test_extension_routes_admission_and_engine_specific_lifecycle(self):
        provider = source("iosApp/PacketTunnel/PacketTunnelProvider.swift")
        startup = provider.split("private func startTunnelInternal", 1)[1].split("private func startMihomo", 1)[0]
        self.assertLess(startup.index("let engine = try NimboCoreAdmission.validate"), startup.index("if engine == .mihomo"))
        self.assertLess(startup.index("if engine == .mihomo"), startup.index("XrayConfigurationBuilder.moduleRulesJSON"))
        for fragment in ["mihomo.cancelPendingStart()", "self.mihomo.stop()", "self.mihomo.counters", 'mihomo.command("networkChanged")', 'request?["sourceSHA256"] as? String == sourceHash']:
            self.assertIn(fragment, provider)

    def test_import_does_not_flatten_and_live_choice_persists_after_readback(self):
        repository = source("iosApp/Nimbo/NimboSubscriptionRepository.swift")
        self.assertLess(repository.index("NimboMihomoControl.looksLikeConfiguration"), repository.index("NimboParseSubscriptionPayload"))
        admission = repository.split("func importFullConfiguration(_ configuration:", 1)[1].split("private func fullSource", 1)[0]
        self.assertLess(admission.index("configuration.validate()"), admission.index("saveFullConfiguration(configuration)"))
        self.assertLess(admission.index("NimboMihomoControl.inspection(configuration)"), admission.index("saveFullConfiguration(configuration)"))
        self.assertIn("saveFullConfiguration(candidate, expected: previous)", repository)
        control = source("iosApp/Nimbo/NimboMihomoControl.swift")
        choice = control.split("static func select(", 1)[1].split("private static func native", 1)[0]
        self.assertLess(choice.index('rpc("mihomoSelect"'), choice.index("recordingSelection"))
        self.assertIn('groups?[group.name]?["now"] as? String == member', choice)
        self.assertIn("current.sourceSHA256 == full.sourceSHA256", choice)
        self.assertIn("saveFullConfiguration(updated, expected: current)", choice)
        self.assertIn("withTaskCancellationHandler", control)

    def test_ping_native_cancellation_and_persistent_source_scoped_values(self):
        card = source("iosApp/Nimbo/NimboMihomoProfileCard.swift")
        self.assertNotIn("composePresentation", card) # private in RootView, inaccessible here
        self.assertIn("NimboMihomoPingCache", card)
        self.assertNotIn(".disabled(busy || !group.selectable)", card)
        control = source("iosApp/Nimbo/NimboMihomoControl.swift")
        self.assertIn('"cancelMihomoProbe"', control)
        self.assertIn('request["requestID"] = requestID', control)
        provider = source("iosApp/PacketTunnel/PacketTunnelProvider.swift")
        self.assertLess(provider.index('== "cancelMihomoProbe"'), provider.index("lifecycleQueue.async", provider.index("override func handleAppMessage")))
        native = source("tools/native/mihomo-core/runtime.go")
        self.assertIn("m.beginSessionProbe", native)
        bridge = source("iosApp/PacketTunnel/MihomoPacketBridge.swift")
        stop_cancel = bridge.split("func cancelPendingStart()", 1)[1].split("func stop()", 1)[0]
        self.assertIn("pendingProbeID", stop_cancel)
        self.assertIn("targetRequestId", stop_cancel)

    def test_portable_admission_target_parity(self):
        self.assertEqual(source("iosApp/Nimbo/NimboCoreSelection.swift"), source("iosApp/PacketTunnel/NimboCoreSelection.swift"))
        self.assertIn("var isAvailable: Bool { true }", source("iosApp/Nimbo/NimboCoreSelection.swift"))
        workflow = source(".github/workflows/build-ios-unsigned.yml")
        self.assertIn("test-ios-packet-flow.py --swift", workflow)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--swift", action="store_true")
    args = parser.parse_args()
    suite = unittest.defaultTestLoader.loadTestsFromTestCase(PacketFlowContracts)
    if not unittest.TextTestRunner(verbosity=2).run(suite).wasSuccessful():
        raise SystemExit(1)
    if args.swift:
        with tempfile.TemporaryDirectory(prefix="nimbo-mihomo-session-policy-") as directory:
            output = str(Path(directory) / "session-tests")
            subprocess.run(["swiftc", str(ROOT / "iosApp/Shared/NimboMihomoSessionPolicy.swift"),
                str(ROOT / "iosApp/Tests/MihomoSessionPolicyTests.swift"), "-o", output], check=True)
            subprocess.run([output], check=True)

        with tempfile.TemporaryDirectory(prefix="nimbo-core-selection-") as directory:
            output = str(Path(directory) / "core-tests")
            subprocess.run(["swiftc", str(ROOT / "iosApp/Shared/NimboAWGConfiguration.swift"),
                str(ROOT / "iosApp/Nimbo/NimboCoreSelection.swift"),
                str(ROOT / "iosApp/Tests/CoreSelectionTests.swift"), "-o", output], check=True)
            subprocess.run([output], check=True)

        with tempfile.TemporaryDirectory(prefix="nimbo-mihomo-ping-cache-") as directory:
            output = str(Path(directory) / "cache-tests")
            subprocess.run(["swiftc", str(ROOT / "iosApp/Nimbo/NimboMihomoPingCache.swift"),
                str(ROOT / "iosApp/Tests/MihomoPingCacheTests.swift"), "-o", output], check=True)
            subprocess.run([output], check=True)
