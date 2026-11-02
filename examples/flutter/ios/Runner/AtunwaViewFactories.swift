import Flutter
import MediaSDK
import UIKit

// From flutter/docs/native-views.md.

private final class WrappedView: NSObject, FlutterPlatformView {
    private let wrapped: UIView
    init(_ view: UIView) { wrapped = view }
    func view() -> UIView { wrapped }
}

final class MiniPlayerFactory: NSObject, FlutterPlatformViewFactory {
    func create(withFrame frame: CGRect, viewIdentifier viewId: Int64, arguments args: Any?) -> FlutterPlatformView {
        MainActor.assumeIsolated {
            let view = SDKMiniPlayerView()
            view.onExpandListener = { MediaSDK.shared.launchPlayer() }
            // reset() clears the active station and publisher config. The Dart screens
            // initialize again before the next play.
            view.onCloseClickListener = { MediaSDK.shared.reset() }
            return WrappedView(view)
        }
    }
}

final class LiveCardFactory: NSObject, FlutterPlatformViewFactory {
    func create(withFrame frame: CGRect, viewIdentifier viewId: Int64, arguments args: Any?) -> FlutterPlatformView {
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
            return WrappedView(view)
        }
    }
}
