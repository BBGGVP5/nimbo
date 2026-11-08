import Foundation

@main
struct ShortcutRegistrationTests {
    static func main() async {
        let registration = NimboShortcutRegistration()
        let counter = Counter()
        let first = await registration.refreshIfAvailable(hasMetadata: true) { counter.increment() }
        let second = await registration.refreshIfAvailable(hasMetadata: true) { counter.increment() }
        precondition(first && !second && counter.value == 1, "Registration must run once")

        let missing = NimboShortcutRegistration()
        let skipped = await missing.refreshIfAvailable(hasMetadata: false) { counter.increment() }
        precondition(!skipped && counter.value == 1, "Absent metadata must not invoke App Intents")

        let concurrent = NimboShortcutRegistration()
        await withTaskGroup(of: Void.self) { group in
            for _ in 0..<20 {
                group.addTask {
                    _ = await concurrent.refreshIfAvailable(hasMetadata: true) { counter.increment() }
                }
            }
        }
        precondition(counter.value == 2, "Concurrent root tasks must not register repeatedly")
        print("Shortcut registration: once-only, absent-metadata and concurrent callers passed")
    }
}

private final class Counter: @unchecked Sendable {
    private let lock = NSLock()
    private var count = 0
    var value: Int { lock.lock(); defer { lock.unlock() }; return count }
    func increment() { lock.lock(); defer { lock.unlock() }; count += 1 }
}
