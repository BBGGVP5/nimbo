import Foundation

// swiftc -swift-version 5 -parse-as-library iosApp/Shared/NimboSupportReport.swift \
//   iosApp/Tests/SupportReportTests.swift -o support-report-tests && ./support-report-tests
@main struct SupportReportTests {
    static let appProcess = "com.example.app-private-process"
    static let environment = NimboSupportReport.Environment(
        appVersion: "1.3.0-beta.1", build: "180", sourceRevision: "0123456789abcdef",
        os: "iOS 26.0.1", device: "iPhone17,1"
    )
    static let instant = Date(timeIntervalSince1970: 1_760_486_400)

    static func record(_ fields: [String: Any] = [:]) throws -> Data {
        var object: [String: Any] = [
            "timestamp": "2026-10-15T12:00:00.000Z", "level": "info", "stage": "app",
            "process": appProcess, "code": "IOS_DIAGNOSTICS_EXPORT", "message": "private message",
            "metadata": ["private": "value"]
        ]
        object.merge(fields) { _, new in new }
        var data = try JSONSerialization.data(withJSONObject: object, options: [.sortedKeys])
        data.append(0x0A)
        return data
    }

    static func render(_ data: Data) throws -> NimboSupportReport.Rendered {
        try NimboSupportReport.render(records: data, appProcess: appProcess,
                                      environment: environment, generatedAt: instant)
    }

    static func json(_ report: NimboSupportReport.Rendered) throws -> [String: Any] {
        try JSONSerialization.jsonObject(with: Data(report.preview.utf8)) as! [String: Any]
    }

    static func privacyAndClosedSchema() throws {
        let secrets = ["arbitrary customer sentence", "https://secret.example/sub/token?password=pw",
                       "192.0.2.42", "2001:db8::42", "Bearer private-credential", "user@example.test",
                       "8d71eb00-8c61-4faa-8db1-bac1bd1df333", "sensitive-process-or-code", "пароль-секрет"]
        for secret in secrets {
            let result = try render(record(["message": secret, "code": secret,
                                            "metadata": [secret: secret], "newUnreviewedField": secret]))
            precondition(result.events.count == 1)
            precondition(!result.preview.contains(secret))
            precondition(!result.preview.contains(appProcess))
            let object = try json(result)
            precondition(Set(object.keys) == Set(["schemaVersion", "scope", "generatedAt", "environment", "counts", "omissions", "events"]))
            let event = (object["events"] as! [[String: Any]])[0]
            precondition(Set(event.keys) == Set(["timestamp", "level", "stage"]))
            precondition(object["scope"] as? String == "app_events_only")
        }
        // Even a familiar code is omitted: no heuristic "looks safe" exception.
        let familiarCode = try render(record())
        precondition(!familiarCode.preview.contains("IOS_DIAGNOSTICS_EXPORT"))
        // Unknown nested fields with unexpected types do not become exported strings.
        let nested = try render(record(["metadata": ["nested": ["token": [1, 2, 3]]], "message": ["private"]]))
        precondition(nested.events.count == 1)
    }

    static func malformedAndUnknownRecords() throws {
        var data = Data("{broken\n".utf8)
        let badFields: [[String: Any]] = [
            ["level": "private-level"], ["stage": "private-stage"],
            ["timestamp": "private-date"], ["timestamp": "2026-02-30T12:00:00.000Z"],
            ["timestamp": "2026-10-15T12:00:00.000Z\nsecret"],
            ["timestamp": "2026-10-15T12:00:00Zsecret"], ["level": NSNull()],
            ["process": NSNull()], ["message": String(repeating: "я", count: NimboSupportReport.maximumLineBytes)]
        ]
        for fields in badFields { data.append(try record(fields)) }
        data.append(try record(["process": "com.example.app-private-process.PacketTunnel"]))
        data.append(try record(["timestamp": "2026-10-15T12:01:00Z", "level": "warning", "stage": "dns"]))
        let result = try render(data)
        precondition(result.omissions.invalidRecords == badFields.count + 1)
        precondition(result.omissions.otherProcessRecords == 1)
        precondition(result.events.count == 1)
        precondition(result.events[0].timestamp == "2026-10-15T12:01:00.000Z")
        precondition(!result.preview.contains("private-"))
        let unknownProcess = try NimboSupportReport.render(records: record(), appProcess: "", environment: environment)
        precondition(unknownProcess.events.isEmpty)
    }

