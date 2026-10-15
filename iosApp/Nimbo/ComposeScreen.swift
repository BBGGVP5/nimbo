import NimboShared
import SwiftUI
import UIKit

struct ComposeScreen: UIViewControllerRepresentable {
    let tab: NimboTab
    @Environment(\.nimboLaunchContext) private var launchContext

    func makeUIViewController(context _: Context) -> UIViewController {
        if let launchContext {
            launchContext.coordinator.markFactory(.creatingComposeController, attempt: launchContext.attempt)
        }
        // A returned controller is not a rendered frame. Kotlin fatal errors
        // cannot be recovered by wrapping this call in Swift do/catch.
        let controller = IosComposeControllerKt.NimboComposeViewController(screenName: tab.rawValue)
        if let launchContext {
            launchContext.coordinator.markFactory(.composeControllerReturned, attempt: launchContext.attempt)
        }
        return controller
    }

    func updateUIViewController(_: UIViewController, context _: Context) {}
}
