"""Anchored field dropdown source regressions; toolbar overflow menus are excluded."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]


def selector(path, name, next_name):
    source = (ROOT / path).read_text(encoding="utf-8-sig")
    return source.split(f"private fun {name}(", 1)[1].split(f"private fun {next_name}(", 1)[0]


class DropdownAlignmentTests(unittest.TestCase):
    def assert_anchored(self, source):
        for token in ["ExposedDropdownMenuBox(", "ExposedDropdownMenu(",
                      "menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)",
                      "matchAnchorWidth = true", "Modifier.fillMaxWidth()",
                      "selectableGroup()", "Role.RadioButton", "selected =", "containerColor = colors.panelFill"]:
            self.assertIn(token, source)
        self.assertNotIn("\n        DropdownMenu(", source)
        self.assertNotIn(".clickable(", source)
        self.assertNotIn("maxLines = 1", source)

    def test_app_mode_dropdown_matches_field_and_preserves_selection(self):
        source = selector("app/src/main/java/com/danila/nimbo/ui/screens/AppProxySettingsScreen.kt",
                          "AppRoutingModeSelector", "AppSelectionFilter")
        self.assert_anchored(source)
        self.assertIn("onModeChange(index + 1); expanded = false", source)
        self.assertIn("colors.textSecondary", source)
        self.assertIn("heightIn(min = 64.dp)", source)

    def test_retention_dropdown_matches_field_and_preserves_selection(self):
        source = selector("app/src/main/java/com/danila/nimbo/ui/screens/NimboMiniApp.kt",
                          "LogRetentionOptionGrid", "SettingsStepperRow")
        self.assert_anchored(source)
        self.assertIn("onSelect(value); open = false", source)
        self.assertIn("labels.zip(values)", source)


if __name__ == "__main__":
    unittest.main()
