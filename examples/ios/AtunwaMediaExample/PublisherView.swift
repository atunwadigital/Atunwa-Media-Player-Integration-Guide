import MediaSDK
import SwiftUI

/// Publisher mode: an app offering **every station** a publisher has, identified by the
/// publisher's SDK key.
///
/// 1. `initializePublisher(sdkKey:)` fetches the publisher and its stations. It doesn't start
///    anything.
/// 2. `loadPlayer(_:)` switches to a station and presents the SDK's full-screen player.
///    `activatePlayer(_:)` switches to it and plays in place.
///
/// The full-screen player also shows a rail of the publisher's other TV stations.
struct PublisherView: View {
    let publisherSdkKey: String

    private enum Filter: String, CaseIterable {
        case all = "All", radio = "Radio", tv = "TV"
    }

    @EnvironmentObject private var playback: Playback
    @State private var config: PublisherConfig? = MediaSDK.shared.publisherConfig
    @State private var error: String?
    @State private var refreshFailed = false
    @State private var filter = Filter.all

    var body: some View {
        NavigationStack {
            content
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .principal) {
                        HStack(spacing: 8) {
                            if let logo = config?.publisher.logo {
                                StationLogo(url: logo, size: 28)
                            }
                            Text(config?.publisher.name ?? "Atunwa").font(.headline)
                        }
                    }
                }
        }
        .miniPlayer(playback) {
            // reset() also clears publisherConfig. Initialize again (from the SDK's cache, so it's
            // instant) so the player's related-channels rail keeps working.
            MediaSDK.shared.reset()
            Task { await initialize() }
        }
        .task {
            if config == nil { await initialize() }
        }
        .alert("Couldn't refresh the stations.", isPresented: $refreshFailed) {}
    }

    @ViewBuilder
    private var content: some View {
        if let config, let defaultStation = config.players.first {
            stationList(config, defaultStation: defaultStation)
        } else if config != nil {
            MessageView(title: "No stations yet", message: "This publisher has no active stations on the Atunwa dashboard.")
        } else if let error {
            MessageView(title: "Couldn't load the stations", message: error, actionTitle: "Try again") {
                Task { await initialize() }
            }
        } else {
            ProgressView()
        }
    }

    private func stationList(_ config: PublisherConfig, defaultStation: PlayerConfig) -> some View {
        let hasTV = config.players.contains { $0.isVideo }
        let hasRadio = config.players.contains { !$0.isVideo }
        let stations = config.players.filter {
            switch filter {
            case .all: return true
            case .radio: return !$0.isVideo
            case .tv: return $0.isVideo
            }
        }

        return List {
            // Shows the active station, or the publisher's first station until one is picked. Its
            // own play button starts that station in place.
            SDKLiveCardViewRepresentable(previewPlayer: defaultStation) {
                let sdk = MediaSDK.shared
                if sdk.playerConfig != nil {
                    sdk.launchPlayer()
                } else {
                    sdk.loadPlayer(defaultStation)
                }
            }
            .listRowInsets(EdgeInsets(top: 8, leading: 16, bottom: 8, trailing: 16))
            .listRowSeparator(.hidden)

            Section {
                ForEach(stations, id: \.assetKey) { station in
                    let isActive = station.assetKey == playback.activeAssetKey
                    StationRow(
                        station: station,
                        isActive: isActive,
                        isPlaying: playback.isPlaying,
                        onOpen: { MediaSDK.shared.loadPlayer(station) },
                        onPlayPause: {
                            let sdk = MediaSDK.shared
                            if !isActive {
                                sdk.activatePlayer(station)
                            } else if playback.isPlaying {
                                sdk.pause()
                            } else {
                                sdk.play()
                            }
                        }
                    )
                }
            } header: {
                VStack(alignment: .leading, spacing: 8) {
                    Text("\(config.players.count) stations")
                    if hasTV && hasRadio {
                        Picker("Show", selection: $filter) {
                            ForEach(Filter.allCases, id: \.self) { Text($0.rawValue) }
                        }
                        .pickerStyle(.segmented)
                        .textCase(nil)
                    }
                }
            }
        }
        .listStyle(.plain)
        .refreshable { await refresh() }
    }

    /// Uses the SDK's memory or disk cache when it has one, so the app also starts offline.
    private func initialize() async {
        error = nil
        do {
            config = try await MediaSDK.shared.initializePublisher(sdkKey: publisherSdkKey)
        } catch {
            self.error = "Check the publisher SDK key and your connection. (\(error.localizedDescription))"
        }
    }

    /// Always goes to the network, so a failure is reported rather than hidden by the cache.
    private func refresh() async {
        do {
            config = try await MediaSDK.shared.refreshPublisherConfig(sdkKey: publisherSdkKey)
        } catch {
            refreshFailed = true
        }
    }
}
