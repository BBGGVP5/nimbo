import SwiftUI

@available(iOS 26.0, *)
struct LiquidGlassTabShell: View {
    @Binding var selectedTab: NimboTab

    var body: some View {
        TabView(selection: $selectedTab) {
            Tab("Главная", systemImage: NimboTab.home.systemImage, value: NimboTab.home) {
                NimboTabRoot(tab: .home)
            }
            Tab("Профили", systemImage: NimboTab.profiles.systemImage, value: NimboTab.profiles) {
                NimboTabRoot(tab: .profiles)
            }
            Tab("Статистика", systemImage: NimboTab.stats.systemImage, value: NimboTab.stats) {
                NimboTabRoot(tab: .stats)
            }
            Tab("Настройки", systemImage: NimboTab.settings.systemImage, value: NimboTab.settings) {
                NimboTabRoot(tab: .settings)
            }
        }
        .tabBarMinimizeBehavior(.onScrollDown)
        .tint(Color(red: 0.35, green: 0.62, blue: 1.0))
    }
}

@available(iOS 26.0, *)
private struct NimboTabRoot: View {
    let tab: NimboTab

    var body: some View {
        NavigationStack {
            Group {
                if tab == .home {
                    HomeContainerView()
                } else if tab == .profiles {
                    ProfilesContainerView()
                } else if tab == .settings {
                    SettingsContainerView()
                } else {
                    ComposeScreen(tab: tab)
                        .ignoresSafeArea(.container, edges: .top)
                }
            }
            .navigationTitle(tab.title)
            .navigationBarHidden(true)
        }
    }
}
