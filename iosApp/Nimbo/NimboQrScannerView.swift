import AVFoundation
import SwiftUI
import UIKit

struct NimboQrScannerView: View {
    enum Purpose: Equatable { case subscription, sync }
    var purpose: Purpose = .subscription
    let onScan: (String) -> Void
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.verticalSizeClass) private var verticalSizeClass
    @State private var cameraError: String?
    @State private var cameraGeneration = 0

    var body: some View {
        NavigationStack {
            NimboPage {
                NimboNotice(title: "Наведите камеру на QR", detail: purpose == .sync
                            ? "Сканируйте код синхронизации со второго устройства."
                            : "Сканируйте ссылку подписки или конфигурацию сервера.", symbol: "qrcode.viewfinder")
                NimboCameraPreview(purpose: purpose, isSceneActive: scenePhase == .active,
                                   onScan: onScan, onError: { cameraError = $0 })
                    .id(cameraGeneration)
                    .frame(height: verticalSizeClass == .compact ? 160 : 280)
                    .clipShape(RoundedRectangle(cornerRadius: 18))
                    .overlay(RoundedRectangle(cornerRadius: 18).strokeBorder(NimboNative.border, lineWidth: 1))
                    .accessibilityLabel("Камера для сканирования QR")
                if let cameraError {
                    NimboNotice(title: "Проверьте камеру или код", detail: cameraError,
                                symbol: "exclamationmark.circle", tint: NimboNative.error).nimboCard()
                    Button("Повторить") { cameraError = nil; cameraGeneration += 1 }
                        .buttonStyle(NimboActionStyle())
                    Button("Настройки камеры") {
                        if let url = URL(string: UIApplication.openSettingsURLString) { openURL(url) }
                    }.buttonStyle(NimboActionStyle())
                }
                Text("Можно закрыть камеру и вставить ссылку вручную.")
                    .nimboFont(14, relativeTo: .subheadline).foregroundStyle(NimboNative.secondary)
            }
            .nimboSheetStyle()
            .navigationTitle("Сканировать QR")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Закрыть") { dismiss() }.frame(minWidth: 44, minHeight: 44)
                }
            }
        }
    }
}

@MainActor
private struct NimboCameraPreview: UIViewControllerRepresentable {
    let purpose: NimboQrScannerView.Purpose
    let isSceneActive: Bool
    let onScan: (String) -> Void
    let onError: (String?) -> Void

    func makeUIViewController(context: Context) -> NimboCameraController {
        let controller = NimboCameraController(purpose: purpose, onScan: onScan, onError: onError)
        controller.setSceneActive(isSceneActive)
        return controller
    }
    func updateUIViewController(_ controller: NimboCameraController, context: Context) {
        controller.setSceneActive(isSceneActive)
    }
    static func dismantleUIViewController(_ controller: NimboCameraController, coordinator: ()) {
        controller.dismantle()
    }
}

@MainActor
private final class NimboCameraController: UIViewController {
    private let purpose: NimboQrScannerView.Purpose
    private let onScan: (String) -> Void
    private let onError: (String?) -> Void
    private var lifecycle = NimboCameraLifecycle()
    private var preview: AVCaptureVideoPreviewLayer?
    private lazy var capture = NimboCameraCapture { [weak self] request, event in
        self?.receive(event, for: request)
    }

    init(purpose: NimboQrScannerView.Purpose, onScan: @escaping (String) -> Void,
         onError: @escaping (String?) -> Void) {
        self.purpose = purpose
        self.onScan = onScan
        self.onError = onError
        super.init(nibName: nil, bundle: nil)
    }
    required init?(coder: NSCoder) { fatalError("Use init(purpose:onScan:onError:)") }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black
        let preview = capture.makePreviewLayer()
        preview.videoGravity = .resizeAspectFill
        view.layer.addSublayer(preview)
        self.preview = preview
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        preview?.frame = view.bounds
        if let orientation = view.window?.windowScene?.interfaceOrientation,
           let connection = preview?.connection, connection.isVideoOrientationSupported {
            switch orientation {
            case .landscapeLeft: connection.videoOrientation = .landscapeLeft
            case .landscapeRight: connection.videoOrientation = .landscapeRight
            case .portraitUpsideDown: connection.videoOrientation = .portraitUpsideDown
            default: connection.videoOrientation = .portrait
            }
        }
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        updateCapture(visible: true)
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        // Invalidate immediately, not after the dismissal animation finishes.
        updateCapture(visible: false)
    }

    func setSceneActive(_ active: Bool) {
        updateCapture(sceneActive: active)
    }

    func dismantle() {
        // Terminal even if UIKit later sends another appearance callback.
        lifecycle.finish()
        capture.stop()
    }

    private func updateCapture(visible: Bool? = nil, sceneActive: Bool? = nil) {
        let hadRequest = lifecycle.request != nil
        if let request = lifecycle.update(visible: visible, sceneActive: sceneActive) {
            requestPermission(for: request)
        } else if hadRequest && lifecycle.request == nil {
            // No queue work before viewDidLoad attaches the preview, nor for
            // repeated inactive SwiftUI updates. Cancellation precedes stop.
            capture.stop()
        }
    }

    private func requestPermission(for request: NimboCameraRequest) {
        guard lifecycle.accepts(request) else { return }
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            capture.start(request: request)
        case .notDetermined:
            // iOS owns its permission alert. Its response cannot be cancelled,
            // but a suspended/dismantled request must never start the camera.
            AVCaptureDevice.requestAccess(for: .video) { [weak self] granted in
                Task { @MainActor in
                    guard let self, self.lifecycle.accepts(request) else { return }
                    if granted { self.capture.start(request: request) }
                    else { self.reportPermissionError() }
                }
            }
        default: reportPermissionError()
        }
    }

    private func reportPermissionError() {
        onError("Разрешите Nimbo доступ к камере в настройках iOS или вставьте ссылку вручную.")
    }

    private func receive(_ event: NimboCameraCapture.Event, for request: NimboCameraRequest) {
        guard lifecycle.accepts(request) else { return }
        switch event {
        case .started:
            onError(nil)
            view.setNeedsLayout()
        case let .failure(message):
            onError(message)
        case let .scanned(value):
            if purpose == .sync && !value.hasPrefix("nimbo-sync://") {
                onError("Это не код синхронизации Nimbo. Откройте синхронизацию на втором устройстве.")
                return
            }
            lifecycle.finish()
            capture.stop()
            // Subscription payload validation belongs to the existing importer.
            onScan(value)
        }
    }
}
