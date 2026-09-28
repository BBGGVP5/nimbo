"""Windows source regressions for startup integration, NOT executed Swift logic.

The companion VpnStartAttemptTests.swift executes the actual portable Swift policy
when swiftc is available. These tests inspect ordering/guards in production source.
"""
from pathlib import Path
import re
import sys
import unittest

IOS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(IOS / 'build/universal-redesign/python-deps'))
from tree_sitter import Language, Parser
import tree_sitter_swift

PARSER = Parser(Language(tree_sitter_swift.language()))

def source(name):
    return (IOS / name).read_text(encoding='utf-8-sig')

def function(name, method):
    tree = PARSER.parse(source(name).encode())
    def visit(node):
        if node.type == 'function_declaration':
            identifier = node.child_by_field_name('name')
            if identifier and identifier.text.decode() == method:
                return node.text.decode()
        for child in node.children:
            result = visit(child)
            if result: return result
    result = visit(tree.root_node)
    if not result: raise AssertionError('Missing production function: ' + method)
    return result

CONTROLLER = 'Nimbo/VpnController.swift'
POLICY = 'Nimbo/NimboVpnStartAttempt.swift'

class VpnStartupSourceRegressions(unittest.TestCase):
    def test_old_disconnected_status_waits_until_progress(self):
        body = function(POLICY, 'disconnectedAction')
        self.assertRegex(body, r'case \.preparing, \.awaitingStatus:\s+return \.waitForStart')
        self.assertRegex(body, r'case \.starting:\s+phase = \.reportingFailure\s+return \.reportFailure')
        self.assertRegex(body, r'case \.reportingFailure:\s+return \.preserveFailure')
        self.assertNotIn('pendingConnection', source(CONTROLLER))

    def test_preference_awaits_cannot_resurrect_a_cancelled_start(self):
        body = function(CONTROLLER, 'connect')
        self.assertLess(body.index('startAttempt.begin()'), body.index('try await stageConfiguration'))
        self.assertGreaterEqual(body.count('guard startAttempt.isCurrent(attempt) else { return }'), 4)
        self.assertLess(body.index('let loaded = try await loadOrCreateManager()'), body.index('manager = loaded'))
        between = body.split('let loaded = try await loadOrCreateManager()', 1)[1].split('manager = loaded', 1)[0]
        self.assertIn('guard startAttempt.isCurrent(attempt)', between)
        last_await = body.index('await NimboDiagnostics.shared.record')
        actual_start = body.index('try manager.connection.startVPNTunnel()')
        self.assertIn('guard startAttempt.isCurrent(attempt)', body[last_await:actual_start])

    def test_request_and_clock_are_registered_before_system_start(self):
        body = function(CONTROLLER, 'connect')
        start = body.index('try manager.connection.startVPNTunnel()')
        self.assertLess(body.index('startAttempt.requestedStart(for: attempt)'), start)
        self.assertLess(body.index('startRequestedAt = Date()'), start)
        self.assertLess(body.index('transitionStartedAt = startRequestedAt'), start)
        self.assertIn('fail(code: "IOS_TUNNEL_START_FAILED", error: error)', body)

    def test_error_callback_is_scoped_and_rechecks_actual_connection(self):
        body = function(CONTROLLER, 'applyDisconnectError')
        failure = body.index('state = .failed')
        before = body[:failure]
        self.assertIn('startAttempt.acceptsDisconnectError(for: attempt)', before)
        self.assertIn('connection === manager?.connection', before)
        self.assertIn('connection.status == .disconnected || connection.status == .invalid', before)
        self.assertIn('synchronizeStatus()', before)
        self.assertIn('generation == token && phase == .reportingFailure', function(POLICY, 'acceptsDisconnectError'))

    def test_success_cancel_and_recovered_attempt_invalidate_callbacks(self):
        body = function(CONTROLLER, 'synchronizeStatus')
        self.assertRegex(body, r'case \.connected:\s+startAttempt.invalidate\(\)\s+state = \.connected')
        disconnect = function(CONTROLLER, 'disconnect')
        self.assertLess(disconnect.index('startAttempt.invalidate()'), disconnect.index('await'))
        self.assertIn('if phase == .reportingFailure { generation &+= 1 }', function(POLICY, 'observedProgress'))
        self.assertIn('generation &+= 1', function(POLICY, 'invalidate'))

    def test_real_failures_persist_and_stalled_start_still_times_out(self):
        body = function(CONTROLLER, 'synchronizeStatus')
        self.assertIn('case .reportFailure:\n                reportUnexpectedDisconnect()', body)
        self.assertIn('if case .failed = state { break }', body)
        poll = function(CONTROLLER, 'scheduleStatusPollIfNeeded')
        self.assertIn('if waited > 30', poll)
        self.assertIn('code: "IOS_VPN_START_TIMEOUT"', poll)
        self.assertIn('!startAttempt.isPending, waited > 5', poll)
        self.assertIn('guard !startAttempt.isPreparing else { return }', poll)

    def test_readiness_is_not_used_as_disconnect_cause(self):
        body = function(CONTROLLER, 'applyDisconnectError')
        self.assertNotIn('NimboSigningReport', body)
        self.assertIn('iOS не сообщила причину', body)
        self.assertIn('message: presentation.message', body)
        presentation = function(CONTROLLER, 'errorPresentation')
        self.assertNotIn('Переподпишите', presentation)
        self.assertIn('NimboRedactor.redact(described)', presentation)
        self.assertIn('IOS_VPN_PERMISSION_DENIED', presentation)
        # The independent readiness inspection still exists; no signature bypass.
        self.assertIn('NimboSigningReport.problem', function(CONTROLLER, 'prepare'))

    def test_notifications_are_filtered_to_the_owned_connection(self):
        self.assertIn('notification.object as? NEVPNConnection', source(CONTROLLER))
        self.assertIn('connection === self.manager?.connection', source(CONTROLLER))

    def test_portable_swift_regressions_are_present_and_parse(self):
        swift = source('Tests/VpnStartAttemptTests.swift')
        for scenario in ['staleDisconnectedDoesNotMeanFailure', 'observedStartupFailureIsReportedOnce',
                         'successInvalidatesLateError', 'cancelAndRetryInvalidateOldWork',
                         'recoveredTransitionInvalidatesOldError', 'preflightStatusDoesNotPretendStartWasRequested']:
            self.assertIn('static func ' + scenario + '()', swift)
        self.assertFalse(PARSER.parse(swift.encode()).root_node.has_error)
        self.assertFalse(PARSER.parse(source(POLICY).encode()).root_node.has_error)

