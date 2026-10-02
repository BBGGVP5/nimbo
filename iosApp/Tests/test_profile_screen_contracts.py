"""Source checks on Windows; --swift executes production admission/staging ordering on macOS."""
import argparse
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "iosApp/Nimbo/ProfilesContainerView.swift"


class ProfileScreenContracts(unittest.TestCase):
    def test_ping_notification_is_visible_across_source_files(self):
        actions = ROOT / "iosApp/Nimbo/NimboServerActions.swift"
        self.assertTrue(actions.exists(), "Cross-file ping action must not live in a private extension")
        source = actions.read_text(encoding="utf-8")
        self.assertIn('static let nimboPingServer = Notification.Name("com.nimbo.action.ping-server")', source)
        self.assertNotIn("private extension", source)
        root = (ROOT / "iosApp/Nimbo/RootView.swift").read_text(encoding="utf-8")
        self.assertNotIn("static let nimboPingServer", root)

    def test_native_row_is_split_into_small_type_checkable_views(self):
        source = SOURCE.read_text(encoding="utf-8")
        listing = source.split("private func serverList(", 1)[1].split("private func serverRow(", 1)[0]
        self.assertIn("serverRow(server, isSelected:", listing)
        self.assertNotIn("HStack(", listing)
        row = source.split("private func serverRow(", 1)[1].split("private func serverRowLabel(", 1)[0]
        self.assertIn("serverRowLabel(server, isSelected:", row)
        self.assertIn(".contextMenu {", row)

    def test_selection_is_one_awaited_guarded_operation(self):
        source = SOURCE.read_text(encoding="utf-8")
        selection = source.split("private func select(", 1)[1].split("private func removeConfiguration", 1)[0]
        for token in ["async", "guard !isWorking", "selectingServerID = server.id", "defer { selectingServerID = nil }",
                      "try await NimboProfileSelection.apply(", "try vpn.validateCore(data:", "try await vpn.stageConfiguration"]:
            self.assertIn(token, selection)
        self.assertNotIn("Task {", selection)
        self.assertLess(selection.index("try vpn.validateCore"), selection.index("shared.select(serverID:"))
        self.assertLess(selection.index("try await vpn.stageConfiguration"), selection.index('resultMessage = "'))
        self.assertIn("Button { Task { await select(server) } }", source)
        self.assertIn(".disabled(isWorking)", source)

    def test_profile_uses_complete_home_metadata_and_obvious_selected_rows(self):
        source = SOURCE.read_text(encoding="utf-8")
        for token in ["NimboSubscriptionMetaStore.current", "meta.announce", "Text(displayTitle(profile))",
                      "strokeBorder", "isSelected ? 2 : 0", ".accessibilityAddTraits", ".accessibilityIdentifier"]:
            self.assertIn(token, source)
        self.assertNotIn(".frame(minHeight: 140)", source)
        self.assertIn("axis: .vertical", source)
        self.assertIn(".lineLimit(2...8)", source)
        self.assertIn("accessibilityReduceMotion", source)

    def test_server_context_actions_have_no_permanent_ping_control(self):
        native = SOURCE.read_text(encoding="utf-8")
        self.assertIn('.contextMenu {', native)
        self.assertIn('post(name: .nimboPingServer, object: server.id)', native)
        self.assertIn('.accessibilityAction(named: "Пинг сервера")', native)
        shared = (ROOT / "shared/src/commonMain/kotlin/com/danila/nimbo/shared/ui/NimboProfilesScreen.kt").read_text(encoding="utf-8")
        self.assertIn('onLongClick = { menuExpanded = true }', shared)
        self.assertIn('onPing(server.id)', shared)
        self.assertIn('"Остановить пинг"', shared)
        self.assertNotIn('NimboIconButton(NimboIconName.PING', shared)

    def test_removal_is_awaited_and_does_not_delete_before_clear_succeeds(self):
        source = SOURCE.read_text(encoding="utf-8").split("private func removeConfiguration", 1)[1]
        self.assertIn("guard !isWorking", source)
        self.assertLess(source.index("try await vpn.clearConfiguration()"), source.index("try NimboConfigurationStore.shared.removeAll()"))
        self.assertIn("NimboSubscriptionMetaStore.clear()", source)
        self.assertNotIn("try? await", source)


def swift_tests():
    with tempfile.TemporaryDirectory(prefix="nimbo-profile-selection-") as directory:
        executable = str(Path(directory) / "ProfileSelectionTests")
        subprocess.run(["xcrun", "swiftc", "-parse-as-library", "iosApp/Nimbo/NimboProfileSelection.swift",
                        "iosApp/Tests/ProfileSelectionTests.swift", "-o", executable], cwd=ROOT, check=True)
        subprocess.run([executable], check=True)
        # Compile a real consumer in a separate source file, not a source-token check.
        consumer = Path(directory) / "main.swift"
        consumer.write_text('import Foundation\nprecondition(Notification.Name.nimboPingServer.rawValue == "com.nimbo.action.ping-server")\n', encoding="utf-8")
        subprocess.run(["xcrun", "swiftc", "iosApp/Nimbo/NimboServerActions.swift", str(consumer), "-o", executable], cwd=ROOT, check=True)
        subprocess.run([executable], check=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--swift", action="store_true")
    args = parser.parse_args()
    result = unittest.TextTestRunner(verbosity=2).run(unittest.defaultTestLoader.loadTestsFromTestCase(ProfileScreenContracts))
    if not result.wasSuccessful():
        raise SystemExit(1)
    if args.swift:
        swift_tests()
