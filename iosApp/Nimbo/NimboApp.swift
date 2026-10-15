import SwiftUI
import AppIntents

@main
struct NimboApp: App {
    init() {
        NimboNativeFonts.register()
        // Retire obsolete visual effects, but keep the active connection-button preference.
        for key in ["elementStyle", "backgroundStyle", "backgroundPalette",
                    "backgroundMotion", "statusParticles", "brightness", "transparency", "corners", "refraction"] {
            UserDefaults.standard.removeObject(forKey: "com.nimbo.appearance." + key)
        }
    }
    @StateObject private var vpnController = VpnController()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(vpnController)
                .task {
                    // System indexing is optional and can involve IPC. Never
                    // perform it synchronously before SwiftUI creates the UI.
                    Task(priority: .utility) {
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
                    await NimboDiagnostics.shared.record(
                        .info,
                        stage: .app,
                        code: "IOS_APP_STARTED",
                        message: "Nimbo iOS запущен"
                    )
                    do {
                        // Storage migration does not need VPN permission and must
                        // not rewrite an existing or stopping provider at launch.
                        _ = try await NimboSubscriptionRepository.shared.migrateStoredProfileIfNeeded()
                        NotificationCenter.default.post(name: Notification.Name("com.nimbo.subscription.restored"), object: nil)
                    } catch {
                        await NimboDiagnostics.shared.record(
                            .warning,
                            stage: .config,
                            code: "IOS_SUBSCRIPTION_MIGRATION_FAILED",
                            message: NimboRedactor.redact(error.localizedDescription)
                        )
                    }
                    await vpnController.restore()
                }
        }
    }
}
