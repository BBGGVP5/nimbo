import Foundation

/// A new diagnostic-only file. No dependency on application configuration storage.
final class NimboLaunchJournal: @unchecked Sendable {
    enum LoadResult: Sendable {
        case missing
        case record(NimboLaunchRecord)
        case unavailable
    }
    static let maximumBytes = 32 * 1_024
    private let directory: URL
    private let queue = DispatchQueue(label: "com.nimbo.launch-diagnostics", qos: .utility)

    init(directory: URL) { self.directory = directory }

    // Called only after native appearance/display acknowledgement.
    static func applicationSupport() -> NimboLaunchJournal? {
        guard let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first else { return nil }
        return NimboLaunchJournal(directory: base.appendingPathComponent("NimboLaunchDiagnostics", isDirectory: true))
    }

    func load() async -> LoadResult {
        await withCheckedContinuation { continuation in
            queue.async {
                let file = self.directory.appendingPathComponent("launch.json")
                guard FileManager.default.fileExists(atPath: file.path) else {
                    continuation.resume(returning: .missing); return
                }
                do {
                    let handle = try FileHandle(forReadingFrom: file)
                    defer { try? handle.close() }
                    let data = try handle.read(upToCount: Self.maximumBytes + 1) ?? Data()
                    guard data.count <= Self.maximumBytes else {
                        continuation.resume(returning: .unavailable); return
                    }
                    let record = try JSONDecoder().decode(NimboLaunchRecord.self, from: data)
                    continuation.resume(returning: record.isValid ? .record(record) : .unavailable)
                } catch { continuation.resume(returning: .unavailable) }
            }
        }
    }

    func save(_ record: NimboLaunchRecord) async -> Bool {
        await withCheckedContinuation { continuation in
            enqueue(record) { continuation.resume(returning: $0) }
        }
    }

    /// Serial nonblocking later breadcrumbs. Only the pre-root boundary is
    /// explicitly acknowledged; a fatal termination can lose a later queued mark.
    func enqueue(_ record: NimboLaunchRecord, completion: @escaping @Sendable (Bool) -> Void = { _ in }) {
        queue.async {
            do {
                let data = try JSONEncoder().encode(record)
                guard record.isValid, data.count <= Self.maximumBytes else { completion(false); return }
                try FileManager.default.createDirectory(at: self.directory, withIntermediateDirectories: true)
                try data.write(to: self.directory.appendingPathComponent("launch.json"), options: .atomic)
                completion(true)
            } catch { completion(false) }
        }
    }
}
