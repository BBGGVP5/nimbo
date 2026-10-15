"""Pure fixture tests: no sockets, PowerShell, registry, service or WFP calls."""
import importlib.util
import json
import socket
import struct
import subprocess
import unittest
import io
from pathlib import Path
from unittest.mock import Mock, patch

spec = importlib.util.spec_from_file_location(
    "windows_fixture", Path(__file__).with_name("test-mihomo-windows-tun.py"))
fixture = importlib.util.module_from_spec(spec)
with patch.object(socket, "socket", side_effect=AssertionError("no live sockets")):
    spec.loader.exec_module(fixture)


class CleanupTests(unittest.TestCase):
    def test_clean_success(self):
        calls = []
        fixture.finish_with_cleanup(None, [("check", lambda: calls.append("check"))])
        self.assertEqual(calls, ["check"])

    def test_original_failure_is_preserved(self):
        original = subprocess.CalledProcessError(101, ["driver"])
        with self.assertRaises(subprocess.CalledProcessError) as raised:
            fixture.finish_with_cleanup(original, [("check", lambda: None)])
        self.assertIs(raised.exception, original)

    def test_primary_and_all_cleanup_failures_are_reported(self):
        original = subprocess.CalledProcessError(101, ["driver"])
        calls = []

        def failed(name):
            calls.append(name)
            raise AssertionError(name)

        with self.assertRaises(BaseExceptionGroup) as raised:
            fixture.finish_with_cleanup(original, [
                ("proxy", lambda: failed("proxy")),
                ("firewall", lambda: failed("firewall")),
                ("routes", lambda: calls.append("routes")),
            ])
        self.assertIs(raised.exception.exceptions[0], original)
        self.assertEqual(calls, ["proxy", "firewall", "routes"])
        self.assertEqual(len(raised.exception.exceptions), 3)
        self.assertIn("proxy", raised.exception.exceptions[1].__notes__[0])

    def test_cleanup_only_failure_is_fatal(self):
        def failed():
            raise AssertionError("proxy")
        with self.assertRaises(AssertionError):
            fixture.finish_with_cleanup(None, [("proxy", failed)])

    def test_counter_diagnostic_failure_does_not_skip_close_or_uninstall(self):
        calls = []
        def broken_trace():
            raise ValueError("trace unavailable")
        with self.assertRaises(ValueError):
            fixture.finish_with_cleanup(None, [
                ("trace", broken_trace), ("close", lambda: calls.append("close")),
                ("uninstall", lambda: calls.append("uninstall"))])
        self.assertEqual(calls, ["close", "uninstall"])

    def test_nonzero_reset_is_fatal_even_if_uninstall_and_checks_succeed(self):
        fake = Mock()
        fake.diagnostics.return_value = {}
        with patch.object(fixture.subprocess, "run", return_value=Mock(returncode=9)) as run, \
             patch.object(fixture, "ps", return_value="0"), \
             patch.object(fixture, "snapshot", return_value={}), \
             patch.object(Path, "exists", return_value=False), patch("builtins.print"):
            steps = fixture.make_cleanup_steps(True, Path("driver"), {}, fake,
                Path("svc"), {}, "0", "0", Path("fixture-journal"))
            with self.assertRaisesRegex(AssertionError, "explicit owner reset failed"):
                fixture.finish_with_cleanup(None, steps)
        self.assertEqual(run.call_count, 2)
        self.assertIn("--exact", run.call_args_list[0].args[0])
        self.assertEqual(run.call_args_list[0].kwargs["timeout"], 20)
        self.assertEqual(run.call_args_list[1].kwargs["timeout"], 45)

    def test_interrupt_does_not_prevent_other_cleanup_checks(self):
        calls = []
        def interrupted():
            raise KeyboardInterrupt()
        with self.assertRaises(BaseExceptionGroup):
            fixture.finish_with_cleanup(
                RuntimeError("primary"), [("reset", interrupted), ("last", lambda: calls.append(1))])
        self.assertEqual(calls, [1])

    def test_real_cleanup_plan_runs_every_check_despite_multiple_failures(self):
        calls = []
        fake = Mock()
        fake.diagnostics.return_value = {"counts": {}, "events": []}
        def run(argv, **_):
            calls.append("reset" if argv[0] == "driver" else "uninstall")
            if argv[0] == "driver":
                raise subprocess.TimeoutExpired(argv, 20)
            raise subprocess.CalledProcessError(2, argv)
        def ps(code, **_):
            if "ProxyEnable" in code:
                calls.append("proxy")
                return "owned-proxy"
            if "Get-NetFirewallProfile" in code:
                calls.append("firewall")
                return "firewall-before"
            calls.append("adapter")
            return "0"
        def snapshot():
            calls.append("routes")
            return {"routes": []}
        with patch.object(fixture.subprocess, "run", side_effect=run), \
             patch.object(fixture, "ps", side_effect=ps), \
             patch.object(fixture, "snapshot", side_effect=snapshot), \
             patch.object(Path, "exists", return_value=False), \
             patch("builtins.print"):
            steps = fixture.make_cleanup_steps(
                True, Path("driver"), {}, fake, Path("svc"), {"routes": []},
                "proxy-before", "firewall-before", Path("fixture-journal"))
            with self.assertRaises(BaseExceptionGroup) as raised:
                fixture.finish_with_cleanup(subprocess.CalledProcessError(101, ["driver"]), steps)
        self.assertEqual(calls, ["reset", "uninstall", "adapter", "adapter", "proxy", "firewall", "routes"])
        self.assertEqual(len(raised.exception.exceptions), 4)
        fake.close.assert_called_once()


