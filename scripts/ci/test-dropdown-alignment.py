"""Field choice selectors grow in place inside one card; no floating dropdown windows."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
CARD = "app/src/main/java/com/danila/nimbo/ui/components/NimboExpandingChoiceCard.kt"


def selector(path, name, next_name):
    source = (ROOT / path).read_text(encoding="utf-8-sig")
    return source.split(f"private fun {name}(", 1)[1].split(f"private fun {next_name}(", 1)[0]


class ExpandingChoiceCardTests(unittest.TestCase):
    def test_card_is_inline_accessible_and_preserves_selection(self):
        source = (ROOT / CARD).read_text(encoding="utf-8-sig")
        for token in ["Modifier.fillMaxWidth()", "color = colors.panelFill",
                      "selectableGroup()", "Role.RadioButton", "selected = option.value == selectedValue",
                      "onSelect(option.value)", "expanded = false", "heightIn(min = 64.dp)",
                      "colors.textSecondary", "BackHandler(enabled = expanded)", "Key.Escape",
                      "AnimatedVisibility("]:
            self.assertIn(token, source)
        for token in ["DropdownMenu(", "ExposedDropdownMenuBox(", "Popup(", "maxLines = 1"]:
            self.assertNotIn(token, source)

    def assert_delegates(self, source, tag):
        self.assertIn("NimboExpandingChoiceCard(", source)
        self.assertIn(f'testTag = "{tag}"', source)
        self.assertNotIn("DropdownMenu(", source)
        self.assertNotIn(".clickable(", source)

    def test_app_mode_selector_uses_choice_card(self):
        source = selector("app/src/main/java/com/danila/nimbo/ui/screens/AppProxySettingsScreen.kt",
                          "AppRoutingModeSelector", "AppSelectionFilter")
        self.assert_delegates(source, "app-routing-mode")
        self.assertIn("NimboChoiceOption(index + 1, title, description)", source)
        self.assertIn("onSelect = onModeChange", source)

    def test_retention_selector_uses_choice_card(self):
        source = selector("app/src/main/java/com/danila/nimbo/ui/screens/NimboMiniApp.kt",
                          "LogRetentionOptionGrid", "SettingsStepperRow")
        self.assert_delegates(source, "log-retention")
        self.assertIn("labels.zip(values)", source)
        self.assertIn("selectedValue.takeIf { it in values } ?: 24", source)


if __name__ == "__main__":
    unittest.main()
