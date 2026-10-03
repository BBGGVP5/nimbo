"""Non-mutating checks for the disposable namespace's bounded diagnostics."""
import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('broker', Path(__file__).with_name('test-mihomo-helper-netns.py'))
broker = importlib.util.module_from_spec(spec)
spec.loader.exec_module(broker)


class NativeDiagnosticsTests(unittest.TestCase):
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
