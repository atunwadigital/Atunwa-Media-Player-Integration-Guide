import Combine
import Foundation
import MediaSDK

/// The module from react-native/docs/getting-started.md, extended with what this example needs:
/// `initializePlayer` resolves with the station, `load` starts the active station's stream, and
/// the event also reports the active station and whether the full-screen player is open.
@objc(AtunwaMedia)
final class AtunwaMedia: RCTEventEmitter {

    private var playbackSubscription: AnyCancellable?

    override static func requiresMainQueueSetup() -> Bool { true }
    override func supportedEvents() -> [String]! { ["AtunwaMediaPlayback"] }

    @objc func setup(_ resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        onMain(resolve) { _ = MediaSDK.shared }   // the first access creates the player and ad managers
    }

    @objc func initializePlayer(_ apiKey: String, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        Task { @MainActor in
            do {
                try await MediaSDK.shared.initialize(apiKey: apiKey)
                resolve(MediaSDK.shared.playerConfig.map(Self.toMap))
            } catch {
                reject("initializePlayer", "\(error)", error)
            }
        }
    }

    @objc func activeStation(_ resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        Task { @MainActor in resolve(MediaSDK.shared.playerConfig.map(Self.toMap)) }
    }

    @objc func load(_ resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        Task { @MainActor in
            do {
                if MediaSDK.shared.currentMediaItem == nil { try MediaSDK.shared.load() }
                resolve(nil)
            } catch {
                reject("load", "\(error)", error)
            }
        }
    }

    @objc func launchPlayer(_ apiKey: String, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        onMain(resolve) { MediaSDK.shared.launchPlayer(apiKey: apiKey) }
    }

    @objc func openPlayer(_ resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        onMain(resolve) { MediaSDK.shared.launchPlayer() }
    }

    @objc func initializePublisher(_ sdkKey: String, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        Task { @MainActor in
            do {
                resolve(Self.toMap(try await MediaSDK.shared.initializePublisher(sdkKey: sdkKey)))
            } catch {
                reject("initializePublisher", "\(error)", error)
            }
        }
    }

    @objc func refreshPublisher(_ sdkKey: String, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        Task { @MainActor in
            do {
                resolve(Self.toMap(try await MediaSDK.shared.refreshPublisherConfig(sdkKey: sdkKey)))
            } catch {
                reject("refreshPublisher", "\(error)", error)
            }
        }
    }

    @objc func loadStation(_ assetKey: String, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        withStation(assetKey, resolve, reject) { MediaSDK.shared.loadPlayer($0) }
    }

    @objc func activateStation(_ assetKey: String, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        withStation(assetKey, resolve, reject) { MediaSDK.shared.activatePlayer($0) }
    }

    @objc func play(_ resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        onMain(resolve) { MediaSDK.shared.play() }
    }

    @objc func pause(_ resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        onMain(resolve) { MediaSDK.shared.pause() }
    }

    @objc func reset(_ resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        onMain(resolve) { MediaSDK.shared.reset() }
    }

    // MARK: Events. Updates run only while JavaScript is listening.

    // React Native calls these on the module's own queue, so hop to the main queue, in order.

    override func startObserving() {
        DispatchQueue.main.async {
            MainActor.assumeIsolated {
                let sdk = MediaSDK.shared
                self.playbackSubscription = sdk.statePublisher
                    .combineLatest(sdk.playWhenReadyPublisher, sdk.currentMediaItemPublisher, sdk.isDedicatedPlayerActivePublisher)
                    .receive(on: DispatchQueue.main)
                    .sink { [weak self] state, playWhenReady, _, isPlayerOpen in
                        self?.sendEvent(withName: "AtunwaMediaPlayback", body: [
                            "state": state.rawValue,
                            "playWhenReady": playWhenReady,
                            "activeAssetKey": sdk.playerConfig?.assetKey as Any,
                            "isPlayerOpen": isPlayerOpen,
                        ])
                    }
            }
        }
    }

    override func stopObserving() {
        DispatchQueue.main.async { self.playbackSubscription = nil }
    }

    // MARK: Helpers

    private func onMain(_ resolve: @escaping RCTPromiseResolveBlock, _ action: @escaping @MainActor () -> Void) {
        Task { @MainActor in
            action()
            resolve(nil)
        }
    }

    /// Looks a station up by asset key in the loaded publisher config.
    private func withStation(
        _ assetKey: String,
        _ resolve: @escaping RCTPromiseResolveBlock,
        _ reject: @escaping RCTPromiseRejectBlock,
        _ action: @escaping @MainActor (PlayerConfig) -> Void
    ) {
        Task { @MainActor in
            guard let station = MediaSDK.shared.publisherConfig?.players.first(where: { $0.assetKey == assetKey }) else {
                reject("unknownStation", "No station \(assetKey); call initializePublisher first", nil)
                return
            }
            action(station)
            resolve(nil)
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
}
