"""Merged field-selector contracts; unrelated toolbar popups stay untouched."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]


def selector(path, name, next_name):
    source = (ROOT / path).read_text(encoding="utf-8-sig")
    return source.split(f"private fun {name}(", 1)[1].split(f"private fun {next_name}(", 1)[0]


class DropdownAlignmentTests(unittest.TestCase):
    def test_shared_card_animates_one_envelope_without_popup(self):
        source = (ROOT / "app/src/main/java/com/danila/nimbo/ui/components/NimboExpandingChoiceCard.kt").read_text(encoding="utf-8")
        self.assertEqual(source.count("Surface("), 1)
        for token in ["AnimatedVisibility(", "expandVertically(", "shrinkVertically(",
                      "expandFrom = Alignment.Top", "shrinkTowards = Alignment.Top",
                      "fadeIn(", "fadeOut(", "animateFloatAsState(", ".rotate(rotation)",
                      "selectableGroup()", "Role.RadioButton", "enabled = expanded",
                      "BackHandler(enabled = expanded)", "stateDescription =",
                      "heightIn(min = 64.dp)", "onSelect(option.value)"]:
            self.assertIn(token, source)
        self.assertNotIn("DropdownMenu", source)
        self.assertNotIn("maxLines = 1", source)

    def test_options_have_no_divider_or_edge_to_edge_highlight_seam(self):
        source = (ROOT / "app/src/main/java/com/danila/nimbo/ui/components/NimboExpandingChoiceCard.kt").read_text(encoding="utf-8")
        self.assertNotIn("HorizontalDivider", source)
        self.assertIn("padding(horizontal = 6.dp)", source)
        self.assertIn(".clip(optionShape)", source)
        self.assertLess(source.index(".clip(optionShape)"), source.index(".background(if (option.value"))

    def test_app_mode_dropdown_matches_field_and_preserves_selection(self):
        source = selector("app/src/main/java/com/danila/nimbo/ui/screens/AppProxySettingsScreen.kt",
                          "AppRoutingModeSelector", "AppSelectionFilter")
        self.assertIn("NimboExpandingChoiceCard(", source)
        self.assertIn("index + 1", source)
        self.assertIn("selectedValue = mode", source)
        self.assertIn("onSelect = onModeChange", source)
        self.assertNotIn("DropdownMenu", source)

    def test_retention_dropdown_matches_field_and_preserves_selection(self):
        source = selector("app/src/main/java/com/danila/nimbo/ui/screens/NimboMiniApp.kt",
                          "LogRetentionOptionGrid", "SettingsStepperRow")
        self.assertIn("NimboExpandingChoiceCard(", source)
        self.assertIn("selectedValue = selected", source)
        self.assertIn("onSelect = onSelect", source)
        self.assertIn("labels.zip(values)", source)
        self.assertNotIn("DropdownMenu", source)


if __name__ == "__main__":
    unittest.main()
