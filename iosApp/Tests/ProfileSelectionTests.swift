import Foundation

@main
@MainActor
struct ProfileSelectionTests {
    enum Failure: Error { case admission, persist, stage, stop, restart }
    static func main() async throws {
        var steps: [String] = []
        do {
            let _: Int = try await NimboProfileSelection.apply(
                validate: { steps.append("validate"); throw Failure.admission },
                persist: { steps.append("persist"); return 7 },
                stage: { _ in steps.append("stage") })
            fatalError("Rejected admission must throw")
        } catch Failure.admission {}
        precondition(steps == ["validate"], "Rejected core must not change the stored selection")
        steps = []
        do {
            let _: Int = try await NimboProfileSelection.apply(
                validate: { steps.append("validate") },
                persist: { steps.append("persist"); throw Failure.persist },
                stage: { _ in steps.append("stage") })
            fatalError("Persistence failure must throw")
        } catch Failure.persist {}
        precondition(steps == ["validate", "persist"])
        steps = []
        do {
            let _: Int = try await NimboProfileSelection.apply(
                validate: { steps.append("validate") },
                persist: { steps.append("persist"); return 7 },
                stage: { _ in steps.append("stage"); throw Failure.stage })
            fatalError("A staging failure must not be reported as success")
        } catch Failure.stage {}
        precondition(steps == ["validate", "persist", "stage"])
        steps = []
        let selected = try await NimboProfileSelection.apply(
            validate: { steps.append("validate") },
            persist: { steps.append("persist"); return 7 },
            stage: { value in
                precondition(value == 7)
                await Task.yield()
                steps.append("stage")
            })
        steps.append("success")
        precondition(selected == 7 && steps == ["validate", "persist", "stage", "success"])
        steps = []
        do {
            let _: Int = try await NimboProfileSelection.apply(
                validate: { steps.append("validate"); throw Failure.admission },
                stop: { steps.append("stop") },
                persist: { steps.append("persist"); return 7 },
                stage: { _ in steps.append("stage") },
                restart: { steps.append("restart") })
            fatalError("Invalid new core must not stop active session")
        } catch Failure.admission {}
        precondition(steps == ["validate"])
        steps = []
        do {
            let _: Int = try await NimboProfileSelection.apply(
                validate: { steps.append("validate") },
                stop: { steps.append("stop"); throw Failure.stop },
                persist: { steps.append("persist"); return 7 },
                stage: { _ in steps.append("stage") },
                restart: { steps.append("restart") })
            fatalError("Failed stop must not persist or start")
        } catch Failure.stop {}
        precondition(steps == ["validate", "stop"])
        steps = []
        let _: Int = try await NimboProfileSelection.apply(
            validate: { steps.append("validate") },
            stop: { await Task.yield(); steps.append("stop") },
            persist: { steps.append("persist"); return 7 },
            stage: { _ in await Task.yield(); steps.append("stage") },
            restart: { await Task.yield(); steps.append("restart") })
        precondition(steps == ["validate", "stop", "persist", "stage", "restart"])
        steps = []
        do {
            let _: Int = try await NimboProfileSelection.apply(
                validate: { steps.append("validate") },
                stop: { steps.append("stop") },
                persist: { steps.append("persist"); return 7 },
                stage: { _ in steps.append("stage"); throw Failure.stage },
                restart: { steps.append("restart") })
            fatalError("Failed stage must not restart")
        } catch Failure.stage {}
        precondition(steps == ["validate", "stop", "persist", "stage"])
        print("Profile selection: 8 behavioral tests passed")
    }
}
