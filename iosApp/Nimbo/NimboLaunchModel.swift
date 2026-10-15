import Foundation

enum NimboLaunchPhase: String, Codable, Sendable {
    case nativeVisible = "NATIVE_VISIBLE"
    case waitingForContinue = "WAITING_FOR_CONTINUE"
    case stayingNative = "STAYING_NATIVE"
    case recovery = "PREVIOUS_LAUNCH_INCOMPLETE"
    case preparingRoot = "PREPARING_ROOT"
    case registeringFonts = "REGISTERING_FONTS"
    case retiringAppearanceDefaults = "RETIRING_APPEARANCE_DEFAULTS"
    case rootBoundaryArmed = "ROOT_KOTLIN_BOUNDARY_ARMED"
    case creatingRootOwner = "CREATING_ROOT_OWNER"
    case creatingComposeController = "CREATING_COMPOSE_CONTROLLER"
    case composeControllerReturned = "COMPOSE_CONTROLLER_RETURNED"
    case rootOwnerAppeared = "ROOT_OWNER_APPEARED"
    case migratingProfile = "MIGRATING_PROFILE"
    case migrationFailed = "MIGRATION_FAILED"
    case observingVPN = "OBSERVING_VPN"
    case vpnObservationFailed = "VPN_OBSERVATION_FAILED"
    case handingOffRoot = "HANDING_OFF_ROOT"
    case rootDisplayCycleObserved = "ROOT_DISPLAY_CYCLE_OBSERVED"
    case awaitingVisualConfirmation = "AWAITING_VISUAL_CONFIRMATION"
    // Normal mode: host lifecycle + bootstrap only, NOT Compose/GPU proof.
    case bootstrapComplete = "BOOTSTRAP_HOST_HANDOFF_COMPLETE"
    case userConfirmedInterface = "USER_CONFIRMED_INTERFACE_VISIBLE"
    case timedOut = "STARTUP_WAIT_TIMED_OUT"
    case journalUnavailable = "STARTUP_JOURNAL_UNAVAILABLE"
}

struct NimboLaunchMetadata: Sendable {
    let version: String
    let build: String
    let revision: String

    init(version: String, build: String, revision: String) {
        self.version = Self.safe(version)
        self.build = Self.safe(build)
        self.revision = Self.safe(revision)
    }

    static func diagnosticsEnabled(_ value: String?) -> Bool { value == "1" }

    private static func safe(_ value: String) -> String {
        let allowed = CharacterSet(charactersIn: "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789._+-")
        guard !value.isEmpty, value.utf8.count <= 80,
              value.unicodeScalars.allSatisfy({ allowed.contains($0) }) else { return "unknown" }
        return value
    }
}

struct NimboLaunchRecord: Codable, Equatable, Sendable {
    struct Event: Codable, Equatable, Sendable {
        let phase: NimboLaunchPhase
        let milliseconds: Int64
    }
    static let maximumEvents = 64
    let version: String
    let build: String
    let revision: String
    private(set) var events: [Event] = []

    init(metadata: NimboLaunchMetadata) {
        version = metadata.version; build = metadata.build; revision = metadata.revision
    }

    mutating func append(_ phase: NimboLaunchPhase, milliseconds: Int64) {
        events.append(Event(phase: phase, milliseconds: max(0, milliseconds)))
        if events.count > Self.maximumEvents { events.removeFirst(events.count - Self.maximumEvents) }
    }

    var isValid: Bool {
        let safe = NimboLaunchMetadata(version: version, build: build, revision: revision)
        return safe.version == version && safe.build == build && safe.revision == revision
            && events.count <= Self.maximumEvents && events.allSatisfy { $0.milliseconds >= 0 }
    }

    var isIncomplete: Bool {
        guard let last = events.last?.phase else { return false }
        switch last {
        case .nativeVisible, .waitingForContinue, .stayingNative, .bootstrapComplete, .userConfirmedInterface: return false
        default: return true
        }
    }
}

/// Foundation-only admission policy. A timed-out mounted root owns this process
/// until exit: never remount it while its original migration may still write.
struct NimboLaunchRootGate {
    let diagnostics: Bool
    private(set) var nativeAcknowledged = false
    private(set) var journalLoaded = false
    private(set) var requiresRecovery = false
    private(set) var staysNative = false
    private(set) var token: UUID?
    private(set) var entered = false
    private(set) var timedOut = false
    private(set) var failed = false
    private(set) var completed = false

    mutating func acknowledgeNative() { nativeAcknowledged = true }

    mutating func loaded(previous: NimboLaunchRecord?, unavailable: Bool) {
        guard !journalLoaded else { return }
        journalLoaded = true
        requiresRecovery = unavailable || previous?.isIncomplete == true
    }

    func canBegin(explicit: Bool) -> Bool {
        nativeAcknowledged && journalLoaded && token == nil
            && (explicit || (!diagnostics && !requiresRecovery && !staysNative))
    }

    mutating func begin(explicit: Bool) -> UUID? {
        guard canBegin(explicit: explicit) else { return nil }
        let next = UUID()
        token = next; entered = false; timedOut = false; failed = false; completed = false
        if explicit { staysNative = false }
        return next
    }

    func owns(_ attempt: UUID) -> Bool { token == attempt }
    func accepts(_ attempt: UUID) -> Bool { owns(attempt) && !timedOut && !failed && !completed }

    @discardableResult
    mutating func enter(_ attempt: UUID, factory: () -> Void) -> Bool {
        guard accepts(attempt), !entered else { return false }
        entered = true
        factory()
        return true
    }

    @discardableResult
    mutating func timeout(_ attempt: UUID) -> Bool {
        guard accepts(attempt) else { return false }
        timedOut = true
        return true
    }

    mutating func fail(_ attempt: UUID) {
        guard accepts(attempt) else { return }
        failed = true
    }

    mutating func preparationReturned(_ attempt: UUID) {
        guard owns(attempt), !entered, timedOut || failed else { return }
        token = nil // Only after the actual pre-root operation has returned.
    }

    @discardableResult
    mutating func complete(_ attempt: UUID, userConfirmed: Bool = false) -> Bool {
        guard accepts(attempt), entered, !diagnostics || userConfirmed else { return false }
        completed = true
        return true
    }

    mutating func stayNative() {
        staysNative = true
        if token != nil && !entered { failed = true }
    }
}
