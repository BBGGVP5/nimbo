import SwiftUI
import UIKit

struct NimboLiveActivitySettingsView: View {
    @EnvironmentObject private var vpn: VpnController
    @Environment(\.scenePhase) private var scenePhase
    @AppStorage(NimboLiveActivityPolicy.preferenceKey) private var enabled = true
    @State private var authorized = false

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Toggle(isOn: $enabled) {
                Label("Dynamic Island и Live Activity", systemImage: "cloud.fill").font(.headline)
            }
            Text("Live Activity на экране блокировки и Dynamic Island, если поддерживается устройством.")
                .font(.caption).foregroundStyle(NimboNative.secondary)
            Text(enabled
                ? "Выключите, чтобы скрыть пилюлю без отключения VPN."
                : "Пилюля и Live Activity скрыты. VPN продолжает работать; его системный индикатор не меняется.")
                .font(.caption).foregroundStyle(NimboNative.secondary)
            HStack {
                if enabled {
                    Label("NIMBO", systemImage: "cloud.fill")
                        .font(.caption.weight(.semibold)).padding(.horizontal, 14).padding(.vertical, 8)
                        .foregroundStyle(.white).background(.black, in: Capsule())
                        .accessibilityLabel("Предпросмотр пилюли")
                }
                Spacer()
                Button(authorized ? "Настройки iOS" : "Разрешить в iOS") {
                    guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
                    UIApplication.shared.open(url)
                }.font(.caption).frame(minHeight: 44)
            }
            if enabled && !authorized {
                Text("Live Activities отключены в системе или недоступны на этом устройстве.")
                    .font(.caption).foregroundStyle(NimboNative.secondary)
            }
        }
        .nimboCard().padding(.horizontal, 16).padding(.vertical, 8)
        .onAppear { authorized = NimboLiveActivityBridge.authorized }
        .onChange(of: enabled) { _ in vpn.refreshLiveActivity() }
        .onChange(of: scenePhase) { phase in
            if phase == .active { authorized = NimboLiveActivityBridge.authorized }
        }
    }
}
