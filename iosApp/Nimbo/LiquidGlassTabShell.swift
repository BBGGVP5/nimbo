import SwiftUI

@available(iOS 26.0, *)
struct LiquidGlassTabShell: View {
    @Binding var selectedTab: NimboTab

    var body: some View {
        // One native shell for both availability branches, including accessibility fallback.
        LegacyComposeShell(selectedTab: $selectedTab)
    }
}
