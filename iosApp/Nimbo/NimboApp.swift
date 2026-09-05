import SwiftUI
import AppIntents

@main
struct NimboApp: App {
    init() { NimboShortcuts.updateAppShortcutParameters() }
    @StateObject private var vpnController = VpnController()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(vpnController)
                .task {
                    await NimboDiagnostics.shared.record(
                        .info,
                        stage: .app,
                        code: "IOS_APP_STARTED",
                        message: "Nimbo iOS запущен"
                    )
                    do {
                        if let migrated = try await NimboSubscriptionRepository.shared.migrateStoredProfileIfNeeded(),
                           let selected = migrated.selectedServer {
                            try await vpnController.stageConfiguration(data: NimboSubscriptionRepository.shared.stagingData(for: selected))
                        }
                    } catch {
                        await NimboDiagnostics.shared.record(
                            .warning,
                            stage: .config,
                            code: "IOS_SUBSCRIPTION_MIGRATION_FAILED",
                            message: NimboRedactor.redact(error.localizedDescription)
                        )
                    }
                    await vpnController.prepare()
                }
        }
    }
}
