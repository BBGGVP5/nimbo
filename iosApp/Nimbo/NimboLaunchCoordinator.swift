import Foundation
import SwiftUI

@MainActor
final class NimboLaunchCoordinator: ObservableObject {
    @Published private(set) var gate: NimboLaunchRootGate
    @Published private(set) var phase: NimboLaunchPhase = .nativeVisible
    @Published private(set) var rootToken: UUID?
    @Published private(set) var exposeRoot = false
    @Published private(set) var persistenceFailed = false
    private(set) var record: NimboLaunchRecord
    private var previous: NimboLaunchRecord?
    private var journal: NimboLaunchJournal?
    private var journalActive = false
    private var composeReturned = false
    private var bootstrapClaimed = false
    private var bootstrapCompleted = false
    private var rootDisplayObserved = false
    private var deadline: Task<Void, Never>?
    private var loadDeadline: Task<Void, Never>?
    private var loadExceededDeadline = false
    private let startedAt = ProcessInfo.processInfo.systemUptime

    init(bundle: Bundle = .main) {
        let identity = NimboLaunchMetadata(
            version: bundle.object(forInfoDictionaryKey: "NimboDisplayVersion") as? String ?? "unknown",
            build: bundle.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "unknown",
            revision: bundle.object(forInfoDictionaryKey: "NimboBuildRevision") as? String ?? "unknown")
        record = NimboLaunchRecord(metadata: identity)
        gate = NimboLaunchRootGate(diagnostics: NimboLaunchMetadata.diagnosticsEnabled(
            bundle.object(forInfoDictionaryKey: "NimboLaunchDiagnostics") as? String))
        // Memory/Bundle identity only: no file access, application services or root factory.
    }

    func nativeSurfaceAcknowledged() {
        guard !gate.nativeAcknowledged else { return }
        gate.acknowledgeNative()
        append(.nativeVisible)
        journal = NimboLaunchJournal.applicationSupport()
        loadDeadline = Task { @MainActor in
            do { try await Task.sleep(nanoseconds: 15_000_000_000) }
            catch { return }
            guard !gate.journalLoaded else { return }
            loadExceededDeadline = true
            persistenceFailed = true
            append(.journalUnavailable)
            // The file operation still owns its queue; this reports waiting,
            // cannot cancel a filesystem hang or safely bypass that operation.
        }
        Task { @MainActor in
            let loaded = await journal?.load() ?? .unavailable
            loadDeadline?.cancel()
            var unavailable = loadExceededDeadline
            switch loaded {
            case let .record(value): previous = value
            case .unavailable: unavailable = true; persistenceFailed = true
            case .missing: break
            }
            gate.loaded(previous: previous, unavailable: unavailable)
            if gate.requiresRecovery { append(unavailable ? .journalUnavailable : .recovery) }
            else { append(.waitingForContinue) }
            begin(explicit: false)
        }
    }

    func continueLaunch() { begin(explicit: true) }

    private func begin(explicit: Bool) {
        guard let attempt = gate.begin(explicit: explicit) else { return }
        composeReturned = false; bootstrapClaimed = false; bootstrapCompleted = false; rootDisplayObserved = false
        append(.preparingRoot)
        armDeadline(attempt)
        Task { @MainActor in
            guard let journal else {
                preparationFailed(attempt)
                return
            }
            let saved = await journal.save(record)
            guard gate.accepts(attempt) else {
                gate.preparationReturned(attempt)
                return
            }
            guard saved else { preparationFailed(attempt); return }
            journalActive = true
            persistenceFailed = false
            mark(.registeringFonts, attempt: attempt)
            NimboNativeFonts.register()
            mark(.retiringAppearanceDefaults, attempt: attempt)
            // Existing migration, now strictly after native gate and durable boundary.
            for key in ["elementStyle", "backgroundStyle", "backgroundPalette",
                        "backgroundMotion", "statusParticles", "brightness", "transparency", "corners", "refraction"] {
                UserDefaults.standard.removeObject(forKey: "com.nimbo.appearance." + key)
            }
            // RootView has Kotlin entry points before makeUIViewController too.
            // Acknowledge this boundary off-main BEFORE realizing the entire
            // root child; the synchronous factory hook must never wait for I/O.
            mark(.rootBoundaryArmed, attempt: attempt)
            let armed = await journal.save(record)
            guard gate.accepts(attempt) else {
                gate.preparationReturned(attempt)
                return
            }
            guard armed else { preparationFailed(attempt); return }
            mark(.creatingRootOwner, attempt: attempt)
            gate.enter(attempt) { rootToken = attempt }
        }
    }

    private func preparationFailed(_ attempt: UUID) {
        guard gate.accepts(attempt) else { return }
        gate.fail(attempt)
        persistenceFailed = true
        append(.journalUnavailable)
        gate.preparationReturned(attempt)
        deadline?.cancel()
    }

