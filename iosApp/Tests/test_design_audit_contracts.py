"""Cross-platform active-design regressions; --swift compiles Foundation projections on macOS."""
import argparse
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
def read(path):
    return (ROOT / path).read_text(encoding="utf-8-sig")

class DesignAuditContracts(unittest.TestCase):
    def test_native_mihomo_uses_category_cards_not_unbounded_group_lists(self):
        card = read("iosApp/Nimbo/NimboMihomoProfileCard.swift")
        for token in ["ScrollView(.horizontal", "LazyVGrid", "activeGroup", "dynamicTypeSize.isAccessibilitySize", "NimboMihomoGroup.active"]:
            self.assertIn(token, card)
        self.assertNotIn("ForEach(groups) { group in\n                groupCard", card)
        self.assertNotIn('Text("Mihomo · полная конфигурация")', card)
        self.assertIn('.nimboFont(11, relativeTo: .caption)', card)
        self.assertIn('.frame(minWidth: 44, minHeight: 44)', card)

    def test_ios_operations_use_current_source_and_discard_cancelled_replies(self):
        card = read("iosApp/Nimbo/NimboMihomoProfileCard.swift")
        for token in ["currentFull", "pingRunID", "refreshRunID == runID", "connection?.status == status", "invalidatePing()", "!Task.isCancelled", "sourceSHA256 == request.sourceSHA256", "session.status == .connected", "onDisappear"]:
            self.assertIn(token, card)
        self.assertNotIn("NimboMihomoControl.select(group: group, member: member, full: full,", card)
        self.assertNotIn("NimboMihomoPingCache.values(sourceSHA256: full.sourceSHA256", card)
        self.assertIn("NimboMihomoGroup.declared", read("iosApp/Nimbo/NimboMihomoControl.swift"))

    def test_android_home_native_polling_observes_visible_lifecycle(self):
        code = read("app/src/main/java/com/danila/nimbo/ui/screens/NimboMiniApp.kt")
        block = code.split("private fun MihomoHomeSelectedServerBar(",1)[1].split("@Composable",1)[0]
        self.assertIn("LocalLifecycleOwner.current.lifecycle", block)
        self.assertIn("repeatOnLifecycle(Lifecycle.State.STARTED)", block)
        self.assertIn('status.data.get("sourceSHA256")?.asString != sourceHash', block)

    def test_ping_text_is_compact_without_shrinking_touch_targets(self):
        shared = read("shared/src/commonMain/kotlin/com/danila/nimbo/shared/ui/NimboPingPresentation.kt")
        self.assertIn("fontSize = 11.sp",shared)
        android = read("app/src/main/java/com/danila/nimbo/ui/components/PingValueContent.kt")
        self.assertIn("fontSize = 11.sp",android)
        css = read("apps/ui/src/components/core-subscription-groups.css")
        self.assertIn(".core-proxy-card-latency { font-size: 10px",css)
        self.assertIn("min-width: 44px; min-height: 44px",css)

def swift_tests():
    with tempfile.TemporaryDirectory(prefix="nimbo-mihomo-design-") as folder:
        exe = str(Path(folder)/"MihomoGroupTests")
        subprocess.run(["xcrun","swiftc","-parse-as-library","iosApp/Nimbo/NimboMihomoGroup.swift","iosApp/Tests/MihomoGroupTests.swift","-o",exe],cwd=ROOT,check=True)
        subprocess.run([exe],check=True)

if __name__ == "__main__":
    parser=argparse.ArgumentParser();parser.add_argument("--swift",action="store_true");args=parser.parse_args()
    result=unittest.TextTestRunner(verbosity=2).run(unittest.defaultTestLoader.loadTestsFromTestCase(DesignAuditContracts))
    if not result.wasSuccessful():raise SystemExit(1)
    if args.swift:swift_tests()
