import Foundation

/// Optional system indexing must not block App.init or the first UI frame.
/// This actor owns a background executor and coalesces repeated root tasks.
actor NimboShortcutRegistration {
    static let shared = NimboShortcutRegistration()
    private var attempted = false

    func refreshIfAvailable(hasMetadata: Bool, update: @Sendable () -> Void) -> Bool {
        guard !attempted else { return false }
        attempted = true
        guard hasMetadata else { return false }
        update()
        return true
    }
}
