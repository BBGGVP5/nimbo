"""Read-only integration contracts; native event behavior has a rustc-only gate.

Run: python tools/release/test_windows_startup.py
Native: rustc --edition=2021 --test
        apps/ui/src-tauri/tests/window_activation_standalone.rs -o <scratch>/activation.exe
"""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]
LIB = (ROOT / "apps/ui/src-tauri/src/lib.rs").read_text(encoding="utf-8")
TRAY = (ROOT / "apps/ui/src-tauri/src/tray.rs").read_text(encoding="utf-8")


class WindowsStartupContracts(unittest.TestCase):
    def test_listener_exists_before_singleton_claim_so_startup_requests_are_not_lost(self):
        run = LIB.split("pub fn run() {", 1)[1]
        self.assertLess(run.index("WindowActivation::open("), run.index("acquire_single_instance()"))

    def test_duplicate_forwards_protected_launch_or_requests_reveal_then_returns(self):
        branch = LIB.split("let Some(single_instance_guard) = acquire_single_instance() else {", 1)[1]
        branch = branch.split("let app_state =", 1)[0]
        self.assertIn("queue_run_through_request(path)", branch)
        self.assertIn("} else {", branch)
        self.assertIn("signal.request()", branch)
        self.assertIn("return;", branch)
        self.assertNotIn("tauri::Builder", branch)
        self.assertNotIn("process::Command", branch)

    def test_singleton_and_protected_launch_transport_remain_in_place(self):
        self.assertIn('OsStr::new("Local\\\\Nimbo.Ui.Singleton")', LIB)
        self.assertIn(".manage(single_instance_guard)", LIB)
        self.assertIn('serde_json::to_vec(path)', LIB)
        self.assertIn('.emit("nimbo:run-through-request", executable_path)', LIB)

    def test_reveal_is_consumed_by_existing_loop_and_reuses_tray_restore(self):
        loop = LIB.split("let protected_launch_handle =", 1)[1].split(".start_minimized", 1)[0]
        self.assertIn("signal.take_request()", loop)
        self.assertIn("Ok(true)", loop)
        compact = re.sub(r"\s+", "", loop)
        self.assertIn("if!EXIT_CLEANUP_DONE.load(", compact)
        self.assertIn("tray::show_main_window(&reveal_handle)", loop)
        self.assertLess(compact.index("run_on_main_thread(move||"), compact.index("if!EXIT_CLEANUP_DONE.load("))
        self.assertIn("drain_run_through_requests()", loop)
        helper = TRAY.split("pub(crate) fn show_main_window(", 1)[1].split("fn refresh_all_subscriptions", 1)[0]
        self.assertLess(helper.index("window.show()"), helper.index("window.unminimize()"))
        self.assertLess(helper.index("window.unminimize()"), helper.index("window.set_focus()"))


if __name__ == "__main__":
    unittest.main()
