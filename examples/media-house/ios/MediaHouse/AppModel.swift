import Combine
import MediaSDK
import SwiftUI

/// The brand color the SDK's player screens use.
let brandColorHex = "#FF4F3F"

/**
 The app's content and every SDK call it makes. One publisher SDK key gives the whole app its
 live content: radio and TV stations, their branding and ads, and the publisher's news feeds.
 */
@MainActor
final class AppModel: ObservableObject {
    @Published private(set) var publisher: PublisherConfig?
    @Published private(set) var error: String?
    @Published private(set) var news: [NewsArticle] = []
    @Published private(set) var isLoadingNews = false

    let catalogue = Catalogue.load()
    let sdkKey: String
    private let theme = SDKThemeConfig(primaryColorHex: brandColorHex)

    init() {
        sdkKey = (Bundle.main.object(forInfoDictionaryKey: "ATUNWA_PUBLISHER_SDK_KEY") as? String ?? "")
            .trimmingCharacters(in: .whitespaces)
    }

    var hasKey: Bool { !sdkKey.isEmpty }

    var radioStations: [PlayerConfig] { publisher?.players.filter { $0.streamChannels?.isVideo != true } ?? [] }
    var tvStations: [PlayerConfig] { publisher?.players.filter { $0.streamChannels?.isVideo == true } ?? [] }

    /// The station the home screen features: the publisher's first one, as ordered on the dashboard.
    var featuredStation: PlayerConfig? { publisher?.players.first }

    /// A Google Ad Manager ad unit for in-feed banners. This reuses a station's from the dashboard.
    var inFeedAdUnit: String? { publisher?.players.first { !$0.gptUrl.isEmpty }?.gptUrl }

    func load() async {
        guard hasKey else { return }
        error = nil
        do {
            // Uses the SDK's memory or disk cache when it has one, so the app also starts offline.
            publisher = try await MediaSDK.shared.initializePublisher(sdkKey: sdkKey, themeConfig: theme)
            await loadNews()
        } catch {
            self.error = "Check the publisher SDK key and your connection. (\(error.localizedDescription))"
        }
    }

    /// Pull to refresh: always goes to the network, for the stations and the news.
    func refresh() async {
        if let fresh = try? await MediaSDK.shared.refreshPublisherConfig(sdkKey: sdkKey) {
            publisher = fresh
        }
        await loadNews()
    }

    /// Fetches every news feed configured for the publisher, merged and newest first.
    private func loadNews() async {
        guard let sources = publisher?.publisher.newsSources, !sources.isEmpty else { return }
        isLoadingNews = true
        news = await MediaSDK.shared.fetchNews(sources: sources)
        isLoadingNews = false
    }

    // MARK: Playback. Each kind of content uses a different SDK call.

    /// Radio: switch station and play in place. The mini player follows the listener around the app.
    func playStationInPlace(_ station: PlayerConfig) {
        withSDK { sdk in
            if self.isStationLoaded(station) {
                sdk.play()
            } else if sdk.playerConfig?.assetKey == station.assetKey {
                // Still the active station, but something on demand replaced its stream: reload it.
                try? sdk.load()
            } else {
                sdk.activatePlayer(station)
            }
        }
    }

    /// Live TV: switch station and present the SDK's full-screen player (landscape video, PiP, AirPlay).
    func watchStation(_ station: PlayerConfig) {
        withSDK { sdk in
            if sdk.playerConfig?.assetKey == station.assetKey && !self.isStationLoaded(station) {
                try? sdk.load()
                sdk.launchPlayer()
            } else {
                sdk.loadPlayer(station)
            }
        }
    }

    /// Catch-up video: hand the SDK any stream as a MediaItem, then present its full-screen player.
    func watchVideo(_ video: Video) {
        withSDK { sdk in
            try? sdk.load(item: video.mediaItem)
            sdk.launchPlayer()
        }
    }

    /// Podcast episode: on-demand audio played in place, with the mini player as its controls.
    func playEpisode(_ episode: Episode, of show: PodcastShow) {
        withSDK { sdk in
            if sdk.currentMediaItem?.id == episode.id {
                sdk.play()
            } else {
                try? sdk.load(item: episode.mediaItem(show: show))
            }
        }
    }

    func pause() { MediaSDK.shared.pause() }

    /// The mini player's close button. reset() stops playback and clears the SDK's state, including
    /// the publisher config; the next play initializes again (from the cache, so it's instant).
    func stop() { MediaSDK.shared.reset() }

    private func isStationLoaded(_ station: PlayerConfig) -> Bool {
        let sdk = MediaSDK.shared
        return sdk.playerConfig?.assetKey == station.assetKey && sdk.currentMediaItem?.streamType == .live
    }

    /// Runs `action` once the publisher is loaded in the SDK, as it isn't after reset(). load(item:)
    /// also needs this: it throws until the SDK is initialized.
    private func withSDK(_ action: @escaping (MediaSDK) -> Void) {
        Task {
            let sdk = MediaSDK.shared
            if sdk.publisherConfig == nil || !sdk.isInitialized {
                _ = try? await sdk.initializePublisher(sdkKey: sdkKey, themeConfig: theme)
            }
            action(sdk)
        }
    }
}

/// The SDK's playback state, republished for SwiftUI.
@MainActor
final class Playback: ObservableObject {
    @Published private(set) var state: SDKState = .idle
    @Published private(set) var playWhenReady = false
    /// The id of the loaded MediaItem: a catch-up video or podcast episode id, for on-demand items.
    @Published private(set) var itemId: String?
    /// The asset key of the station whose live stream is loaded, if one is.
    @Published private(set) var liveStationKey: String?
    /// The SDK's full-screen player exists, including while it's in Picture-in-Picture.
    @Published private(set) var isPlayerOpen = false

    private var cancellables = Set<AnyCancellable>()

    init() {
        let sdk = MediaSDK.shared
        sdk.statePublisher
            .combineLatest(sdk.playWhenReadyPublisher, sdk.isDedicatedPlayerActivePublisher, sdk.currentMediaItemPublisher)
            .receive(on: DispatchQueue.main)
            .sink { [weak self] state, playWhenReady, isPlayerOpen, item in
                guard let self else { return }
                self.state = state
                self.playWhenReady = playWhenReady
                self.isPlayerOpen = isPlayerOpen
                self.itemId = item?.id
                self.liveStationKey = item?.streamType == .live ? sdk.playerConfig?.assetKey : nil
            }
            .store(in: &cancellables)
    }

    var hasStream: Bool { state != .idle && state != .initializing }
    var isPlaying: Bool { playWhenReady && [.loading, .buffering, .playing, .adPlaying].contains(state) }
    var showsMiniPlayer: Bool { hasStream && !isPlayerOpen }

    func isPlaying(station: PlayerConfig) -> Bool { isPlaying && liveStationKey == station.assetKey }
    func isPlaying(itemId id: String) -> Bool { isPlaying && itemId == id }
}
