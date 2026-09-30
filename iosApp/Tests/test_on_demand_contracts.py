"""Cross-host contracts, plus executable Swift policy / iOS SDK typechecks."""
import argparse
import pathlib
import subprocess
import tempfile
import unittest
ROOT = pathlib.Path(__file__).resolve().parents[2]
def read(path): return (ROOT/path).read_text(encoding="utf-8-sig")
class OnDemandContracts(unittest.TestCase):
    def test_ordered_rules_do_not_require_public_internet(self):
        policy=read("iosApp/Shared/NimboOnDemandPolicy.swift")
        self.assertIn("var enabled = false", policy)
        self.assertLess(policy.index(".disconnectTrustedWiFi(trustedSSIDs)"), policy.index(".connectWiFi)"))
        rules=read("iosApp/Shared/NimboOnDemandRules.swift")
        for item in ["NEOnDemandRuleDisconnect", "NEOnDemandRuleConnect", "NEOnDemandRuleIgnore", "rule.ssidMatch", ".wiFi", ".cellular"]:
            self.assertIn(item, rules)
        for forbidden in ["URLSession", "probeURL =", "dnsServerAddressMatch =", "dnsSearchDomainMatch ="]:
            self.assertNotIn(forbidden, rules)
    def test_manual_stop_is_not_silently_rearmed_by_load(self):
        source=read("iosApp/Nimbo/VpnController.swift")
        self.assertIn('catch { fail(code: "IOS_ON_DEMAND_PAUSE_FAILED", error: error); return }',source)
        self.assertIn("Never called on launch",source)
        self.assertIn("NimboOnDemandRules.providerKey] == nil",source)
        self.assertIn("NimboCoreAdmission.validate(",source)
        self.assertIn("previous?.value",read("iosApp/Shared/NimboOnDemandRules.swift"))
        self.assertIn("NimboOnDemandSettingsView().environmentObject(vpn)",read("iosApp/Nimbo/RootView.swift"))
    def test_widget_is_self_contained_and_validates_before_arming(self):
        source=read("iosApp/Shared/NimboTunnelControl.swift")
        self.assertLess(source.index("try NimboCoreAdmission.validate("),source.index("NimboOnDemandRules.persist(", source.index("try NimboCoreAdmission.validate(")))
        widget=read("iosApp/project.yml").split("  NimboControlWidget:",1)[1].split("schemes:",1)[0]
        for item in ["Shared/NimboOnDemandPolicy.swift","Shared/NimboOnDemandRules.swift","Nimbo/NimboCoreSelection.swift","Shared/NimboAWGConfiguration.swift"]:
            self.assertIn(item,widget)
    def test_memory_builds_use_tracked_replacement_without_gc_timer(self):
        source=read("tools/native/libxray-memory/memory_ios.go")
        for forbidden in ["time.Sleep", "time.NewTicker", "debug.FreeOSMemory", "go func", "SetGCPercent(10)"]:
            self.assertNotIn(forbidden,source)
        self.assertIn("debug.SetMemoryLimit(30 << 20)",source)
        for path in ["scripts/ci/build-libxray-awg-apple.sh","scripts/ci/build-libxray-mihomo-android.ps1"]:
            self.assertIn("tools/native/libxray-memory/memory_ios.go",read(path))
        service=read("app/src/main/java/com/danila/nimbo/vpn/MyVpnService.kt")
        self.assertIn("nimboConfigureRuntimeMemory",service)
        self.assertNotIn("runFinalization()",service)
        self.assertNotIn("Runtime.getRuntime().gc()",service)

def swift_tests():
    with tempfile.TemporaryDirectory(prefix="nimbo-ondemand-") as directory:
        exe=str(pathlib.Path(directory)/"PolicyTests")
        subprocess.run(["xcrun","swiftc","-parse-as-library","iosApp/Shared/NimboOnDemandPolicy.swift","iosApp/Tests/OnDemandPolicyTests.swift","-o",exe],cwd=ROOT,check=True)
        subprocess.run([exe],check=True)
        sdk=subprocess.check_output(["xcrun","--sdk","iphoneos","--show-sdk-path"],text=True).strip()
        shared=["iosApp/Shared/NimboOnDemandPolicy.swift","iosApp/Shared/NimboOnDemandRules.swift"]
        subprocess.run(["xcrun","swiftc","-typecheck","-parse-as-library","-target","arm64-apple-ios16.0","-sdk",sdk,*shared],cwd=ROOT,check=True)
        subprocess.run(["xcrun","swiftc","-typecheck","-parse-as-library","-application-extension","-target","arm64-apple-ios18.0","-sdk",sdk,*shared,"iosApp/Shared/NimboConstants.swift","iosApp/Shared/NimboAWGConfiguration.swift","iosApp/Nimbo/NimboCoreSelection.swift","iosApp/Shared/NimboTunnelControl.swift"],cwd=ROOT,check=True)
        stub=pathlib.Path(directory)/"VpnController.swift"
        stub.write_text("import SwiftUI\n@MainActor final class VpnController: ObservableObject { func saveOnDemandSettings(_ settings: NimboOnDemandSettings) async throws {} }\n")
        subprocess.run(["xcrun","swiftc","-typecheck","-parse-as-library","-target","arm64-apple-ios16.0","-sdk",sdk,"iosApp/Shared/NimboOnDemandPolicy.swift","iosApp/Nimbo/NimboOnDemandSettingsView.swift",str(stub)],cwd=ROOT,check=True)
if __name__=="__main__":
    parser=argparse.ArgumentParser();parser.add_argument("--swift",action="store_true");args=parser.parse_args()
    result=unittest.TextTestRunner(verbosity=2).run(unittest.defaultTestLoader.loadTestsFromTestCase(OnDemandContracts))
    if not result.wasSuccessful(): raise SystemExit(1)
    if args.swift: swift_tests()
