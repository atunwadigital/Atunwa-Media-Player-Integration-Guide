import Combine
import MediaSDK
import SwiftUI

/// The SDK's playback state, republished for SwiftUI.
@MainActor
final class Playback: ObservableObject {
    @Published private(set) var state: SDKState = .idle
    @Published private(set) var playWhenReady = false
    /// The asset key of the active station, if any.
    @Published private(set) var activeAssetKey: String?
    /// The SDK's full-screen player exists, including while it's in Picture-in-Picture.
    @Published private(set) var isPlayerOpen = false

    private var cancellables = Set<AnyCancellable>()

    init() {
        let sdk = MediaSDK.shared
        sdk.statePublisher
            .combineLatest(sdk.playWhenReadyPublisher, sdk.isDedicatedPlayerActivePublisher, sdk.currentMediaItemPublisher)
            .receive(on: DispatchQueue.main)
            .sink { [weak self] state, playWhenReady, isPlayerOpen, _ in
                guard let self else { return }
                self.state = state
                self.playWhenReady = playWhenReady
                self.isPlayerOpen = isPlayerOpen
                self.activeAssetKey = sdk.playerConfig?.assetKey
            }
            .store(in: &cancellables)
    }

    /// A stream is loaded: playing, paused, buffering, in an ad, or failed.
    var hasStream: Bool { state != .idle && state != .initializing }

    var isPlaying: Bool { playWhenReady && [.loading, .buffering, .playing, .adPlaying].contains(state) }

    /// Show the mini player while a stream is loaded and the full-screen player isn't open.
    var showsMiniPlayer: Bool { hasStream && !isPlayerOpen }
}
