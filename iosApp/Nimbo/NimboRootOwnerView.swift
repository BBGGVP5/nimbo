import SwiftUI
import AppIntents

/// This entire child is constructed only after the native gate. Keeping the
/// StateObject here is essential: the launch surface must not realize VPN state.
@MainActor
struct NimboRootOwnerView: View {
    let attempt: UUID
    @ObservedObject var launch: NimboLaunchCoordinator
    @StateObject private var vpnController = VpnController()

    var body: some View {
        RootView()
            .environmentObject(vpnController)
            .environment(\.nimboLaunchContext, NimboLaunchContext(attempt: attempt, coordinator: launch))
            .onAppear { launch.mark(.rootOwnerAppeared, attempt: attempt) }
            .background {
                if launch.exposeRoot {
                    NimboLaunchAppearanceObserver {
                        launch.rootDisplayAcknowledged(attempt)
                    }
                    .allowsHitTesting(false)
                }
            }
            .task(id: launch.gate.completed) {
                if launch.gate.completed { await registerOptionalShortcuts() }
            }
            .task {
                guard launch.claimBootstrap(attempt) else { return }
                await NimboDiagnostics.shared.record(.info, stage: .app,
                    code: "IOS_APP_STARTED", message: "Nimbo iOS запущен")
                launch.mark(.migratingProfile, attempt: attempt)
                do {
                    // Retain the original migration/observe-only restore behavior.
                    _ = try await NimboSubscriptionRepository.shared.migrateStoredProfileIfNeeded()
                    NotificationCenter.default.post(name: Notification.Name("com.nimbo.subscription.restored"), object: nil)
                } catch {
                    launch.bootstrapFailed(.migrationFailed, attempt: attempt)
                    await NimboDiagnostics.shared.record(.warning, stage: .config,
                        code: "IOS_SUBSCRIPTION_MIGRATION_FAILED",
                        message: NimboRedactor.redact(error.localizedDescription))
                }
                launch.mark(.observingVPN, attempt: attempt)
                await vpnController.restore()
                if case .failed = vpnController.state {
                    launch.bootstrapFailed(.vpnObservationFailed, attempt: attempt)
                }
                launch.bootstrapFinished(attempt)
            }
    }

    private func registerOptionalShortcuts() async {
        guard launch.gate.owns(attempt), launch.gate.completed else { return }
        let hasMetadata = Bundle.main.url(forResource: "extract", withExtension: "actionsdata",
                                          subdirectory: "Metadata.appintents") != nil
        _ = await NimboShortcutRegistration.shared.refreshIfAvailable(hasMetadata: hasMetadata) {
            NimboShortcuts.updateAppShortcutParameters()
        }
        if !hasMetadata {
            await NimboDiagnostics.shared.record(.warning, stage: .app,
                code: "IOS_APPINTENTS_METADATA_MISSING",
                message: "В сборке отсутствует системный индекс быстрых команд")
        }
    }
}
