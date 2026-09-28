import NimboShared
import SwiftUI

struct LegacyComposeShell: View {
    @Binding var selectedTab: NimboTab

    var body: some View {
        ComposeScreen(tab: selectedTab)
            .safeAreaInset(edge: .bottom, spacing: 0) {
                NimboTabBar(selection: $selectedTab)
            }
            .background(NimboNative.canvas.ignoresSafeArea())
            .onChange(of: selectedTab) { tab in
                IosComposeControllerKt.NimboSetIosScreen(wireName: tab.rawValue)
            }
    }
}