    private func armDeadline(_ attempt: UUID) {
        deadline?.cancel()
        deadline = Task { @MainActor in
            do { try await Task.sleep(nanoseconds: 15_000_000_000) }
            catch { return }
            guard gate.timeout(attempt) else { return }
            append(.timedOut)
            exposeRoot = false
            // This timer cannot run/repaint while MainActor is blocked. An
            // incomplete acknowledged breadcrumb enables NEXT-launch recovery.
        }
    }

    func mark(_ next: NimboLaunchPhase, attempt: UUID) {
        guard gate.accepts(attempt) else { return }
        append(next)
        if next == .composeControllerReturned {
            composeReturned = true
            exposeIfPrepared(attempt)
        }
    }

    // Representable construction may be inside a SwiftUI update. Capture the
    // honest boundary now; defer ObservableObject publication until that returns.
    func markFactory(_ next: NimboLaunchPhase, attempt: UUID) {
        guard gate.accepts(attempt),
              next == .creatingComposeController || next == .composeControllerReturned else { return }
        append(next, publish: false)
        Task { @MainActor in
            guard gate.accepts(attempt) else { return }
            phase = record.events.last?.phase ?? next
            if next == .composeControllerReturned {
                composeReturned = true
                exposeIfPrepared(attempt)
            }
        }
    }

    func claimBootstrap(_ attempt: UUID) -> Bool {
        guard gate.owns(attempt), !bootstrapClaimed else { return false }
        bootstrapClaimed = true
        return true
    }

    func bootstrapFailed(_ next: NimboLaunchPhase, attempt: UUID) {
        guard gate.accepts(attempt) else { return }
        gate.fail(attempt)
        append(next)
        exposeRoot = false
        deadline?.cancel()
    }

    func bootstrapFinished(_ attempt: UUID) {
        guard gate.accepts(attempt) else { return }
        bootstrapCompleted = true
        exposeIfPrepared(attempt)
    }

    private func exposeIfPrepared(_ attempt: UUID) {
        guard gate.accepts(attempt), bootstrapCompleted, composeReturned, !gate.staysNative else { return }
        append(.handingOffRoot)
        exposeRoot = true
    }

    func rootDisplayAcknowledged(_ attempt: UUID) {
        guard exposeRoot, gate.accepts(attempt), bootstrapCompleted else { return }
        mark(.rootDisplayCycleObserved, attempt: attempt)
        rootDisplayObserved = true
        deadline?.cancel()
        if gate.diagnostics {
            // Native cycles cannot distinguish a drawn home from black Compose.
            // Keep this attempt incomplete until the user explicitly confirms.
            append(.awaitingVisualConfirmation)
        } else if gate.complete(attempt) {
            append(.bootstrapComplete)
        }
    }

    var canConfirmVisibleInterface: Bool {
        guard let attempt = rootToken else { return false }
        return gate.diagnostics && exposeRoot && rootDisplayObserved && bootstrapCompleted && gate.accepts(attempt)
    }

    func confirmVisibleInterface() {
        guard canConfirmVisibleInterface, let attempt = rootToken,
              gate.complete(attempt, userConfirmed: true) else { return }
        append(.userConfirmedInterface)
    }

    func stayNative() {
        gate.stayNative()
        exposeRoot = false
        // Do not turn a mounted/incomplete attempt into a successful journal.
        if rootToken == nil { append(.stayingNative) }
    }

    func showExistingRoot() {
        guard rootToken != nil, composeReturned else { return }
        exposeRoot = true // Same owner; never retries its migration or clears its failure.
    }

    var canShowExistingRoot: Bool { rootToken != nil && composeReturned }
    var hasFailure: Bool { gate.timedOut || gate.failed || gate.requiresRecovery || persistenceFailed }

    private func append(_ next: NimboLaunchPhase, publish: Bool = true) {
        if publish { phase = next }
        let elapsed = Int64(max(0, ProcessInfo.processInfo.systemUptime - startedAt) * 1_000)
        record.append(next, milliseconds: elapsed)
        if journalActive {
            journal?.enqueue(record) { [weak self] saved in
                if !saved {
                    Task { @MainActor in self?.persistenceFailed = true }
                }
            }
        }
    }

    var exportText: String {
        // Only typed identity + enum/time records; never read existing app logs/data.
        let records = previous.map { [$0, record] } ?? [record]
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        return (try? encoder.encode(records)).flatMap { String(data: $0, encoding: .utf8) } ?? "[]"
    }
}

struct NimboLaunchContext {
    let attempt: UUID
    let coordinator: NimboLaunchCoordinator
}

private struct NimboLaunchContextKey: EnvironmentKey {
    static let defaultValue: NimboLaunchContext? = nil
}

extension EnvironmentValues {
    var nimboLaunchContext: NimboLaunchContext? {
        get { self[NimboLaunchContextKey.self] }
        set { self[NimboLaunchContextKey.self] = newValue }
    }
}
