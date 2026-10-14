"""Integration wiring only; the paired Swift tests execute blocking/cancel races."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]


def read(path):
    p = ROOT / path
    return p.read_text(encoding='utf-8-sig') if p.exists() else ''


class MihomoStartResponsiveness(unittest.TestCase):
    def test_worker_has_bounded_wait_without_false_retirement(self):
        worker = read('iosApp/Shared/NimboCoreValidationWorker.swift')
        self.assertIn('timeout: TimeInterval = 12', worker)
        self.assertIn('guard owner == nil', worker)
        self.assertIn('func cancelCurrent()', worker)
        self.assertIn('retire(id)', worker)
        self.assertNotIn('DispatchQueue.main.sync', worker)
        tests = read('iosApp/Tests/CoreValidationWorkerTests.swift')
        for scenario in ['mainActorRemainsResponsive', 'timeoutRetainsOwnership',
                         'cancelRejectsLateAdmission', 'queuedCancellationAndErrors']:
            self.assertIn(scenario, tests)

    def test_controller_awaits_readonly_preparation_and_checks_snapshot(self):
        controller = read('iosApp/Nimbo/VpnController.swift')
        self.assertIn('func validateCore(data: Data) async throws', controller)
        self.assertIn('try await validatePreparedProfile(data: data)', controller)
        self.assertIn('NimboVpnProfilePreparation.prepare(', controller)
        self.assertIn('coreValidationWorker.cancelCurrent()', controller)
        self.assertIn('prepared.storeRevision == NimboConfigurationStore.shared.revision', controller)
        validate = controller.split('func validateCore(data: Data)', 1)[1].split('/// Saves the next-start', 1)[0]
        self.assertNotIn('NimboMihomoControl.preflight(', validate)
        self.assertNotIn('NimboConfigurationStore.shared.loadFullConfiguration()', validate)

    def test_card_does_not_call_synchronous_native_inspection(self):
        card = read('iosApp/Nimbo/NimboMihomoProfileCard.swift')
        self.assertIn('try await NimboMihomoControl.readDeclared(', card)
        self.assertNotIn('NimboMihomoControl.declaredGroups(', card)
        self.assertNotIn('NimboMihomoControl.inspection(', card)


if __name__ == '__main__':
    unittest.main()