class NativeOperationPhraseSourceRegressions(unittest.TestCase):
    def test_rotation_is_cancellable_and_reduced_motion_freezes_it(self):
        body = source('Nimbo/NimboOperationPhrase.swift')
        self.assertIn('.task(id: RotationKey(operation: operation, active: isActive, reduceMotion: reduceMotion))', body)
        self.assertIn('guard isActive, !reduceMotion else { return }', body)
        self.assertIn('Task.sleep(nanoseconds: 4_500_000_000)', body)
        self.assertIn('catch { return }', body)
        self.assertIn('guard !Task.isCancelled else { return }', body)
        self.assertIn('transaction.animation = nil', body)
        self.assertNotIn('Timer', body)

    def test_sync_phrases_only_appear_in_the_actual_working_branch(self):
        ui = source('Nimbo/NimboSyncView.swift')
        working = ui.split('case .working:', 1)[1].split('case let .completed', 1)[0]
        self.assertIn('NimboOperationPhrase(operation: .syncTransfer, isActive: true)', working)
        self.assertEqual(ui.count('NimboOperationPhrase('), 1)
        self.assertIn('NimboNotice(title: "Переносим данные…"', working)

    def test_connection_and_download_phrases_follow_real_state(self):
        home = source('Nimbo/HomeContainerView.swift')
        self.assertRegex(home, r'NimboOperationPhrase\(operation: \.connection,\s+isActive: vpn.state == \.preparing \|\| vpn.state == \.connecting\)')
        ui = source('Nimbo/NimboUpdateDownloadView.swift')
        busy = ui.split('if isDownloading {', 1)[1].split('} else if let error', 1)[0]
        self.assertIn('NimboOperationPhrase(operation: .updateDownload, isActive: isDownloading)', busy)
        self.assertEqual(ui.count('NimboOperationPhrase('), 1)
        self.assertIn('NimboNotice(title: "Загружаем файл сборки…"', busy)

    def test_secondary_copy_cannot_drive_or_fake_operation_state(self):
        body = source('Nimbo/NimboOperationPhrase.swift')
        self.assertIn('.foregroundStyle(NimboNative.secondary)', body)
        for forbidden in ['ProgressView(value:', 'state =', 'engine.', 'vpn.', 'URLSession', 'успешно', '100%']:
            self.assertNotIn(forbidden, body)
        for requested in ['Забираем новую версию…', 'Наводим порядок…', 'Ещё немного…',
                          'Собираем настройки…', 'Готовим передачу…']:
            self.assertIn(requested, body)

if __name__ == '__main__':
    unittest.main()
