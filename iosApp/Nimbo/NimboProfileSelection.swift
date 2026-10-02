import Foundation

/// Admission precedes persistence; success is published only after awaited staging.
/// This prepares the next start and never starts/stops the current VPN session.
@MainActor
enum NimboProfileSelection {
    static func apply<Selection>(
        validate: () throws -> Void,
        persist: () throws -> Selection,
        stage: (Selection) async throws -> Void
    ) async throws -> Selection {
        try validate()
        let selected = try persist()
        try await stage(selected)
        return selected
    }
}
