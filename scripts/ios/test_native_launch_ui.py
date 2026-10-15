"""Exercise actual native launch and floating tab-bar sources on an iPhone simulator.

Root/font factories and presentation-only colors/brand/system fonts are test doubles.
This checks native gate/selection/heartbeat behavior, NOT full Nimbo/Compose/iOS27/device
acceptance or GPU readiness.
"""
from pathlib import Path
import argparse
import json
import platform
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
COMPONENTS = ['NimboLaunchModel.swift', 'NimboLaunchJournal.swift',
              'NimboLaunchCoordinator.swift', 'NimboLaunchAppearanceObserver.swift',
              'NimboLaunchView.swift', 'NimboTabBar.swift', 'NimboTab.swift']
HOST = r'''
import SwiftUI
// Explicit presentation-only doubles. No real design system or font resources.
enum NimboNative {
    static let canvas = Color(uiColor: .systemBackground)
    static let panel = Color(uiColor: .secondarySystemBackground)
    static let raised = Color(uiColor: .tertiarySystemBackground)
    static let ink = Color.primary
    static let secondary = Color.secondary
    static let border = Color.primary.opacity(0.15)
}
struct NimboBrand: View {
    var body: some View { Text("Nimbo — smoke brand double") }
}
extension View {
    func nimboFont(_ size: CGFloat = 17, relativeTo style: Font.TextStyle = .body,
                   weight: Font.Weight = .regular) -> some View {
        // Accept the production signature; system fonts only in this component host.
        self.font(.system(size: size, weight: weight))
    }
}
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
    @State private var selection: NimboTab = .home
    @State private var heartbeat = 0
    init(attempt: UUID, launch: NimboLaunchCoordinator) {
        self.attempt = attempt; self.launch = launch
        SmokeMeter.roots += 1
    }
    var body: some View {
        VStack(spacing: 12) {
            Text("Synthetic root mounted").accessibilityIdentifier("smoke.root")
            Text("Selected raw tab (synthetic content)")
            Text(selection.rawValue).accessibilityIdentifier("smoke.selection")
            Text("Main-actor heartbeat")
            Text("\(heartbeat)").accessibilityIdentifier("smoke.heartbeat")
            Text("Actual native tab bar; no Compose, VPN or profile services")
                .font(.footnote).multilineTextAlignment(.center)
        }
            .padding(24)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(NimboNative.canvas.ignoresSafeArea())
            .overlay(alignment: .bottom) {
                NimboTabBar(selection: $selection)
            }
            .task {
                // Independent of selection; only a resumed main-actor task advances it.
                while !Task.isCancelled {
                    do { try await Task.sleep(nanoseconds: 250_000_000) }
                    catch { return }
                    heartbeat += 1
                }
            }
            .task {
                guard launch.claimBootstrap(attempt) else { return }
                // Simulate coordinator handoff only; no Compose factory is linked/called.
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
            NimboLaunchView().dynamicTypeSize(.large).overlay(alignment: .topLeading) {
                TimelineView(.periodic(from: .now, by: 0.1)) { _ in
                    Text("root=\(SmokeMeter.roots);fonts=\(SmokeMeter.fonts)")
                        .font(.system(size: 10)).accessibilityIdentifier("smoke.counts")
                }.padding(8).allowsHitTesting(false)
            }
        }
    }
}
'''
TESTS = r'''
import XCTest
final class NativeLaunchUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
        XCUIDevice.shared.orientation = .portrait
    }
    func attach(_ app: XCUIApplication, _ name: String) {
        let image = XCTAttachment(screenshot: app.screenshot())
        image.name = name; image.lifetime = .keepAlways; add(image)
    }
    func heartbeatValue(_ element: XCUIElement) throws -> Int {
        try XCTUnwrap(Int(element.label), "Invalid heartbeat: \(element.label)")
    }
    func heartbeatAdvanced(after before: Int, element: XCUIElement) -> Bool {
        let predicate = NSPredicate { _, _ in
            guard let value = Int(element.label) else { return false }
            return value > before
        }
        let advanced = XCTNSPredicateExpectation(predicate: predicate, object: element)
        return XCTWaiter.wait(for: [advanced], timeout: 5) == .completed
    }
    func testActualNativeTabBarRepeatedSelectionKeepsHeartbeatAdvancing() throws {
        let app = XCUIApplication(); app.launch()
        defer { app.terminate() }
        let action = app.buttons["nimbo.launch.continue"]
        XCTAssertTrue(action.waitForExistence(timeout: 10))
        let enabled = XCTNSPredicateExpectation(
            predicate: NSPredicate(format: "enabled == true"), object: action)
        XCTAssertEqual(XCTWaiter.wait(for: [enabled], timeout: 10), .completed)
        XCTAssertEqual(app.staticTexts["smoke.counts"].label, "root=0;fonts=0")
        XCTAssertFalse(app.staticTexts["smoke.root"].exists)
        action.tap()
        let selected = app.staticTexts["smoke.selection"]
        let heartbeat = app.staticTexts["smoke.heartbeat"]
        XCTAssertTrue(selected.waitForExistence(timeout: 10))
        XCTAssertTrue(heartbeat.waitForExistence(timeout: 5))
        XCTAssertEqual(selected.label, "home")
        let tabs = ["home", "profiles", "stats", "settings"]
        for _ in 0..<3 {
            for rawValue in tabs {
                let button = app.buttons["nimbo.tab.\(rawValue)"]
                XCTAssertTrue(button.waitForExistence(timeout: 5))
                XCTAssertTrue(button.isHittable)
                button.tap()
                let changed = XCTNSPredicateExpectation(
                    predicate: NSPredicate(format: "label == %@", rawValue), object: selected)
                XCTAssertEqual(XCTWaiter.wait(for: [changed], timeout: 5), .completed)
                XCTAssertEqual(selected.label, rawValue)
                XCTAssertTrue(button.isSelected)
            }
            // Take the baseline after taps; an earlier tick cannot satisfy this check.
            let before = try heartbeatValue(heartbeat)
            XCTAssertTrue(heartbeatAdvanced(after: before, element: heartbeat))
            XCTAssertGreaterThan(try heartbeatValue(heartbeat), before)
        }
        attach(app, "Actual native floating tab bar after repeated selections — synthetic content")
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
                    'CFBundleVersion': '183', 'NimboDisplayVersion': '1.3.0-beta.2',
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
    devices = json.loads(subprocess.check_output(
        ['xcrun','simctl','list','devices','available','--json'], timeout=30))['devices']
    candidates = [dict(d, runtime=runtime) for runtime, rows in devices.items() if 'iOS' in runtime for d in rows
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
            '-destination-timeout','30','-parallel-testing-enabled','NO',
            '-test-timeouts-enabled','YES','-default-test-execution-time-allowance','120',
            '-maximum-test-execution-time-allowance','180',
            '-derivedDataPath',str(generated/'DerivedData'),'-resultBundlePath',str(args.output/'NativeLaunchSmoke.xcresult'),
            'CODE_SIGNING_ALLOWED=NO','test']
        with (args.output/'xcodebuild.log').open('w',encoding='utf-8') as log:
            subprocess.run(command,stdout=log,stderr=subprocess.STDOUT,check=True,timeout=600)
    (args.output/'scope.json').write_text(json.dumps(dict(actualComponents=COMPONENTS,
        rootAndFontFactories='synthetic test doubles; coordinator handoff acknowledgements simulated',
        tabBarAndModelSources='actual; presentation-only color/brand/system-font doubles',
        checks=['native zero-factory gate', 'explicit Continue', 'repeated actual tab selection',
                'independent main-actor heartbeat advancement', 'native diagnostic relaunch'],
        fullAppLaunchTested=False, composeGpuPresentationTested=False,
        physicalDeviceTested=False, iOS27AcceptanceTested=False,
        simulator=device['name'], runtime=device['runtime'], result='passed'),indent=2)+'\n')
    print('PASS: native gate/tab-selection/heartbeat simulator checks; NOT full Nimbo/Compose/iOS27/device acceptance')


if __name__ == '__main__':
    main()
