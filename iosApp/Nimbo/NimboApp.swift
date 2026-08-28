import SwiftUI

@main
struct NimboApp: App {
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
                    await vpnController.prepare()
                }
        }
    }
}
