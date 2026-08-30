import AVFoundation
import SwiftUI
import UIKit

/// Экран синхронизации: сканируем QR со второго устройства или вставляем
/// ссылку вручную. Сопряжение всегда начинает iOS — так не нужен ни поиск
/// устройств в сети, ни объявление своего сервиса.
struct NimboSyncView: View {
    @StateObject private var engine = NimboSyncEngine()
    @Environment(\.dismiss) private var dismiss
    @State private var manualLink = ""
    @State private var showScanner = false

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    header
                    stageCard
                    if case .chooseDirection = engine.stage { directionButtons }
                    manualEntry
                    hint
                }
                .padding(16)
            }
            .background(Color(red: 0x09 / 255, green: 0x13 / 255, blue: 0x21 / 255).ignoresSafeArea())
            .navigationTitle("Синхронизация")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Готово") { dismiss() }
                }
            }
            .sheet(isPresented: $showScanner) {
                NimboQrScannerView { scanned in
                    showScanner = false
                    engine.start(link: scanned)
                }
            }
        }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Перенос подписок и настроек")
                .font(.system(size: 20, weight: .bold))
                .foregroundStyle(.white)
            Text("Откройте синхронизацию на компьютере или Android, покажите QR — и отсканируйте его здесь.")
                .font(.system(size: 13))
                .foregroundStyle(.white.opacity(0.66))

            Button {
                showScanner = true
            } label: {
                Label("Сканировать QR", systemImage: "qrcode.viewfinder")
                    .font(.system(size: 16, weight: .semibold))
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(Color.nimboAccent.opacity(0.18), in: RoundedRectangle(cornerRadius: 16))
                    .overlay(
                        RoundedRectangle(cornerRadius: 16)
                            .strokeBorder(Color.nimboAccent.opacity(0.45), lineWidth: 1)
                    )
            }
            .buttonStyle(.plain)
            .foregroundStyle(Color.nimboAccent)
        }
    }

    @ViewBuilder
    private var stageCard: some View {
        switch engine.stage {
        case .idle:
            EmptyView()
        case .connecting:
            statusCard(icon: "antenna.radiowaves.left.and.right", title: "Связываемся с устройством…", tint: .nimboAccent)
        case let .awaitingApproval(code):
            statusCard(
                icon: "person.badge.clock",
                title: "Подтвердите на втором устройстве",
                subtitle: code.map { "Код сверки: \($0)" } ?? "Ждём подтверждения",
                tint: .nimboAccent
            )
        case let .chooseDirection(peer):
            statusCard(icon: "arrow.left.arrow.right", title: "Устройство \(peer) готово", subtitle: "Выберите направление переноса", tint: .nimboAccent)
        case .working:
            statusCard(icon: "arrow.triangle.2.circlepath", title: "Переносим данные…", tint: .nimboAccent)
        case let .completed(summary):
            statusCard(icon: "checkmark.circle", title: "Готово", subtitle: summary, tint: .green)
        case let .failed(reason):
            statusCard(icon: "exclamationmark.triangle", title: "Не получилось", subtitle: reason, tint: .orange)
        }
    }

    private var directionButtons: some View {
        VStack(spacing: 10) {
            Button {
                engine.commit(direction: "desktop_to_android")
            } label: {
                directionLabel("Забрать на iPhone", subtitle: "Подписки и настройки со второго устройства")
            }
            .buttonStyle(.plain)
            Button {
                engine.commit(direction: "android_to_desktop")
            } label: {
                directionLabel("Отправить со iPhone", subtitle: "Перенести свои данные на второе устройство")
            }
            .buttonStyle(.plain)
        }
    }

    private func directionLabel(_ title: String, subtitle: String) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(title).font(.system(size: 16, weight: .semibold)).foregroundStyle(.white)
            Text(subtitle).font(.system(size: 12)).foregroundStyle(.white.opacity(0.6))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .background(Color.white.opacity(0.06), in: RoundedRectangle(cornerRadius: 16))
    }

    private var manualEntry: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Или вставьте ссылку")
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(.white.opacity(0.7))
            HStack {
                TextField("nimbo-sync://pair?…", text: $manualLink)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .foregroundStyle(.white)
                Button("Начать") { engine.start(link: manualLink) }
                    .disabled(manualLink.isEmpty)
                    .foregroundStyle(Color.nimboAccent)
            }
            .padding(12)
            .background(Color.white.opacity(0.06), in: RoundedRectangle(cornerRadius: 14))
        }
    }

    private var hint: some View {
        Text(
            "Устройства должны быть в одной сети Wi-Fi. При первом запуске iOS спросит разрешение на доступ к локальной сети — без него связаться не получится."
        )
        .font(.system(size: 12))
        .foregroundStyle(.white.opacity(0.5))
    }

    private func statusCard(icon: String, title: String, subtitle: String? = nil, tint: Color) -> some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: icon)
                .font(.system(size: 20))
                .foregroundStyle(tint)
            VStack(alignment: .leading, spacing: 3) {
                Text(title).font(.system(size: 15, weight: .semibold)).foregroundStyle(.white)
                if let subtitle {
                    Text(subtitle).font(.system(size: 12)).foregroundStyle(.white.opacity(0.66))
                }
            }
            Spacer()
        }
        .padding(14)
        .background(Color.white.opacity(0.06), in: RoundedRectangle(cornerRadius: 16))
    }
}

/// Считыватель QR на AVFoundation: системного варианта для SwiftUI нет.
struct NimboQrScannerView: UIViewControllerRepresentable {
    let onScan: (String) -> Void

    func makeCoordinator() -> Coordinator { Coordinator(onScan: onScan) }

    func makeUIViewController(context: Context) -> UIViewController {
        let controller = UIViewController()
        controller.view.backgroundColor = .black

        let session = AVCaptureSession()
        guard let device = AVCaptureDevice.default(for: .video),
              let input = try? AVCaptureDeviceInput(device: device),
              session.canAddInput(input) else {
            return controller
        }
        session.addInput(input)

        let output = AVCaptureMetadataOutput()
        guard session.canAddOutput(output) else { return controller }
        session.addOutput(output)
        output.setMetadataObjectsDelegate(context.coordinator, queue: .main)
        output.metadataObjectTypes = [.qr]

        let preview = AVCaptureVideoPreviewLayer(session: session)
        preview.videoGravity = .resizeAspectFill
        preview.frame = UIScreen.main.bounds
        controller.view.layer.addSublayer(preview)
        context.coordinator.session = session

        DispatchQueue.global(qos: .userInitiated).async { session.startRunning() }
        return controller
    }

    func updateUIViewController(_: UIViewController, context _: Context) {}

    final class Coordinator: NSObject, AVCaptureMetadataOutputObjectsDelegate {
        var session: AVCaptureSession?
        private let onScan: (String) -> Void
        private var handled = false

        init(onScan: @escaping (String) -> Void) {
            self.onScan = onScan
        }

        func metadataOutput(
            _: AVCaptureMetadataOutput,
            didOutput objects: [AVMetadataObject],
            from _: AVCaptureConnection
        ) {
            guard !handled,
                  let object = objects.first as? AVMetadataMachineReadableCodeObject,
                  let value = object.stringValue,
                  value.hasPrefix("nimbo-sync://") else {
                return
            }
            handled = true
            session?.stopRunning()
            onScan(value)
        }
    }
}
