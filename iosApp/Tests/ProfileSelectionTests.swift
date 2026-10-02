import Foundation

@main
@MainActor
struct ProfileSelectionTests {
    enum Failure: Error { case admission, persist, stage }
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
        print("Profile selection: 4 behavioral tests passed")
    }
}
