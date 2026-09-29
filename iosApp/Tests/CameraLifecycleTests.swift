// Portable logic tests; NOT Apple camera/UI tests.
// swiftc iosApp/Nimbo/NimboCameraLifecycle.swift iosApp/Tests/CameraLifecycleTests.swift -o camera-lifecycle-tests
// ./camera-lifecycle-tests
@main
enum CameraLifecycleTests {
    static func main() {
        permissionAfterDismissal()
        backgroundResume()
        duplicateDelivery()
        dismantleIsTerminal()
        repeatedUpdate()
        cancelledBeforeQueueStart()
        print("PASS: 6 camera lifecycle scenarios")
    }

    static func permissionAfterDismissal() {
        var policy = NimboCameraLifecycle()
        let pending = policy.update(visible: true, sceneActive: true)!
        _ = policy.update(visible: false)
        precondition(pending.isCancelled)
        precondition(!policy.accepts(pending))
        let reopened = policy.update(visible: true)!
        precondition(reopened !== pending)
        precondition(policy.accepts(reopened))
        precondition(!policy.accepts(pending))
    }

    static func backgroundResume() {
        var policy = NimboCameraLifecycle()
        let old = policy.update(visible: true, sceneActive: true)!
        _ = policy.update(sceneActive: false)
        precondition(!policy.accepts(old))
        let resumed = policy.update(sceneActive: true)!
        precondition(policy.accepts(resumed))
        precondition(!policy.accepts(old))
    }

    static func duplicateDelivery() {
        var policy = NimboCameraLifecycle()
        let frame = policy.update(visible: true, sceneActive: true)!
        precondition(policy.accepts(frame))
        policy.finish() // Called before onScan; later frames/permission cannot win.
        precondition(!policy.accepts(frame))
        precondition(frame.isCancelled)
    }

    static func dismantleIsTerminal() {
        var policy = NimboCameraLifecycle()
        policy.finish()
        precondition(policy.update(visible: true, sceneActive: true) == nil)
        policy.finish()
        precondition(policy.request == nil)
    }

    static func repeatedUpdate() {
        var policy = NimboCameraLifecycle()
        precondition(policy.update(sceneActive: true) == nil)
        let current = policy.update(visible: true)!
        precondition(policy.update(visible: true, sceneActive: true) == nil)
        precondition(policy.request === current)
        precondition(policy.accepts(current))
    }

    static func cancelledBeforeQueueStart() {
        let request = NimboCameraRequest()
        precondition(!request.isCancelled)
        request.cancel()
        request.cancel()
        precondition(request.isCancelled)
    }
}
