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
        NimboShortcuts.updateAppShortcutParameters()
    }
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
                        // Storage migration does not need VPN permission and must
                        // not rewrite an existing or stopping provider at launch.
                        _ = try await NimboSubscriptionRepository.shared.migrateStoredProfileIfNeeded()
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
