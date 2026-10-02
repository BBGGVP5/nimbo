import Foundation

/// Admission precedes persistence; success is published only after awaited staging.
/// Optional stop/restart callbacks transfer an active session only after admission.
@MainActor
enum NimboProfileSelection {
    static func apply<Selection>(
        validate: () throws -> Void,
        stop: () async throws -> Void = {},
        persist: () throws -> Selection,
        stage: (Selection) async throws -> Void,
        restart: () async throws -> Void = {}
    ) async throws -> Selection {
        try validate()
        try await stop()
        let selected = try persist()
        try await stage(selected)
        try await restart()
        return selected
    }
}
