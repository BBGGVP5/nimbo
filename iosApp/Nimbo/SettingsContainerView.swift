import SwiftUI

struct SettingsContainerView: View {
    @State private var showDiagnostics = false
    @State private var showAbout = false

    var body: some View {
        ComposeScreen(tab: .settings)
            .safeAreaInset(edge: .top, spacing: 0) {
                HStack(spacing: 12) {
                    Button { showAbout = true } label: {
                        Label("О приложении", systemImage: "info.circle")
                    }.buttonStyle(NimboActionStyle())
                    Button { showDiagnostics = true } label: {
                        Label("Диагностика", systemImage: "stethoscope")
                    }.buttonStyle(NimboActionStyle())
                }
                .padding(16)
                .background(NimboNative.canvas)
            }
            .sheet(isPresented: $showDiagnostics) { DiagnosticsView() }
            .sheet(isPresented: $showAbout) { AboutView() }
    }
}
