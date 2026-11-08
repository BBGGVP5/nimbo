import Foundation
import OSLog

struct NimboDiagnosticRecord: Codable, Sendable {
    let timestamp: String
    let level: NimboDiagnosticLevel
    let stage: NimboDiagnosticStage
    let code: String
    let message: String
    let metadata: [String: String]
    let process: String
}

actor NimboDiagnostics {
    static let shared = NimboDiagnostics()

    private let logger = Logger(
        subsystem: Bundle.main.bundleIdentifier ?? "com.danila.nimbo",
        category: "Diagnostics"
    )
    private let encoder: JSONEncoder = {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.sortedKeys, .withoutEscapingSlashes]
        return encoder
    }()
    private let formatter: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter
    }()
    private let maximumLogBytes = 2 * 1_024 * 1_024

    func record(
        _ level: NimboDiagnosticLevel,
        stage: NimboDiagnosticStage,
        code: String,
        message: String,
        metadata: [String: String] = [:]
    ) {
        let cleanCode = NimboRedactor.redact(code)
        let cleanMessage = NimboRedactor.redact(message)
        let cleanMetadata = metadata.mapValues { NimboRedactor.redact($0) }
        let record = NimboDiagnosticRecord(
            timestamp: formatter.string(from: Date()),
            level: level,
            stage: stage,
            code: cleanCode,
            message: cleanMessage,
            metadata: cleanMetadata,
            process: Bundle.main.bundleIdentifier ?? "unknown"
        )

        switch level {
        case .debug:
            logger.debug("[\(cleanCode, privacy: .public)] \(cleanMessage, privacy: .public)")
        case .info:
            logger.info("[\(cleanCode, privacy: .public)] \(cleanMessage, privacy: .public)")
        case .warning:
            logger.warning("[\(cleanCode, privacy: .public)] \(cleanMessage, privacy: .public)")
        case .error:
            logger.error("[\(cleanCode, privacy: .public)] \(cleanMessage, privacy: .public)")
        }

        do {
            try append(record)
        } catch {
            logger.error("Unable to persist diagnostic event: \(error.localizedDescription, privacy: .public)")
        }
    }

    func recentRecordsData(maxBytes: Int = 512 * 1_024) throws -> Data {
        let directory = NimboConstants.diagnosticContainer
        var result = Data()
        for name in [NimboConstants.diagnosticArchiveFile, NimboConstants.diagnosticFile] {
            let source = directory.appendingPathComponent(name)
            guard FileManager.default.fileExists(atPath: source.path) else { continue }
            result.append(Data("\n--- \(name) ---\n".utf8))
            result.append(try Data(contentsOf: source))
        }
        guard result.count > maxBytes else { return result }
        let suffix = result.suffix(maxBytes)
        if let firstLineBreak = suffix.firstIndex(of: 0x0A), firstLineBreak < suffix.endIndex {
            return Data(suffix[suffix.index(after: firstLineBreak)...])
        }
        return Data(suffix)
    }

    /// Actor-isolated disk IO and JSON projection; never queries or starts the tunnel.
    func prepareSupportReport() throws -> NimboPreparedSupportReport {
        try Task.checkCancellation()
        let directory = NimboConstants.diagnosticContainer
        var chunks: [Data] = []
        var remaining = NimboSupportReport.maximumInputBytes
        var omittedBytes = 0
        // Read current first so the bounded window always favors recent records.
        for name in [NimboConstants.diagnosticFile, NimboConstants.diagnosticArchiveFile] {
            let source = directory.appendingPathComponent(name)
            guard FileManager.default.fileExists(atPath: source.path) else { continue }
            let handle = try FileHandle(forReadingFrom: source)
            defer { try? handle.close() }
            let size = try handle.seekToEnd()
            let count = min(UInt64(remaining), size)
            try handle.seek(toOffset: size - count)
            var data = try handle.read(upToCount: Int(count)) ?? Data()
            remaining -= Int(count)
            // A bounded suffix can start mid-record. Discard rather than guess.
            if size > count {
                if let newline = data.firstIndex(of: 0x0A) {
                    data = Data(data[data.index(after: newline)...])
                } else {
                    data = Data()
                }
            }
            // A concurrent writer may leave a partial last record in this snapshot.
            if let newline = data.lastIndex(of: 0x0A) {
                data = Data(data[...newline])
            } else {
                data = Data()
            }
            omittedBytes += Int(size) - data.count
            chunks.insert(data, at: 0)
        }
        try Task.checkCancellation()
        let rendered = try NimboSupportReport.render(
            records: chunks.reduce(into: Data()) { $0.append($1) },
            appProcess: Bundle.main.bundleIdentifier ?? "",
            environment: NimboSupportReport.Environment(
                appVersion: NimboDiagnosticPlatformInfo.displayVersion,
                build: Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "unknown",
                sourceRevision: Bundle.main.object(forInfoDictionaryKey: "NimboBuildRevision") as? String ?? "unknown",
                os: NimboDiagnosticPlatformInfo.system,
                device: NimboDiagnosticPlatformInfo.device
            ),
            omittedInputBytes: omittedBytes
        )
        try Task.checkCancellation()
        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent("Nimbo-Support-\(UUID().uuidString).json")
        // This exact immutable string is both the preview and the entire shared file.
        try Data(rendered.preview.utf8).write(to: url, options: [.atomic, .completeFileProtection])
        return NimboPreparedSupportReport(url: url, preview: rendered.preview)
    }

    private func append(_ record: NimboDiagnosticRecord) throws {
        let directory = NimboConstants.diagnosticContainer
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let current = directory.appendingPathComponent(NimboConstants.diagnosticFile)
        let previous = directory.appendingPathComponent(NimboConstants.diagnosticArchiveFile)
        if let size = try? current.resourceValues(forKeys: [.fileSizeKey]).fileSize,
           size >= maximumLogBytes {
            try? FileManager.default.removeItem(at: previous)
            try FileManager.default.moveItem(at: current, to: previous)
        }
        var line = try encoder.encode(record)
        line.append(0x0A)
        if !FileManager.default.fileExists(atPath: current.path) {
            try line.write(to: current, options: [.atomic, .completeFileProtection])
            return
        }
        let handle = try FileHandle(forWritingTo: current)
        defer { try? handle.close() }
        try handle.seekToEnd()
        try handle.write(contentsOf: line)
    }
}

