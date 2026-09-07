import SwiftUI

struct SettingsContainerView: View {
    @State private var showDiagnostics = false
    @State private var showAbout = false

    var body: some View {
        ZStack {
            ComposeScreen(tab: .settings).ignoresSafeArea()
            VStack {
                HStack {
                    Spacer()
                    Button {
                        showAbout = true
                    } label: {
                        Label("О приложении", systemImage: "info.circle")
                            .labelStyle(.iconOnly)
                            .padding(12)
                    }
                    .background(.ultraThinMaterial, in: Circle())
                    .accessibilityLabel("О приложении Nimbo")

                    Button {
                        showDiagnostics = true
                    } label: {
                        Label("Диагностика", systemImage: "stethoscope")
                            .labelStyle(.iconOnly)
                            .padding(12)
                    }
                    .background(.ultraThinMaterial, in: Circle())
                    .accessibilityLabel("Открыть диагностику iOS")
                }
                Spacer()
            }
            .padding()
        }
        .sheet(isPresented: $showDiagnostics) {
            DiagnosticsView()
        }
        .sheet(isPresented: $showAbout) {
            AboutView()
        }
    }
}