    static func latestEventsAndCounts() throws {
        var data = Data()
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        for index in 0..<250 {
            data.append(try record([
                "timestamp": formatter.string(from: instant.addingTimeInterval(Double(index))),
                "stage": index < 50 ? "app" : "tunnel_start",
                "level": index.isMultiple(of: 2) ? "info" : "error"
            ]))
        }
        let result = try render(data)
        precondition(result.events.count == 200)
        precondition(result.omissions.eventLimitRecords == 50)
        precondition(result.events.first!.timestamp == formatter.string(from: instant.addingTimeInterval(50)))
        precondition(result.events.last!.timestamp == formatter.string(from: instant.addingTimeInterval(249)))
        precondition(result.preview.utf8.count <= 64 * 1_024)
        let counts = try json(result)["counts"] as! [String: Any]
        precondition(counts["includedEvents"] as? Int == 200)
        precondition(counts["byLevel"] as? [String: Int] == ["info": 100, "error": 100])
        precondition(counts["byStage"] as? [String: Int] == ["tunnel_start": 200])
    }

    static func inputBoundsAndEmptyReport() throws {
        let empty = try render(Data())
        precondition(empty.events.isEmpty && empty.omissions.invalidRecords == 0)
        precondition(empty.preview.utf8.count <= NimboSupportReport.maximumJSONBytes)
        _ = try json(empty)
        let valid = try record()
        var data = Data(repeating: 0x78, count: NimboSupportReport.maximumInputBytes + 1)
        data.append(0x0A)
        let omittedCount = data.count
        data.append(valid)
        let bounded = try render(data)
        precondition(bounded.events.count == 1)
        precondition(bounded.omissions.inputBytes == omittedCount)
        precondition(bounded.omissions.invalidRecords == 0)
        let noNewline = try render(Data(repeating: 0x78, count: NimboSupportReport.maximumInputBytes + 1))
        precondition(noNewline.events.isEmpty)
        precondition(noNewline.omissions.inputBytes == NimboSupportReport.maximumInputBytes + 1)
        // At the byte boundary input is inspected, rather than silently discarded.
        let exact = try render(Data(repeating: 0x78, count: NimboSupportReport.maximumInputBytes))
        precondition(exact.omissions.inputBytes == 0 && exact.omissions.invalidRecords == 1)
        let prior = try NimboSupportReport.render(records: valid, appProcess: appProcess,
                                                 environment: environment, omittedInputBytes: 123)
        precondition(prior.omissions.inputBytes == 123)
    }

    static func accurateAndValidatedEnvironment() throws {
        let result = try render(record())
        let fields = try json(result)["environment"] as! [String: String]
        precondition(fields == ["appVersion": "1.3.0-beta.1", "build": "180", "sourceRevision": "0123456789abcdef",
                                "os": "iOS 26.0.1", "device": "iPhone17,1"])
        let fourPart = NimboSupportReport.Environment(appVersion: "1.2.3.4", build: "180.1",
                                                     sourceRevision: "local", os: "iOS 26.0.0", device: "arm64")
        precondition(fourPart.appVersion == "1.2.3.4") // Must not be mistaken for an IP address.
        for invalid in ["https://secret.example", "private text", "1.3.0\npassword", String(repeating: "1", count: 65)] {
            let env = NimboSupportReport.Environment(appVersion: invalid, build: invalid, sourceRevision: invalid,
                                                    os: invalid, device: invalid)
            precondition(env.appVersion == "unknown" && env.build == "unknown" && env.sourceRevision == "unknown")
            precondition(env.os == "unknown" && env.device == "unknown")
        }
    }

    static func deterministicImmutablePreview() throws {
        let data = try record()
        let first = try render(data)
        let second = try render(data)
        precondition(first.preview == second.preview)
        let snapshot = NimboPreparedSupportReport(url: URL(fileURLWithPath: "/support-snapshot.json"), preview: first.preview)
        _ = try render(record(["stage": "stop"]))
        precondition(snapshot.preview == first.preview)
        precondition(Data(snapshot.preview.utf8) == Data(first.preview.utf8))
    }

    static func main() throws {
        try privacyAndClosedSchema()
        try malformedAndUnknownRecords()
        try latestEventsAndCounts()
        try inputBoundsAndEmptyReport()
        try accurateAndValidatedEnvironment()
        try deterministicImmutablePreview()
        print("Support report projection: 6 groups PASS")
    }
}
