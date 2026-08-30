import SwiftUI

struct LegacyComposeShell: View {
    @Binding var selectedTab: NimboTab

    var body: some View {
        TabView(selection: $selectedTab) {
            HomeContainerView()
                .tag(NimboTab.home)
                .tabItem { Label(NimboTab.home.title, systemImage: NimboTab.home.systemImage) }
            ProfilesContainerView()
                .tag(NimboTab.profiles)
                .tabItem { Label(NimboTab.profiles.title, systemImage: NimboTab.profiles.systemImage) }
            ComposeScreen(tab: .apps)
                .tag(NimboTab.routing)
                .tabItem { Label(NimboTab.routing.title, systemImage: NimboTab.routing.systemImage) }
            SettingsContainerView()
                .tag(NimboTab.settings)
                .tabItem { Label(NimboTab.settings.title, systemImage: NimboTab.settings.systemImage) }
        }
        .tint(Color(red: 0.35, green: 0.62, blue: 1.0))
    }
}
