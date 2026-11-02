import MediaSDK
import UIKit

// From react-native/docs/native-views.md. React Native strips the `Manager` suffix, so these
// register as `AtunwaMiniPlayer` and `AtunwaLiveCard`, the same names as on Android.

@objc(AtunwaMiniPlayerManager)
final class AtunwaMiniPlayerManager: RCTViewManager {
    override static func requiresMainQueueSetup() -> Bool { true }

    override func view() -> UIView! {
        MainActor.assumeIsolated {
            let view = SDKMiniPlayerView()
            view.onExpandListener = { MediaSDK.shared.launchPlayer() }
            // reset() clears the active station and publisher config. The JavaScript screens
            // initialize again before the next play.
            view.onCloseClickListener = { MediaSDK.shared.reset() }
            return view
        }
    }
}

@objc(AtunwaLiveCardManager)
final class AtunwaLiveCardManager: RCTViewManager {
    override static func requiresMainQueueSetup() -> Bool { true }

    override func view() -> UIView! {
        MainActor.assumeIsolated {
            let view = SDKLiveCardView()
            view.previewPlayer = MediaSDK.shared.publisherConfig?.players.first
            view.onCardClickListener = { [weak view] in
                let sdk = MediaSDK.shared
                if sdk.playerConfig != nil {
                    sdk.launchPlayer()
                } else if let preview = view?.previewPlayer {
                    sdk.loadPlayer(preview)
                }
            }
            return view
        }
    }
}
