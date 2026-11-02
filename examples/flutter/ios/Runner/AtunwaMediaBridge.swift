import Combine
import Flutter
import MediaSDK

/// The bridge from flutter/docs/getting-started.md, extended with what this example needs:
/// `initialize` returns the station, `load` starts the active station's stream, and the event
/// channel also reports the active station.
final class AtunwaMediaBridge: NSObject, FlutterStreamHandler {

    private var stateSubscription: AnyCancellable?

    static func register(with registrar: FlutterPluginRegistrar) {
        let bridge = AtunwaMediaBridge()
        FlutterMethodChannel(name: "atunwa/media", binaryMessenger: registrar.messenger())
            .setMethodCallHandler { call, result in
                Task { @MainActor in await bridge.handle(call, result) }
            }
        FlutterEventChannel(name: "atunwa/media/state", binaryMessenger: registrar.messenger())
            .setStreamHandler(bridge)
    }

    @MainActor
    private func handle(_ call: FlutterMethodCall, _ result: @escaping FlutterResult) async {
        let args = call.arguments as? [String: Any] ?? [:]
        let sdk = MediaSDK.shared   // the first access creates the player and ad managers

        do {
            switch call.method {
            case "setup":
                result(nil)
            case "initialize":
                try await sdk.initialize(apiKey: args["apiKey"] as? String ?? "")
                result(sdk.playerConfig.map(Self.toMap))
            case "activeStation":
                result(sdk.playerConfig.map(Self.toMap))
            case "load":
                if sdk.currentMediaItem == nil { try sdk.load() }
                result(nil)
            case "launchPlayer":
                sdk.launchPlayer(apiKey: args["apiKey"] as? String ?? "")
                result(nil)
            case "openPlayer":
                sdk.launchPlayer()
                result(nil)
            case "initializePublisher":
                let config = try await sdk.initializePublisher(sdkKey: args["sdkKey"] as? String ?? "")
                result(Self.toMap(config))
            case "refreshPublisher":
                let config = try await sdk.refreshPublisherConfig(sdkKey: args["sdkKey"] as? String ?? "")
                result(Self.toMap(config))
            case "loadStation", "activateStation":
                let assetKey = args["assetKey"] as? String
                guard let station = sdk.publisherConfig?.players.first(where: { $0.assetKey == assetKey }) else {
                    result(FlutterError(code: "unknownStation",
                                        message: "No station \(assetKey ?? ""); call initializePublisher first",
                                        details: nil))
                    return
                }
                if call.method == "loadStation" { sdk.loadPlayer(station) } else { sdk.activatePlayer(station) }
                result(nil)
            case "play":
                sdk.play(); result(nil)
            case "pause":
                sdk.pause(); result(nil)
            case "reset":
                sdk.reset(); result(nil)
            default:
                result(FlutterMethodNotImplemented)
            }
        } catch {
            result(FlutterError(code: call.method, message: "\(error)", details: nil))
        }
    }

    private static func toMap(_ config: PublisherConfig) -> [String: Any] {
        [
            "name": config.publisher.name,
            "logo": config.publisher.logo as Any,
            "stations": config.players.map(toMap),
        ]
    }

    private static func toMap(_ station: PlayerConfig) -> [String: Any] {
        [
            "assetKey": station.assetKey,
            "name": station.playerName,
            "logo": station.logo,
            "isVideo": station.streamChannels?.isVideo ?? false,
            "publisherName": station.publisher?.name as Any,
        ]
    }

    // MARK: FlutterStreamHandler

    /// Sends `{state, playWhenReady, activeAssetKey, isPlayerOpen}` whenever any of them changes.
    func onListen(withArguments arguments: Any?, eventSink events: @escaping FlutterEventSink) -> FlutterError? {
        MainActor.assumeIsolated {
            let sdk = MediaSDK.shared
            stateSubscription = sdk.statePublisher
                .combineLatest(sdk.playWhenReadyPublisher, sdk.currentMediaItemPublisher, sdk.isDedicatedPlayerActivePublisher)
                .receive(on: DispatchQueue.main)
                .sink { state, playWhenReady, _, isPlayerOpen in
                    events([
                        "state": state.rawValue,
                        "playWhenReady": playWhenReady,
                        "activeAssetKey": sdk.playerConfig?.assetKey as Any,
                        "isPlayerOpen": isPlayerOpen,
                    ])
                }
        }
        return nil
    }

    func onCancel(withArguments arguments: Any?) -> FlutterError? {
        stateSubscription = nil
        return nil
    }
}
