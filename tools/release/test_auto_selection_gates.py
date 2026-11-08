"""Ensure production Swift auto-selection policy tests gate iOS packages.

Requires PyYAML 6 (python -m pip install PyYAML).
These tests validate workflow wiring; they do not execute Swift or build an IPA.
"""
from pathlib import Path
import unittest

import yaml

ROOT = Path(__file__).resolve().parents[2]
COMMAND = "python3 iosApp/Tests/test_auto_selection_contracts.py --swift"


def job(path, name):
    workflow = yaml.safe_load((ROOT / path).read_text(encoding="utf-8-sig"))
    return workflow["jobs"][name]


def gate_indices(value):
    return [i for i, step in enumerate(value["steps"])
            if COMMAND in [line.strip() for line in step.get("run", "").splitlines()]
            and not step.get("continue-on-error") and not step.get("if")]


class AutoSelectionGates(unittest.TestCase):
    def test_general_macos_ci_executes_swift_not_only_source_contracts(self):
        value = job(".github/workflows/ci.yml", "ios_session_contracts")
        self.assertTrue(value["runs-on"].startswith("macos-"))
        self.assertEqual(len(gate_indices(value)), 1)

    def test_lightweight_ping_checks_execute_auto_policy(self):
        value = job(".github/workflows/build-ios-unsigned.yml", "ping_checks")
        self.assertTrue(value["runs-on"].startswith("macos-"))
        self.assertEqual(len(gate_indices(value)), 1)

    def test_full_ipa_build_cannot_precede_auto_policy_gate(self):
        value = job(".github/workflows/build-ios-unsigned.yml", "build")
        self.assertTrue(value["runs-on"].startswith("macos-"))
        indices = gate_indices(value)
        self.assertEqual(len(indices), 1)
        package = next(i for i, step in enumerate(value["steps"])
                       if "bash scripts/ci/build-unsigned-ios.sh" in step.get("run", ""))
        self.assertLess(indices[0], package)


if __name__ == "__main__":
    unittest.main(verbosity=2)
