"""Non-mutating checks for the disposable namespace's bounded diagnostics."""
import importlib.util
from pathlib import Path
import tempfile
import socket
import struct
import unittest

spec = importlib.util.spec_from_file_location('broker', Path(__file__).with_name('test-mihomo-helper-netns.py'))
broker = importlib.util.module_from_spec(spec)
spec.loader.exec_module(broker)


class NativeDiagnosticsTests(unittest.TestCase):
    def test_packet_evidence_never_contains_payload(self):
        packet = bytearray(60)
        packet[0] = 0x60; packet[6] = 6; packet[52] = 0x50; packet[53] = 0x18
        packet[8:24] = socket.inet_pton(socket.AF_INET6, broker.fixture.TARGET6)
        packet[24:40] = socket.inet_pton(socket.AF_INET6, 'fdfe:dcba:5288::1')
        packet[40:52] = struct.pack('!HHII', 18080, 23456, 42, 100)
        row = broker.fixture.ipv6_tcp_header(packet + b'private-body', 'phys0')
        self.assertEqual(row['payloadBytes'], 12)
        self.assertEqual(row['seq'], 42)
        self.assertNotIn('private-body', str(row))
        self.assertIsNone(broker.fixture.ipv6_tcp_header(b'x' * 60, 'phys0'))

    def test_neighbor_evidence_retains_headers_only(self):
        import socket, struct
        payload = bytes([135, 0]) + b'\x00' * 6 + socket.inet_pton(socket.AF_INET6, 'fdfe:dcba:9900::1') + b'private-option'
        packet = struct.pack('!IHBB', 6 << 28, len(payload), 58, 255) + socket.inet_pton(socket.AF_INET6, 'fdfe:dcba:9900::2') + socket.inet_pton(socket.AF_INET6, 'ff02::1:ff00:1') + payload
        row = broker.fixture.ipv6_neighbor_header(packet, 'phys0')
        self.assertEqual(row['icmpType'], 135)
        self.assertEqual(row['target'], 'fdfe:dcba:9900::1')
        self.assertNotIn('private-option', str(row))
        self.assertIsNone(broker.fixture.ipv6_neighbor_header(packet[:50], 'phys0'))

    def test_physical_fixture_has_deterministic_link_local_without_dad(self):
        from unittest.mock import patch
        with patch.object(broker.fixture, 'ip') as command:
            broker.fixture.configure_link_local()
        self.assertEqual([call.args for call in command.call_args_list], [
            ('-6', 'addr', 'add', 'fe80::1/64', 'dev', 'phys0', 'nodad'),
            ('-n', 'nimbo-fixture', '-6', 'addr', 'add', 'fe80::2/64', 'dev', 'peer0', 'nodad'),
        ])

    def test_trace_is_bounded_and_keeps_latest_error(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'socket.trace'
            path.write_bytes(b'x' * 20000 + b'connect-failed\n')
            tail = broker.trace_tail(path)
            self.assertLessEqual(len(tail.encode()), 16384)
            self.assertTrue(tail.endswith('connect-failed\n'))

    def test_missing_trace_does_not_replace_original_failure(self):
        with tempfile.TemporaryDirectory() as directory:
            self.assertEqual(broker.trace_tail(Path(directory) / 'missing'), 'trace unavailable')

    def test_trace_handles_non_utf8_without_crashing(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'socket.trace'
            path.write_bytes(b'\xffconnect-failed\n')
            self.assertTrue(broker.trace_tail(path).endswith('connect-failed\n'))


if __name__ == '__main__':
    unittest.main()
