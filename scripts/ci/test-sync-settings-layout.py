"""Small source regression contracts for the production sync/settings layout."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]


def read(path):
    return (ROOT / path).read_text(encoding="utf-8-sig")


class SyncSettingsLayoutTests(unittest.TestCase):
    def test_transport_choices_are_inline_accessible_icon_over_label(self):
        selector = read("app/src/main/java/com/danila/nimbo/ui/screens/SyncTransportSelector.kt")
        for token in ["selectableGroup()", "Role.RadioButton", "this.selected = selected",
                      "Modifier.weight(1f)", "fillMaxHeight()", "IntrinsicSize.Min", "88.dp"]:
            self.assertIn(token, selector)
        self.assertEqual(selector.count("Row("), 1)
        for key, icon in [("wifi", "Wifi"), ("bluetooth", "Bluetooth"), ("both", "Sync")]:
            self.assertIn(f'"{key}"', selector)
            self.assertIn(f"Icons.Default.{icon}", selector)
        self.assertLess(selector.index("Icon("), selector.index("Text("))
        self.assertNotIn("maxLines = 1", selector)

    def test_selector_retains_existing_preference_callback(self):
        screen = read("app/src/main/java/com/danila/nimbo/ui/screens/CrossPlatformSyncScreen.kt")
        section = screen.split('var transportMode by remember', 1)[1].split('SyncCategoryRow(', 1)[0]
        self.assertIn("SyncTransportSelector(transportMode)", section)
        self.assertIn("preferencesManager.crossSyncTransportMode = it", section)
        self.assertNotIn("OutlinedButton(", section)

    def test_settings_do_not_repeat_bottom_navigation(self):
        screen = read("app/src/main/java/com/danila/nimbo/ui/screens/NimboMiniApp.kt")
        section = screen.split("private fun NimboSettingsScreen(", 1)[1].split("private fun ColumnScope.GeneralSettingsSection", 1)[0]
        self.assertNotIn("onConnectionsClick", section)
        self.assertNotIn('t("Мои подписки",', section)
        self.assertIn('t("Обновление подписок",', section)
        self.assertIn('t("Настройки серверов",', section)
        shared = read("shared/src/commonMain/kotlin/com/danila/nimbo/shared/ui/NimboSettingsScreen.kt")
        general = shared.split("private fun GeneralPage(", 1)[1].split("private fun AppearancePage(", 1)[0]
        self.assertNotIn("NimboScreen.STATS.wireName", general)
        self.assertNotIn("NimboScreen.ROUTING.wireName", general)
        self.assertNotIn("actions.onOpenDiagnostics", general)


if __name__ == "__main__":
    unittest.main()
