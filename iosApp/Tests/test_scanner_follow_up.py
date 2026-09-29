"""Structural source contracts, not execution of Swift/AVFoundation or layout."""
from pathlib import Path
import hashlib
import json
import sys
import unittest

IOS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(IOS / 'build/universal-redesign/python-deps'))
from tree_sitter import Language, Parser
import tree_sitter_swift
PARSER = Parser(Language(tree_sitter_swift.language()))

def read(path):
    p = IOS / path
    return p.read_text(encoding='utf-8-sig') if p.exists() else ''

class ScannerFollowUp(unittest.TestCase):
    def test_baseline_untouched_and_no_unrelated_production_changes(self):
        folder = IOS / 'build/scanner-follow-up-2026-09-19'
        hashes = json.loads((folder / 'baseline-sha256.json').read_text())
        allowed = {'Nimbo/NimboQrScannerView.swift', 'Nimbo/NimboNativeDesign.swift'}
        # Explicit later visual-only slice; its independent immutable baseline
        # and stricter function/controller contracts are checked by
        # test_final_native_routes.py. Never rebaseline the scanner work.
        allowed |= {'Nimbo/NimboTabBar.swift', 'Nimbo/ProfilesContainerView.swift', 'Nimbo/RootView.swift'}
        allowed_new = {'Nimbo/NimboCameraCapture.swift', 'Nimbo/NimboCameraLifecycle.swift'}
        for name, expected in hashes.items():
            self.assertEqual(hashlib.sha256((folder / 'baseline' / name).read_bytes()).hexdigest(), expected, name)
            if name.startswith(('docs/', 'Tests/', 'Tools/')) or name in allowed:
                continue
            self.assertEqual(hashlib.sha256((IOS / name).read_bytes()).hexdigest(), expected, name)
        for path in IOS.rglob('*'):
            if not path.is_file():
                continue
            name = path.relative_to(IOS).as_posix()
            if name.startswith(('build/', 'docs/', 'Tests/', 'Tools/')):
                continue
            self.assertTrue(name in hashes or name in allowed_new, name)

    def test_controller_is_main_actor_not_metadata_delegate(self):
        ui = read('Nimbo/NimboQrScannerView.swift')
        self.assertIn('@MainActor\nprivate final class NimboCameraController: UIViewController', ui)
        for forbidden in ['AVCaptureMetadataOutputObjectsDelegate', 'cameraQueue.async', 'session.startRunning()', 'session.stopRunning()']:
            self.assertNotIn(forbidden, ui)

    def test_capture_owner_serializes_configuration_start_stop_and_metadata(self):
        capture = read('Nimbo/NimboCameraCapture.swift')
        self.assertIn('NSObject, AVCaptureMetadataOutputObjectsDelegate, @unchecked Sendable', capture)
        self.assertIn('private let session = AVCaptureSession()', capture)
        self.assertIn('private let queue = DispatchQueue(', capture)
        self.assertIn('output.setMetadataObjectsDelegate(self, queue: queue)', capture)
        self.assertGreaterEqual(capture.count('dispatchPrecondition(condition: .onQueue(queue))'), 3)
        for fragment in ['session.beginConfiguration()', 'defer { session.commitConfiguration() }', 'session.startRunning()', 'session.stopRunning()']:
            self.assertIn(fragment, capture)
        self.assertNotIn('UIViewController', capture)

    def test_no_ui_or_framework_objects_cross_callback_boundary(self):
        capture = read('Nimbo/NimboCameraCapture.swift')
        self.assertIn('enum Event: Sendable', capture)
        self.assertIn('@MainActor @Sendable (NimboCameraRequest, Event) -> Void', capture)
        self.assertIn('Task { @MainActor in', capture)
        self.assertIn('guard !request.isCancelled else { return }', capture)
        self.assertIn('case scanned(String)', capture)
        self.assertIn('case failure(String)', capture)
        self.assertNotIn('self.view', capture)
        self.assertIn('guard session.isRunning else {', capture)

    def test_permission_is_guarded_before_main_callback_can_start_capture(self):
        ui = read('Nimbo/NimboQrScannerView.swift')
        block = ui.split('AVCaptureDevice.requestAccess(for: .video)', 1)[1]
        self.assertIn('Task { @MainActor in', block)
        self.assertIn('self.lifecycle.accepts(request)', block)
        self.assertLess(block.index('self.lifecycle.accepts(request)'), block.index('self.capture.start(request: request)'))

    def test_disappearance_scene_and_dismantle_cancel(self):
        ui = read('Nimbo/NimboQrScannerView.swift')
        for fragment in ['isSceneActive: scenePhase == .active', 'controller.setSceneActive(isSceneActive)',
                         'controller.dismantle()', 'override func viewWillDisappear',
                         'updateCapture(visible: false)', 'lifecycle.finish()', 'capture.stop()']:
            self.assertIn(fragment, ui)
        self.assertNotIn('if phase == .active { cameraError = nil; cameraGeneration += 1 }', ui)
        policy = read('Nimbo/NimboCameraLifecycle.swift')
        self.assertIn('request?.cancel()', policy)
        self.assertIn('request === candidate', policy)
        self.assertIn('visible && sceneActive && !finished', policy)
        update = ui.split('private func updateCapture(', 1)[1].split('private func requestPermission(', 1)[0]
        self.assertIn('else if hadRequest && lifecycle.request == nil', update)
        self.assertLess(update.index('lifecycle.update('), update.index('capture.stop()'))

    def test_cancelled_queued_start_and_metadata_are_rejected(self):
        capture = read('Nimbo/NimboCameraCapture.swift')
        self.assertGreaterEqual(capture.count('guard !request.isCancelled else { return }'), 3)
        policy = read('Nimbo/NimboCameraLifecycle.swift')
        self.assertIn('private let lock = NSLock()', policy)
        self.assertIn('defer { lock.unlock() }', policy)
        self.assertIn('cancelled = true', policy)

    def test_qr_contract_and_single_delivery_preserved(self):
        ui = read('Nimbo/NimboQrScannerView.swift')
        block = ui.split('case let .scanned(value):', 1)[1]
        self.assertIn('purpose == .sync && !value.hasPrefix("nimbo-sync://")', block)
        self.assertLess(block.index('lifecycle.finish()'), block.index('onScan(value)'))
        self.assertLess(block.index('capture.stop()'), block.index('onScan(value)'))
        self.assertNotIn('trimmingCharacters', block)

    def test_sheet_scroll_safe_area_and_keyboard_paths_are_preserved(self):
        design = read('Nimbo/NimboNativeDesign.swift')
        self.assertIn('ScrollView {', design)
        self.assertIn('.scrollDismissesKeyboard(.interactively)', design)
        for line in design.splitlines():
            if 'ignoresSafeArea' in line:
                self.assertIn('.background(NimboNative.canvas.ignoresSafeArea())', line)
        for name in ['AboutView', 'DiagnosticsView', 'ReadinessView', 'NimboSyncView', 'NimboProfileInfoView', 'NimboUpdateDownloadView', 'NimboQrScannerView', 'ProfilesContainerView']:
            ui = read('Nimbo/' + name + '.swift')
            self.assertIn('NimboPage {', ui, name)
            self.assertNotIn('.ignoresSafeArea(', ui, name)
            self.assertNotIn('.presentationDetents(', ui, name)

    def test_long_titles_actions_and_brand_have_vertical_fallback(self):
        design = read('Nimbo/NimboNativeDesign.swift')
        notice = design.split('struct NimboNotice:', 1)[1].split('struct NimboActionStyle:', 1)[0]
        title = notice.split('Text(title).nimboFont(16, weight: .semibold)', 1)[1].split('if !detail.isEmpty', 1)[0]
        self.assertIn('.fixedSize(horizontal: false, vertical: true)', title)
        action = design.split('struct NimboActionStyle:', 1)[1].split('struct NimboBrand:', 1)[0]
        self.assertIn('.fixedSize(horizontal: false, vertical: true)', action)
        brand = design.split('struct NimboBrand:', 1)[1]
        self.assertIn('ViewThatFits(in: .horizontal)', brand)
        self.assertIn('VStack(alignment: .leading', brand)

    def test_new_swift_sources_and_portable_scenarios_parse(self):
        for name in ['Nimbo/NimboCameraLifecycle.swift', 'Nimbo/NimboCameraCapture.swift', 'Nimbo/NimboQrScannerView.swift', 'Nimbo/NimboNativeDesign.swift', 'Tests/CameraLifecycleTests.swift']:
            code = read(name)
            self.assertTrue(code, name)
            self.assertFalse(PARSER.parse(code.encode()).root_node.has_error, name)
        tests = read('Tests/CameraLifecycleTests.swift')
        for scenario in ['permissionAfterDismissal', 'backgroundResume', 'duplicateDelivery', 'dismantleIsTerminal', 'repeatedUpdate', 'cancelledBeforeQueueStart']:
            self.assertIn('static func ' + scenario + '()', tests)

if __name__ == '__main__':
    unittest.main()
