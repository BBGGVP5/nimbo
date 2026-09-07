import NimboShared
import SwiftUI
import UIKit

struct ComposeScreen: UIViewControllerRepresentable {
    let tab: NimboTab

    func makeUIViewController(context _: Context) -> UIViewController {
        IosComposeControllerKt.NimboComposeViewController(screenName: tab.rawValue)
    }

    func updateUIViewController(_: UIViewController, context _: Context) {}
}
