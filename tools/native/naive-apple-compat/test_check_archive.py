import unittest
from check_archive import verify_symbols, MISSING, IO_CONSTRUCTOR

class ArchiveGuardTests(unittest.TestCase):
    def test_exact_unused_initializer(self):
        verify_symbols(f"features.o:\n U {MISSING}\n0000 T {IO_CONSTRUCTOR}\n")
    def test_real_kqueue_must_not_be_stubbed(self):
        for extra in ["0000 T __ZN4base17MessagePumpKqueueC1Ev", " U __ZN4base17MessagePumpKqueue3RunEv", f"0000 T {MISSING}"]:
            with self.assertRaises(ValueError):
                verify_symbols(f" U {MISSING}\n0000 T {IO_CONSTRUCTOR}\n{extra}\n")
    def test_missing_real_io_rejected(self):
        with self.assertRaises(ValueError):
            verify_symbols(f" U {MISSING}\n")
    def test_fixed_upstream_rejected(self):
        with self.assertRaises(ValueError):
            verify_symbols(f"0000 T {IO_CONSTRUCTOR}\n")

if __name__ == "__main__":
    unittest.main()
