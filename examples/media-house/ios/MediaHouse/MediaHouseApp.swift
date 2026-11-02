import MediaSDK
import SwiftUI

@main
struct MediaHouseApp: App {
    @StateObject private var model: AppModel
    @StateObject private var playback: Playback

    init() {
        // The first access to MediaSDK.shared creates the player, the lock-screen controls and the
        // ad managers. Do it at launch, so the ad SDKs have started before the first stream loads.
        _ = MediaSDK.shared
        _model = StateObject(wrappedValue: AppModel())
        _playback = StateObject(wrappedValue: Playback())
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(model)
                .environmentObject(playback)
                .tint(.brand)
                .preferredColorScheme(.dark)
        }
    }
}

struct RootView: View {
    @EnvironmentObject private var model: AppModel
    @EnvironmentObject private var playback: Playback
    @State private var tab = Tab.home
    @State private var podcastPath: [PodcastShow] = []

    var body: some View {
        Group {
            if !model.hasKey {
                MessageView(
                    title: "Add your publisher SDK key",
                    message: "Copy Config/MediaHouse.local.xcconfig.example to Config/MediaHouse.local.xcconfig, set ATUNWA_PUBLISHER_SDK_KEY to the key Atunwa gave you, and build again."
                )
            } else if model.publisher == nil, let error = model.error {
                MessageView(title: "Couldn't load the stations", message: error, actionTitle: "Try again") {
                    Task { await model.load() }
                }
            } else if model.publisher == nil {
                ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                tabs
            }
        }
        .task { await model.load() }
    }

    private var tabs: some View {
        TabView(selection: $tab) {
            NavigationStack {
                HomeView(tab: $tab, podcastPath: $podcastPath).toolbar(.hidden, for: .navigationBar)
            }
            .miniPlayer(playback, onClose: model.stop)
            .tabItem { Label("Home", systemImage: "house.fill") }
            .tag(Tab.home)

            NavigationStack { RadioView() }
                .miniPlayer(playback, onClose: model.stop)
                .tabItem { Label("Radio", systemImage: "headphones") }
                .tag(Tab.radio)

            NavigationStack { TVView() }
                .miniPlayer(playback, onClose: model.stop)
                .tabItem { Label("TV", systemImage: "tv") }
                .tag(Tab.tv)

            NavigationStack { NewsView() }
                .miniPlayer(playback, onClose: model.stop)
                .tabItem { Label("News", systemImage: "newspaper") }
                .tag(Tab.news)

            NavigationStack(path: $podcastPath) {
                PodcastsView().navigationDestination(for: PodcastShow.self) { PodcastDetailView(show: $0) }
            }
            .miniPlayer(playback, onClose: model.stop)
            .tabItem { Label("Podcasts", systemImage: "mic") }
            .tag(Tab.podcasts)
        }
    }
}
