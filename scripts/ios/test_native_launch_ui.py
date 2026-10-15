"""Render the actual native launch components on a disposable simulator.

The root/font factories are explicit test doubles: this proves native first-screen
visibility and its lazy gate, NOT full Nimbo/Compose/device launch or GPU readiness.
"""
from pathlib import Path
import argparse
import json
import platform
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
COMPONENTS = ['NimboLaunchModel.swift', 'NimboLaunchJournal.swift',
              'NimboLaunchCoordinator.swift', 'NimboLaunchAppearanceObserver.swift', 'NimboLaunchView.swift']
HOST = r'''
import SwiftUI
@MainActor enum SmokeMeter {
    static var roots = 0
    static var fonts = 0
}
@MainActor enum NimboNativeFonts {
    static func register() { SmokeMeter.fonts += 1 }
}
@MainActor struct NimboRootOwnerView: View {
    let attempt: UUID
    @ObservedObject var launch: NimboLaunchCoordinator
    init(attempt: UUID, launch: NimboLaunchCoordinator) {
        self.attempt = attempt; self.launch = launch
        SmokeMeter.roots += 1
    }
    var body: some View {
        Text("Synthetic root mounted").accessibilityIdentifier("smoke.root")
            .task {
                guard launch.claimBootstrap(attempt) else { return }
                launch.markFactory(.creatingComposeController, attempt: attempt)
                launch.markFactory(.composeControllerReturned, attempt: attempt)
                launch.bootstrapFinished(attempt)
            }
            .background {
                if launch.exposeRoot {
                    NimboLaunchAppearanceObserver { launch.rootDisplayAcknowledged(attempt) }
                }
            }
    }
}
@main @MainActor struct NativeLaunchSmokeApp: App {
    var body: some Scene {
        WindowGroup {
            NimboLaunchView().overlay(alignment: .bottom) {
                TimelineView(.periodic(from: .now, by: 0.1)) { _ in
                    Text("root=\(SmokeMeter.roots);fonts=\(SmokeMeter.fonts)")
                        .font(.system(size: 10)).accessibilityIdentifier("smoke.counts")
                }.allowsHitTesting(false)
            }
        }
    }
}
'''
TESTS = r'''
import XCTest
final class NativeLaunchUITests: XCTestCase {
    override func setUpWithError() throws { continueAfterFailure = false }
    func attach(_ app: XCUIApplication, _ name: String) {
        let image = XCTAttachment(screenshot: app.screenshot())
        image.name = name; image.lifetime = .keepAlways; add(image)
    }
    func testFirstVisibleNativeFrameDoesNotConstructRootOrRegisterFonts() {
        let app = XCUIApplication(); app.launch()
        let action = app.buttons["nimbo.launch.continue"]
        XCTAssertTrue(action.waitForExistence(timeout: 10))
        let enabled = NSPredicate(format: "enabled == true")
        expectation(for: enabled, evaluatedWith: action)
        waitForExpectations(timeout: 10)
        XCTAssertEqual(app.staticTexts["smoke.counts"].label, "root=0;fonts=0")
        XCTAssertFalse(app.staticTexts["smoke.root"].exists)
        attach(app, "Native first frame before any root/font factory")
        app.buttons["nimbo.launch.stay"].tap()
        XCTAssertEqual(app.staticTexts["smoke.counts"].label, "root=0;fonts=0")
        action.tap()
        XCTAssertTrue(app.staticTexts["smoke.root"].waitForExistence(timeout: 10))
        XCTAssertNotEqual(app.staticTexts["smoke.counts"].label, "root=0;fonts=0")
        attach(app, "Explicit gate admitted only synthetic root")
        app.terminate()
    }
    func testDiagnosticRelaunchNeverAutoEntersRoot() {
        let app = XCUIApplication(); app.launch()
        XCTAssertTrue(app.buttons["nimbo.launch.continue"].waitForExistence(timeout: 10))
        app.terminate(); app.launch()
        XCTAssertTrue(app.buttons["nimbo.launch.continue"].waitForExistence(timeout: 10))
        XCTAssertEqual(app.staticTexts["smoke.counts"].label, "root=0;fonts=0")
        XCTAssertFalse(app.staticTexts["smoke.root"].exists)
        attach(app, "Relaunch remains in native diagnostic gate")
        app.terminate()
    }
}
'''