/// Extension-safe platform metadata. The packet-tunnel target cannot depend on
/// the main application's UIKit-only `NimboPlatformInfo`, but its exported
/// diagnostics still need to identify iOS and the real hardware model.
private enum NimboDiagnosticPlatformInfo {
    static let displayVersion: String =
        Bundle.main.object(forInfoDictionaryKey: "NimboDisplayVersion") as? String
        ?? Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String
        ?? "unknown"

    static let system: String = {
        let version = ProcessInfo.processInfo.operatingSystemVersion
        return "iOS \(version.majorVersion).\(version.minorVersion).\(version.patchVersion)"
    }()

    static let device: String = {
        if let simulatorModel = ProcessInfo.processInfo.environment["SIMULATOR_MODEL_IDENTIFIER"] {
            return simulatorModel
        }

        var systemInfo = utsname()
        uname(&systemInfo)
        let machine = withUnsafePointer(to: &systemInfo.machine) { pointer in
            pointer.withMemoryRebound(to: CChar.self, capacity: 1) {
                String(cString: $0)
            }
        }
        return machine.isEmpty ? "unknown" : machine
    }()

    static let userAgent: String = "Nimbo/\(displayVersion) (\(system); \(device))"
}

enum NimboRedactor {
    private static let rules: [(NSRegularExpression, String)] = [
        (regex("(?i)(https?://)[^/@\\s]+@"), "$1***@"),
        (regex("(?i)([?&](?:token|key|secret|password|uuid|id)=)[^&#\\s]+"), "$1***"),
        (regex("(?i)(/sub(?:scription)?/)[^/?#\\s]+"), "$1***"),
        (regex("(?i)(bearer\\s+)[a-z0-9._~+/=-]+"), "$1***"),
        (regex("(?i)\\b[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\\b"), "***-uuid"),
        (regex("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b"), "***.***.***.***")
    ]

    static func redact(_ value: String) -> String {
        rules.reduce(value) { current, rule in
            let range = NSRange(current.startIndex..., in: current)
            return rule.0.stringByReplacingMatches(in: current, range: range, withTemplate: rule.1)
        }
    }

    private static func regex(_ pattern: String) -> NSRegularExpression {
        // Patterns are constants covered by tests in the shared module.
        try! NSRegularExpression(pattern: pattern)
    }
}
