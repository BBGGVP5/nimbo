import Foundation

// Shared by the native profile rows and the root action dispatcher.
// Keep this internal: a private extension in RootView is invisible to rows.
extension Notification.Name {
    static let nimboPingServer = Notification.Name("com.nimbo.action.ping-server")
}
