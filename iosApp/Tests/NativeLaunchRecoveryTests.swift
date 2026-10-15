import Foundation

@main
struct NativeLaunchRecoveryTests {
    static func main() async throws {
        var count = 0
        var diagnostic = NimboLaunchRootGate(diagnostics: true)
        precondition(diagnostic.begin(explicit: true) == nil)
        diagnostic.acknowledgeNative()
        precondition(diagnostic.begin(explicit: true) == nil)
        diagnostic.loaded(previous: nil, unavailable: false)
        precondition(diagnostic.begin(explicit: false) == nil && count == 0)
        let first = diagnostic.begin(explicit: true)!
        precondition(count == 0, "Admission must not construct root or its state")
        precondition(diagnostic.enter(first) { count += 1 })
        precondition(count == 1)
        precondition(!diagnostic.enter(first) { count += 1 })
        precondition(diagnostic.begin(explicit: true) == nil && count == 1)
        print("PASS diagnostic gate: zero factories until native acknowledgement, journal and Continue; one owner")

        precondition(diagnostic.timeout(first))
        precondition(!diagnostic.accepts(first))
        diagnostic.preparationReturned(first)
        precondition(diagnostic.begin(explicit: true) == nil, "Mounted root retains ownership after timeout")
        precondition(!diagnostic.complete(first))
        print("PASS mounted timeout: no retry overlap or late completion")

        var preparation = NimboLaunchRootGate(diagnostics: false)
        preparation.acknowledgeNative()
        preparation.loaded(previous: nil, unavailable: false)
        let old = preparation.begin(explicit: false)!
        precondition(preparation.timeout(old))
        precondition(preparation.begin(explicit: true) == nil)
        precondition(!preparation.enter(old) { count += 1 })
        preparation.preparationReturned(old)
        let current = preparation.begin(explicit: true)!
        precondition(current != old)
        precondition(!preparation.timeout(old) && !preparation.complete(old))
        precondition(!preparation.enter(old) { count += 1 })
        precondition(preparation.enter(current) { count += 1 })
        precondition(count == 2 && preparation.accepts(current))
        precondition(preparation.complete(current))
        precondition(!preparation.accepts(current), "Completed attempt must ignore later factory/navigation markers")
        print("PASS preparation timeout: retry only after real return; stale tokens cannot enter/finish new owner")

        var held = NimboLaunchRootGate(diagnostics: false)
        held.acknowledgeNative()
        held.loaded(previous: nil, unavailable: false)
        held.stayNative()
        precondition(held.begin(explicit: false) == nil)
        precondition(held.begin(explicit: true) != nil)
        precondition(!held.staysNative, "Explicit Continue overrides the earlier native-only choice")
        print("PASS native-only choice disables automatic entry")

        let metadata = NimboLaunchMetadata(version: "1.3.0-beta.2", build: "182", revision: "7695a0b3ef25")
        var interrupted = NimboLaunchRecord(metadata: metadata)
        interrupted.append(.preparingRoot, milliseconds: 10)
        interrupted.append(.creatingComposeController, milliseconds: 20)
        precondition(interrupted.isIncomplete)
        var recovered = NimboLaunchRootGate(diagnostics: false)
        recovered.acknowledgeNative()
        recovered.loaded(previous: interrupted, unavailable: false)
        precondition(recovered.requiresRecovery && recovered.begin(explicit: false) == nil)
        precondition(recovered.begin(explicit: true) != nil)
        interrupted.append(.awaitingVisualConfirmation, milliseconds: 25)
        precondition(interrupted.isIncomplete, "Native display cycles must not claim usable Compose")
        interrupted.append(.userConfirmedInterface, milliseconds: 30)
        precondition(!interrupted.isIncomplete)
        var completed = NimboLaunchRootGate(diagnostics: false)
        completed.acknowledgeNative()
        completed.loaded(previous: interrupted, unavailable: false)
        precondition(completed.begin(explicit: false) != nil)
        let neverEntered = NimboLaunchRecord(metadata: metadata)
        precondition(!neverEntered.isIncomplete)
        var unavailable = NimboLaunchRootGate(diagnostics: false)
        unavailable.acknowledgeNative()
        unavailable.loaded(previous: nil, unavailable: true)
        precondition(unavailable.begin(explicit: false) == nil)
        print("PASS interrupted/invalid journal requires explicit recovery; completed/native-only does not")

        var visual = NimboLaunchRootGate(diagnostics: true)
        visual.acknowledgeNative()
        visual.loaded(previous: nil, unavailable: false)
        let visibleAttempt = visual.begin(explicit: true)!
        precondition(visual.enter(visibleAttempt) { count += 1 })
        precondition(!visual.complete(visibleAttempt), "Native host callbacks cannot complete diagnostic recovery")
        precondition(visual.accepts(visibleAttempt))
        precondition(visual.complete(visibleAttempt, userConfirmed: true))
        print("PASS diagnostic completion requires explicit visual confirmation, not native display cycles")

        var bounded = NimboLaunchRecord(metadata: metadata)
        for n in 0..<200 { bounded.append(.creatingComposeController, milliseconds: Int64(n)) }
        precondition(bounded.events.count == NimboLaunchRecord.maximumEvents)
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: directory) } // Only this test-created directory.
        let journal = NimboLaunchJournal(directory: directory)
        if case .missing = await journal.load() {} else { preconditionFailure("Expected absent new journal") }
        let saved = await journal.save(bounded)
        precondition(saved)
        if case let .record(record) = await journal.load() {
            precondition(record == bounded && record.isIncomplete)
        } else { preconditionFailure("Expected journal round trip") }
        let encoded = try JSONEncoder().encode(bounded)
        let object = try JSONSerialization.jsonObject(with: encoded) as! [String: Any]
        precondition(Set(object.keys) == Set(["version", "build", "revision", "events"]))
        let events = object["events"] as! [[String: Any]]
        precondition(events.allSatisfy { Set($0.keys) == Set(["phase", "milliseconds"]) })
        print("PASS bounded journal round trip and export allowlist (identity plus enum/time only)")

        let file = directory.appendingPathComponent("launch.json")
        try Data("invalid".utf8).write(to: file)
        if case .unavailable = await journal.load() {} else { preconditionFailure("Corrupt record must be explicit") }
        try Data(repeating: 0, count: NimboLaunchJournal.maximumBytes + 1).write(to: file)
        if case .unavailable = await journal.load() {} else { preconditionFailure("Oversized record must be rejected") }
        let blocked = NimboLaunchJournal(directory: file)
        let failedSave = await blocked.save(bounded)
        precondition(!failedSave)
        print("PASS corrupt/oversized/write-failing journal returns safe enum/bool, no raw error")

        let bad = NimboLaunchMetadata(version: "https://secret.invalid/sub?token=x", build: "$(UNRESOLVED)", revision: "safe")
        precondition(bad.version == "unknown" && bad.build == "unknown")
        precondition(NimboLaunchMetadata.diagnosticsEnabled("1"))
        for value in ["0", "true", "YES", "$(NIMBO_LAUNCH_DIAGNOSTICS)", " 1", ""] {
            precondition(!NimboLaunchMetadata.diagnosticsEnabled(value))
        }
        print("PASS strict build flag and safe Bundle metadata")
        print("Native launch recovery Foundation tests passed (9 scenario groups)")
    }
}