class TraceTests(unittest.TestCase):
    def test_tail_and_metadata_are_bounded(self):
        trace = fixture.FixtureTrace(max_events=3)
        for i in range(10):
            trace.record("dns-response", increment="dns", txid=i, secret="never print",
                         error="x" * 10000)
        result = trace.snapshot()
        self.assertEqual(result["counts"]["dns"], 10)
        self.assertEqual(result["dropped"], 7)
        self.assertEqual([event["txid"] for event in result["events"]], [7, 8, 9])
        self.assertNotIn("secret", json.dumps(result))
        self.assertLess(len(json.dumps(result)), 2000)
        self.assertTrue(all("unix_ms" in event for event in result["events"]))

    def test_returned_snapshot_cannot_mutate_trace(self):
        trace = fixture.FixtureTrace()
        trace.record("udp-response", increment="udp", bytes=4)
        result = trace.snapshot()
        result["counts"]["udp"] = 100
        result["events"][0]["bytes"] = 100
        self.assertEqual(trace.snapshot()["counts"]["udp"], 1)
        self.assertEqual(trace.snapshot()["events"][0]["bytes"], 4)


class DnsTests(unittest.TestCase):
    query = bytes.fromhex("123401000001000000000000") + b"\x07fixture\x07invalid\0\0\1\0\1"

    def test_synthetic_response_preserves_transaction_and_answer(self):
        reply = fixture.fixture_dns_reply(self.query)
        self.assertEqual(reply[:2], self.query[:2])
        self.assertEqual(reply[12:12 + len(self.query[12:])], self.query[12:])
        self.assertEqual(reply[-4:], bytes([192, 0, 2, 123]))
        self.assertEqual(struct.unpack("!HH", reply[4:8]), (1, 1))

    def test_truncated_or_foreign_questions_are_rejected_without_index_errors(self):
        for data in (b"", self.query[:15], self.query[:-2], self.query.replace(b"fixture", b"foreign")):
            with self.subTest(data=data), self.assertRaises(ValueError):
                fixture.fixture_dns_reply(data)

    def test_only_synthetic_socks_targets_are_reported(self):
        class Input:
            def __init__(self, data):
                self.data = io.BytesIO(data)
            def recv(self, size):
                return self.data.read(size)
        for address, expected in ((b"fixture.invalid", "fixture.invalid"),
                                  (b"credential.example", "other")):
            data = bytes([3, len(address)]) + address + struct.pack("!H", 18080)
            self.assertEqual(fixture.socks_address(Input(data)), (3, expected, 18080))


class RepeatedRoundsTests(unittest.TestCase):
    def test_bounded_round_counts(self):
        for value in (1, 2, 4, 8, '1', '2', '4', '8'):
            self.assertEqual(fixture.fixture_round_count(value), int(value))
        for value in (0, 3, 9, -1, True, 'x', '1.5', 1.5):
            with self.subTest(value=value), self.assertRaises((ValueError, TypeError)):
                fixture.fixture_round_count(value)

    def test_default_runs_original_order_once(self):
        with patch.object(fixture.subprocess, 'run') as run:
            fixture.run_driver_rounds(Path('driver'), {'fixture': 'only'}, lambda *_: None, lambda *_: None)
        self.assertEqual([call.args[0][3] for call in run.call_args_list], [
            'actual_windows_broker_session_tcp_udp_dns_selection_and_cleanup',
            'actual_both_kill_switch_denies_physical_bypass_and_survives_native_death'])

    def test_rounds_preserve_order_deadline_and_callbacks(self):
        events = []
        with patch.object(fixture.subprocess, 'run', side_effect=lambda argv, **kw: events.append(('run', argv[3], kw))) as run:
            fixture.run_driver_rounds(Path('driver'), {'fixture': 'only'},
                lambda r, c: events.append(('before', r, c)), lambda r, c: events.append(('after', r, c)), rounds=4)
        self.assertEqual(run.call_count, 8)
        for index in range(8):
            before, call, after = events[index * 3:index * 3 + 3]
            self.assertEqual(before, ('before', index // 2, fixture.MANDATORY_CASES[index % 2]))
            self.assertEqual(after, ('after', index // 2, fixture.MANDATORY_CASES[index % 2]))
            self.assertEqual(call[2], dict(env={'fixture': 'only'}, check=True, timeout=240))

    def test_first_failure_stops_all_later_cases_and_rounds(self):
        original = subprocess.CalledProcessError(101, ['driver'])
        before, after = [], []
        with patch.object(fixture.subprocess, 'run', side_effect=[None, original]) as run:
            with self.assertRaises(subprocess.CalledProcessError) as raised:
                fixture.run_driver_rounds(Path('driver'), {}, lambda *x: before.append(x), lambda *x: after.append(x), rounds=8)
        self.assertIs(raised.exception, original)
        self.assertEqual(run.call_count, 2)
        self.assertEqual(len(before), 2)
        self.assertEqual(len(after), 1)

    def test_invalid_round_count_never_executes_driver(self):
        with patch.object(fixture.subprocess, 'run') as run:
            with self.assertRaises(ValueError):
                fixture.run_driver_rounds(Path('driver'), {}, lambda *_: None, lambda *_: None, rounds=3)
        run.assert_not_called()


if __name__ == "__main__":
    unittest.main()
