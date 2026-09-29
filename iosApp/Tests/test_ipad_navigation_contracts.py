"""Source-level iPad navigation checks; these are not a Swift/iOS build."""

from pathlib import Path
import re
import unittest

NIMBO = Path(__file__).resolve().parents[1] / "Nimbo"


class IPadNavigationContracts(unittest.TestCase):
    def test_regular_width_uses_sidebar_and_compact_keeps_native_tab_bar(self):
        root = (NIMBO / "RootView.swift").read_text(encoding="utf-8-sig")
        screen = root.split("private var screen: some View {", 1)[1].split(
            "private var lifecycleLayer: some View", 1
        )[0]
        self.assertIn(r"@Environment(\.horizontalSizeClass)", root)
        self.assertIn("if UIDevice.current.userInterfaceIdiom == .pad && horizontalSizeClass == .regular", screen)
        self.assertIn("NimboWideSidebar(selection: $selectedTab)", screen)
        self.assertIn("NimboTabBar(selection: $selectedTab)", screen)
        self.assertEqual(screen.count("ComposeScreen(tab: selectedTab)"), 2)
        self.assertIn(".onChange(of: selectedTab)", root)
        self.assertIn("NimboSetIosScreen(wireName: tab.rawValue)", root)

    def test_sidebar_reaches_all_eight_existing_pages_accessibly(self):
        source = (NIMBO / "NimboTabBar.swift").read_text(encoding="utf-8-sig")
        sidebar = source.split("struct NimboWideSidebar: View {", 1)[1]
        groups = re.findall(r"private let (\w+Tabs): \[NimboTab\] = \[([^]]+)\]", sidebar)
        self.assertEqual([name for name, _ in groups], ["primaryTabs", "toolTabs", "appTabs"])
        cases = [case for _, items in groups for case in re.findall(r"\.(\w+)", items)]
        self.assertEqual(cases, ["home", "profiles", "stats", "routing", "modules",
                                 "routingProfiles", "notifications", "settings"])
        self.assertIn("ScrollView", sidebar)
        self.assertIn(".frame(minHeight: 48)", sidebar)
        self.assertIn('accessibilityIdentifier("nimbo.sidebar.\\(tab.rawValue)")', sidebar)
        self.assertIn("accessibilityAddTraits", sidebar)

    def test_cloud_ipa_build_checks_ipad_navigation_before_xcode(self):
        workflow = (NIMBO.parents[1] / ".github/workflows/build-ios-unsigned.yml").read_text(encoding="utf-8")
        check = "python3 iosApp/Tests/test_ipad_navigation_contracts.py"
        build = "run: bash scripts/ci/build-unsigned-ios.sh"
        self.assertIn(check, workflow)
        self.assertLess(workflow.index(check), workflow.index(build))


if __name__ == "__main__":
    unittest.main()
