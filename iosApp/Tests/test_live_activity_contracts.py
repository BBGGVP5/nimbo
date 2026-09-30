"""Source/packaging contracts on any host; --swift executes pure policy on macOS."""
import argparse
import pathlib
import subprocess
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]
def read(path):
    return (ROOT / path).read_text(encoding="utf-8-sig")

class LiveActivityContracts(unittest.TestCase):
    def test_extension_is_separate_and_available_before_control_center(self):
        project = read("iosApp/project.yml")
        self.assertIn("NSSupportsLiveActivities: true", project)
        self.assertIn("- target: NimboLiveActivity", project)
        activity = project.split("  NimboLiveActivity:\n", 1)[1].split("  NimboControlWidget:\n", 1)[0]
        self.assertIn('iOS: "16.2"', activity)
        self.assertIn("com.apple.widgetkit-extension", activity)
        self.assertNotIn("LibXray", activity)
        self.assertNotIn("packet-tunnel-provider", activity)

    def test_widget_has_real_compact_minimal_expanded_and_stale_presentations(self):
        source = read("iosApp/LiveActivity/NimboLiveActivityWidget.swift")
        for contract in ["ActivityConfiguration", "DynamicIsland", "compactLeading", "compactTrailing", "minimal", "context.isStale"]:
            self.assertIn(contract, source)
        attributes = read("iosApp/Shared/NimboLiveActivityAttributes.swift")
        for secret in ["serverName", "profileName", "nodeID", "subscriptionURL", "password"]:
            self.assertNotIn(secret, attributes)

    def test_authoritative_single_activity_and_foreground_only_start(self):
        source = read("iosApp/Nimbo/NimboLiveActivityController.swift")
        for contract in ["areActivitiesEnabled", "Activity<NimboLiveActivityAttributes>.activities", "applicationState == .active", "pushType: nil", "dismissalPolicy: .immediate", "staleDate:", "generation", "previous?.value"]:
            self.assertIn(contract, source)
        self.assertNotIn("startVPNTunnel", source)
        self.assertNotIn("Timer.scheduledTimer", source)
        vpn = read("iosApp/Nimbo/VpnController.swift")
        self.assertIn("refreshLiveActivity()", vpn)
        self.assertIn("manager?.connection.status", vpn)
        root = read("iosApp/Nimbo/RootView.swift")
        self.assertIn("NimboLiveActivitySettingsView()", root)
        self.assertIn(".onChange(of: scenePhase)", root)

    def test_ipa_preserves_widget_when_control_widget_removed(self):
        script = read("scripts/ci/build-unsigned-ios.sh")
        self.assertIn("LIVE_ACTIVITY_EXECUTABLE", script)
        self.assertIn('"${LIVE_ACTIVITY_EXECUTABLE}"', script)
        self.assertIn("NimboLiveActivity.appex", script)
        self.assertIn("test_live_activity_contracts.py --swift", read(".github/workflows/build-ios-unsigned.yml"))

def native_tests():
    with tempfile.TemporaryDirectory(prefix="nimbo-live-policy-") as directory:
        executable = str(pathlib.Path(directory) / "PolicyTests")
        subprocess.run(["xcrun", "swiftc", "-parse-as-library", "iosApp/Shared/NimboLiveActivityPolicy.swift",
                        "iosApp/Tests/LiveActivityPolicyTests.swift", "-o", executable], cwd=ROOT, check=True)
        subprocess.run([executable], check=True)
        sdk = subprocess.check_output(["xcrun", "--sdk", "iphoneos", "--show-sdk-path"], text=True).strip()
        shared = ["iosApp/Shared/NimboLiveActivityPolicy.swift", "iosApp/Shared/NimboLiveActivityAttributes.swift"]
        # Typecheck availability against the app's old minimum before the costly full IPA build.
        subprocess.run(["xcrun", "swiftc", "-typecheck", "-parse-as-library", "-target", "arm64-apple-ios16.0",
                        "-sdk", sdk, *shared, "iosApp/Nimbo/NimboLiveActivityController.swift"], cwd=ROOT, check=True)
        subprocess.run(["xcrun", "swiftc", "-typecheck", "-parse-as-library", "-target", "arm64-apple-ios16.2",
                        "-sdk", sdk, *shared, "iosApp/LiveActivity/NimboLiveActivityWidget.swift"], cwd=ROOT, check=True)

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--swift", action="store_true")
    args = parser.parse_args()
    result = unittest.TextTestRunner().run(unittest.defaultTestLoader.loadTestsFromTestCase(LiveActivityContracts))
    if not result.wasSuccessful():
        raise SystemExit(1)
    if args.swift:
        native_tests()
