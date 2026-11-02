import MediaSDK
import SwiftUI

@main
struct AtunwaMediaExampleApp: App {
    @StateObject private var playback: Playback
    private let config = AtunwaConfig.fromInfoPlist()

    init() {
        // The first access to MediaSDK.shared creates the player, the lock-screen controls and the
        // ad managers. Do it at launch, so the ad SDKs have started before the first stream loads.
        _ = MediaSDK.shared
        _playback = StateObject(wrappedValue: Playback())
    }

    var body: some Scene {
        WindowGroup {
            Group {
                switch config {
                case .singlePlayer(let playerApiKey):
                    SinglePlayerView(playerApiKey: playerApiKey)
                case .publisher(let publisherSdkKey):
                    PublisherView(publisherSdkKey: publisherSdkKey)
                case .missing(let problem):
                    MissingKeyView(problem: problem)
                }
            }
            .environmentObject(playback)
        }
    }
}