def project_spec(root, generated):
    return {
        'name': 'NativeLaunchSmoke', 'options': {'deploymentTarget': {'iOS': '16.0'}},
        'settings': {'base': {'SWIFT_VERSION': '5.0', 'CODE_SIGNING_ALLOWED': 'NO'}},
        'targets': {
            'NativeLaunchHost': {'type': 'application', 'platform': 'iOS',
                'sources': [str(root / 'iosApp/Nimbo' / n) for n in COMPONENTS] + [str(generated/'Host.swift')],
                'settings': {'base': {'PRODUCT_BUNDLE_IDENTIFIER': 'com.nimbo.private.launchsmoke'}},
                'info': {'path': str(generated/'Host-Info.plist'), 'properties': {
                    'CFBundleDisplayName': 'Native Launch Smoke', 'CFBundleShortVersionString': '1.3.0',
                    'CFBundleVersion': '182', 'NimboDisplayVersion': '1.3.0-beta.2',
                    'NimboBuildRevision': 'native-smoke', 'NimboLaunchDiagnostics': '1',
                    'UILaunchScreen': {}, 'UIApplicationSceneManifest': {'UIApplicationSupportsMultipleScenes': False}}}},
            'NativeLaunchUITests': {'type': 'bundle.ui-testing', 'platform': 'iOS',
                'sources': [str(generated/'UITests.swift')],
                'dependencies': [{'target': 'NativeLaunchHost'}],
                'settings': {'base': {'PRODUCT_BUNDLE_IDENTIFIER': 'com.nimbo.private.launchsmoke.tests',
                                      'GENERATE_INFOPLIST_FILE': 'YES', 'TEST_TARGET_NAME': 'NativeLaunchHost'}}}},
        'schemes': {'NativeLaunchSmoke': {'build': {'targets': {'NativeLaunchHost': 'all'}},
                                         'test': {'targets': ['NativeLaunchUITests']}}}}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, default=ROOT/'artifacts/ios-native-launch-ui')
    args = parser.parse_args()
    if platform.system() != 'Darwin':
        raise SystemExit('Simulator UI execution requires macOS/Xcode; no host emulation pass')
    args.output.mkdir(parents=True, exist_ok=True)
    devices = json.loads(subprocess.check_output(['xcrun','simctl','list','devices','available','--json']))['devices']
    candidates = [d for runtime, rows in devices.items() if 'iOS' in runtime for d in rows
                  if d.get('isAvailable') and d['name'].startswith('iPhone')]
    if not candidates:
        raise SystemExit('No available iPhone simulator')
    device = sorted(candidates, key=lambda d: (d.get('state') != 'Booted', d['name']))[0]
    with tempfile.TemporaryDirectory(prefix='nimbo-native-ui-') as temp:
        generated = Path(temp)
        (generated/'Host.swift').write_text(HOST, encoding='utf-8')
        (generated/'UITests.swift').write_text(TESTS, encoding='utf-8')
        (generated/'project.json').write_text(json.dumps(project_spec(ROOT,generated),indent=2)+'\n')
        subprocess.run(['xcodegen','generate','--spec',str(generated/'project.json'),'--project',str(generated)],check=True,timeout=60)
        command = ['xcodebuild','-project',str(generated/'NativeLaunchSmoke.xcodeproj'),
            '-scheme','NativeLaunchSmoke','-sdk','iphonesimulator','-destination','platform=iOS Simulator,id='+device['udid'],
            '-derivedDataPath',str(generated/'DerivedData'),'-resultBundlePath',str(args.output/'NativeLaunchSmoke.xcresult'),
            'CODE_SIGNING_ALLOWED=NO','test']
        with (args.output/'xcodebuild.log').open('w',encoding='utf-8') as log:
            subprocess.run(command,stdout=log,stderr=subprocess.STDOUT,check=True,timeout=600)
    (args.output/'scope.json').write_text(json.dumps(dict(actualComponents=COMPONENTS,
        rootAndFontFactories='synthetic test doubles', fullAppLaunchTested=False, composeGpuPresentationTested=False,
        physicalDeviceTested=False, simulator=device['name'], result='passed'),indent=2)+'\n')
    print('PASS: native component first-frame/lazy-gate simulator checks; NOT full Nimbo/Compose/iPhone acceptance')


if __name__ == '__main__':
    main()
