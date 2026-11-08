import Foundation

// These closed enums are also the recording schema. Never project arbitrary log strings.
enum NimboDiagnosticLevel: String, Codable, Sendable {
    case debug, info, warning, error
}

enum NimboDiagnosticStage: String, Codable, Sendable {
    case app, permission, config
    case coreLoad = "core_load"
    case tunnelStart = "tunnel_start"
    case route, dns, probe
    case networkChange = "network_change"
    case recovery, stop
}

struct NimboPreparedSupportReport: Sendable {
    let url: URL
    let preview: String
}

/// Foundation-only, closed-schema projection. No regex redaction of log text is involved.
enum NimboSupportReport {
    static let maximumEvents = 200
    static let maximumJSONBytes = 64 * 1_024
    static let maximumInputBytes = 256 * 1_024
    static let maximumLineBytes = 16 * 1_024

    struct Environment: Encodable, Sendable {
        let appVersion: String
        let build: String
        let sourceRevision: String
        let os: String
        let device: String

        // Inputs come only from bundle/platform metadata, never from diagnostic records.
        init(appVersion: String, build: String, sourceRevision: String, os: String, device: String) {
            self.appVersion = Self.checked(appVersion, pattern: #"[0-9]+(?:\.[0-9]+){1,3}(?:-[A-Za-z0-9.-]+)?(?:\+[A-Za-z0-9.-]+)?"#)
            self.build = Self.checked(build, pattern: #"[0-9]+(?:\.[0-9]+){0,2}"#)
            self.sourceRevision = Self.checked(sourceRevision, pattern: #"(?:[0-9a-fA-F]{7,64}|local)"#)
            self.os = Self.checked(os, pattern: #"iOS [0-9]+\.[0-9]+\.[0-9]+"#)
            self.device = Self.checked(device, pattern: #"(?:(?:iPhone|iPad|iPod)[0-9]+,[0-9]+|arm64|x86_64)"#)
        }

        private static func checked(_ value: String, pattern: String) -> String {
            guard value.utf8.count <= 64,
                  value.range(of: "\\A(?:" + pattern + ")\\z", options: .regularExpression) != nil else {
                return "unknown"
            }
            return value
        }
    }

    struct Event: Codable, Sendable {
        let timestamp: String
        let level: NimboDiagnosticLevel
        let stage: NimboDiagnosticStage
    }

    private struct InputEvent: Decodable {
        let timestamp: String
        let level: NimboDiagnosticLevel
        let stage: NimboDiagnosticStage
        // Used solely to exclude extension events from shared storage; never encoded.
        let process: String
    }

    struct Omissions: Encodable, Sendable {
        var inputBytes = 0
        var invalidRecords = 0
        var otherProcessRecords = 0
        var eventLimitRecords = 0
        var byteLimitRecords = 0
        let excludedFields = ["message", "metadata", "process", "code", "identifiers", "addresses", "URLs", "credentials"]
        let providerData = "not_collected"
        let history = "bounded_local_window_only"
    }

    struct Counts: Encodable, Sendable {
        let includedEvents: Int
        let byLevel: [String: Int]
        let byStage: [String: Int]
    }

    private struct Document: Encodable {
        let schemaVersion = 1
        let scope = "app_events_only"
        let generatedAt: String
        let environment: Environment
        let counts: Counts
        let omissions: Omissions
        let events: [Event]
    }

    struct Rendered: Sendable {
        let preview: String
        let events: [Event]
        let omissions: Omissions
    }

    /// Call on the diagnostics actor. Data is oldest to newest, with whole JSONL records.
    static func render(
        records: Data, appProcess: String, environment: Environment,
        omittedInputBytes: Int = 0, generatedAt: Date = Date()
    ) throws -> Rendered {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        let plainFormatter = ISO8601DateFormatter()
        plainFormatter.formatOptions = [.withInternetDateTime]
        let decoder = JSONDecoder()
        var omissions = Omissions()
        omissions.inputBytes = max(0, omittedInputBytes)
        var input = records
        // Defense in depth for future callers: do not decode unbounded data or partial lines.
        if input.count > maximumInputBytes {
            var tail = Data(input.suffix(maximumInputBytes))
            if let newline = tail.firstIndex(of: 0x0A) {
                tail = Data(tail[tail.index(after: newline)...])
            } else {
                tail = Data()
            }
            omissions.inputBytes += input.count - tail.count
            input = tail
        }
        var events: [Event] = []
        for line in input.split(separator: 0x0A, omittingEmptySubsequences: true) {
            guard line.count <= maximumLineBytes,
                  let event = try? decoder.decode(InputEvent.self, from: Data(line)),
                  event.timestamp.utf8.count <= 24,
                  event.timestamp.range(of: #"\A[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(?:\.[0-9]{3})?Z\z"#, options: .regularExpression) != nil,
                  let date = formatter.date(from: event.timestamp) ?? plainFormatter.date(from: event.timestamp),
                  (event.timestamp.count == 24 ? formatter.string(from: date) : plainFormatter.string(from: date)) == event.timestamp else {
                omissions.invalidRecords += 1
                continue
            }
            guard !appProcess.isEmpty, event.process == appProcess else {
                omissions.otherProcessRecords += 1
                continue
            }
            events.append(Event(timestamp: formatter.string(from: date), level: event.level, stage: event.stage))
            if events.count > maximumEvents {
                events.removeFirst()
                omissions.eventLimitRecords += 1
            }
        }
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys, .withoutEscapingSlashes]
        while true {
            let counts = Counts(
                includedEvents: events.count,
                byLevel: events.reduce(into: [:]) { $0[$1.level.rawValue, default: 0] += 1 },
                byStage: events.reduce(into: [:]) { $0[$1.stage.rawValue, default: 0] += 1 }
            )
            let document = Document(generatedAt: formatter.string(from: generatedAt), environment: environment,
                                    counts: counts, omissions: omissions, events: events)
            let bytes = try encoder.encode(document)
            if bytes.count <= maximumJSONBytes {
                return Rendered(preview: String(decoding: bytes, as: UTF8.self), events: events, omissions: omissions)
            }
            guard !events.isEmpty else { throw ReportError.sizeLimit }
            events.removeFirst()
            omissions.byteLimitRecords += 1
        }
    }

    enum ReportError: Error { case sizeLimit }
}
