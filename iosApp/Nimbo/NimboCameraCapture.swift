import AVFoundation
import Foundation

/// AVFoundation's non-Sendable mutable objects never leave this owner for callers
/// to configure. All capture/delegate state is confined to queue. The sole UI
/// bridge attaches the immutable session reference to a preview on MainActor,
/// before the first start; UIKit/layer geometry is never touched by queue work.
/// This audited GCD ownership invariant is the reason for unchecked Sendable.
final class NimboCameraCapture: NSObject, AVCaptureMetadataOutputObjectsDelegate, @unchecked Sendable {
    enum Event: Sendable {
        case started
        case scanned(String)
        case failure(String)
    }

    private let session = AVCaptureSession()
    private let queue = DispatchQueue(label: "com.nimbo.qr-capture", qos: .userInitiated)
    private let deliver: @MainActor @Sendable (NimboCameraRequest, Event) -> Void
    // Queue-confined state, including metadata delivery admission.
    private var activeRequest: NimboCameraRequest?
    private var configured = false

    init(deliver: @escaping @MainActor @Sendable (NimboCameraRequest, Event) -> Void) {
        self.deliver = deliver
        super.init()
    }

    @MainActor func makePreviewLayer() -> AVCaptureVideoPreviewLayer {
        // Called once from viewDidLoad, before permission/start admission.
        AVCaptureVideoPreviewLayer(session: session)
    }

    func start(request: NimboCameraRequest) {
        queue.async { [self] in
            dispatchPrecondition(condition: .onQueue(queue))
            guard !request.isCancelled else { return }
            activeRequest = request
            guard configure(request: request) else { return }
            // Disappearance can occur while configuration is in progress.
            guard !request.isCancelled else { return }
            if !session.isRunning { session.startRunning() }
            if request.isCancelled {
                if session.isRunning { session.stopRunning() }
                activeRequest = nil
                return
            }
            guard session.isRunning else {
                emit(.failure("Не удалось запустить камеру. Повторите попытку или вставьте ссылку вручную."), for: request)
                return
            }
            emit(.started, for: request)
        }
    }

    func stop() {
        // Retain the owner until queued cleanup finishes, even after dismantle.
        queue.async { [self] in
            dispatchPrecondition(condition: .onQueue(queue))
            activeRequest = nil
            if session.isRunning { session.stopRunning() }
        }
    }

    private func configure(request: NimboCameraRequest) -> Bool {
        dispatchPrecondition(condition: .onQueue(queue))
        if configured { return true }
        guard let device = AVCaptureDevice.default(for: .video),
              let input = try? AVCaptureDeviceInput(device: device),
              session.canAddInput(input) else {
            emit(.failure("Камера недоступна. Вставьте ссылку вручную."), for: request)
            return false
        }
        session.beginConfiguration()
        defer { session.commitConfiguration() }
        session.addInput(input)
        let output = AVCaptureMetadataOutput()
        guard session.canAddOutput(output) else {
            session.removeInput(input)
            emit(.failure("Не удалось запустить распознавание QR."), for: request)
            return false
        }
        session.addOutput(output)
        guard output.availableMetadataObjectTypes.contains(.qr) else {
            session.removeOutput(output)
            session.removeInput(input)
            emit(.failure("Камера не поддерживает распознавание QR. Вставьте ссылку вручную."), for: request)
            return false
        }
        output.setMetadataObjectsDelegate(self, queue: queue)
        output.metadataObjectTypes = [.qr]
        configured = true
        return true
    }

    func metadataOutput(_ output: AVCaptureMetadataOutput, didOutput objects: [AVMetadataObject],
                        from connection: AVCaptureConnection) {
        dispatchPrecondition(condition: .onQueue(queue))
        guard let request = activeRequest else { return }
        guard !request.isCancelled else { return }
        guard let object = objects.first as? AVMetadataMachineReadableCodeObject,
              let value = object.stringValue, !value.isEmpty else { return }
        // Only a Sendable string crosses to UI, never metadata or connection.
        emit(.scanned(value), for: request)
    }

    private func emit(_ event: Event, for request: NimboCameraRequest) {
        dispatchPrecondition(condition: .onQueue(queue))
        let deliver = deliver
        Task { @MainActor in
            guard !request.isCancelled else { return }
            deliver(request, event)
        }
    }
}
