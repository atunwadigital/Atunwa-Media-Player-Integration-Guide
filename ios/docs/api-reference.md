# API reference

Everything public in `import MediaSDK`. `MediaSDK` and every view are `@MainActor`.

- [`MediaSDK`](#mediasdk)
- [Models](#models)
- [UI](#ui)
- [Player & ads](#player--ads)
- [Utilities](#utilities)
- [REST endpoints used](#rest-endpoints-used)

---

## `MediaSDK`

`MediaSDK.shared` is the only instance.

### Single-player mode

| Signature | Notes |
|---|---|
| `func preload(apiKey: String) async` | Fetches the config into the in-memory cache. Doesn't activate the station. Never throws |
| `func initialize(apiKey: String, themeConfig: SDKThemeConfig = SDKThemeConfig(), autoplay: Bool = true) async throws` | Fetches the config (memory cache → network with 3 retries → disk), sets `playerConfig`, applies the theme. State `.initializing` → `.idle`, or `.error` |
| `func launchPlayer(apiKey: String, themeConfig: SDKThemeConfig = SDKThemeConfig(), autoplay: Bool = true, from: UIViewController? = nil)` | Presents `SDKPlayerViewController` immediately, then runs `initialize` and `load()` |
| `func refreshPlayerConfig(apiKey: String) async throws -> PlayerConfig` | Network only, no cache fallback. Updates both caches and `playerConfig`. `@discardableResult` |

### Publisher mode

| Signature | Notes |
|---|---|
| `func initializePublisher(sdkKey: String, themeConfig: SDKThemeConfig = SDKThemeConfig()) async throws -> PublisherConfig` | Fetches the publisher config (memory → network → disk), sets `publisherConfig`, applies publisher branding. Doesn't change state. `@discardableResult` |
| `func launchPublisher(sdkKey: String, themeConfig: SDKThemeConfig = SDKThemeConfig(), from: UIViewController? = nil)` | Runs `initializePublisher`, then `loadPlayer` if the publisher has exactly one station |
| `func loadPlayer(_ playerConfig: PlayerConfig, from: UIViewController? = nil)` | Activates the station and presents the player |
| `func activatePlayer(_ playerConfig: PlayerConfig)` | Activates the station without presenting anything. If it's already active, calls `play()` |
| `func refreshPublisherConfig(sdkKey: String) async throws -> PublisherConfig` | Network only, no cache fallback. Updates both caches and `publisherConfig`. `@discardableResult` |

### Playback

| Signature | Notes |
|---|---|
| `func load() throws` | Loads the active `playerConfig`'s stream as a live item. Throws `SDKError.notInitialized` if nothing is active |
| `func load(item: MediaItem) throws` | Loads any item. Throws `SDKError.notInitialized` before initialization |
| `func play()` | Starts or resumes playback |
| `func pause()` | Pauses playback |
| `func reset()` | Stops playback, clears the item, both configs and ad state, forces `.idle`, closes the player and PiP, sends `dismissPlayerSignal` |
| `func selectQuality(_ option: StreamQualityOption)` | Plays the current stream at one of `availableQualities` (`.auto` lets ABR choose). Resets to `.auto` for each station |
| `func canSeek() -> Bool` | `true` when the live stream has a DVR window wider than 5 s |
| `func seek(by seconds: Double)` | Seeks within the DVR window, clamped |

### Player UI

| Signature | Notes |
|---|---|
| `func launchPlayer(from: UIViewController? = nil)` | Presents `SDKPlayerViewController` for whatever is active. Brings back the one in PiP; does nothing if it's already on screen |
| `func avPlayer() -> AVPlayer?` | The shared player, for attaching to your own `SDKVideoView` |
| `func createVideoView() -> SDKVideoView` | A video view bound to the shared player, with PiP set up |
| `func createVideoViewRepresentable() -> SDKVideoViewRepresentable` | SwiftUI version |
| `func attachVideoView(_ view: SDKVideoView)` | Binds your view to the shared player and sets up PiP on it |
| `func togglePiP()` | Starts or stops PiP for the last view PiP was set up on |
| `func incrementDedicatedPlayerActive()` / `func decrementDedicatedPlayerActive()` | Call from your own full-screen player's appear/disappear. Refcounted |

### News

| Signature | Notes |
|---|---|
| `func fetchNews(sources: [NewsSource]) async -> [NewsArticle]` | Fetches every feed in parallel, merges, sorts newest first. Never throws |

### Publishers

| Publisher | Element |
|---|---|
| `statePublisher` | `SDKState` |
| `playWhenReadyPublisher` | `Bool` |
| `currentMediaItemPublisher` | `MediaItem?` |
| `adPlaybackStatePublisher` | `AdPlaybackState` |
| `adCountdownPublisher` | `Int`, in seconds |
| `hasCompanionAdPublisher` | `Bool` |
| `isDedicatedPlayerActivePublisher` | `Bool` |
| `dismissPlayerSignal` | `PassthroughSubject<Void, Never>` |
| `amplitude` | `PassthroughSubject<Float, Never>`, from 0 to 1 |

### Properties

| Property | Type | Notes |
|---|---|---|
| `currentState` | `SDKState` | |
| `playWhenReady` | `Bool` | |
| `currentMediaItem` | `MediaItem?` | |
| `currentAdPlaybackState` | `AdPlaybackState` | |
| `isDedicatedPlayerActive` | `Bool` | The full-screen player exists, including while in PiP |
| `isPictureInPictureActive` | `Bool` | A PiP window is showing |
| `isInitialized` | `Bool` (read-only) | Set by `initialize`/`initializePublisher`/station switches, cleared by `reset()` |
| `playerConfig` | `PlayerConfig?` (read-only) | Active station |
| `publisherConfig` | `PublisherConfig?` (read-only) | Loaded publisher |
| `availableQualities` | `[StreamQualityOption]` (read-only) | `.auto`, then the current stream's own variants, highest first. Just `[.auto]` until they're read, and for single-variant streams. Also `availableQualitiesPublisher` |
| `selectedQuality` | `StreamQualityOption` (read-only) | Also `selectedQualityPublisher` |
| `videoFullscreenByDefault` | `Bool` (`var`, default `true`) | `SDKPlayerViewController` opens video full screen in landscape |
| `videoPipOnBack` | `Bool` (`var`, default `true`) | Closing the player during a playing video minimizes it into PiP |
| `imaConfig` | `IMAConfig` (`var`) | Set before the first ad-enabled load |
| `apsBidProvider` | `APSBidProvider` (`var`) | Header-bidding seam for banners. Default `NoOpAPSBidProvider()` |
| `gptManager` | `SDKGptManager` | `isAdsEnabled` |

## Models

### Playback

| Type | Members |
|---|---|
| `SDKState` (enum, `String`) | `idle`, `initializing`, `loading`, `ready`, `playing`, `paused`, `buffering`, `error`, `adPlaying` |
| `SDKError` (enum) | `notInitialized`, `networkFailure` |
| `MediaItem` (struct) | `id`, `streamUrl: URL`, `streamChannels: StreamChannels?`, `mediaType: MediaType` (default `.audio`), `streamType: StreamType`, `metadata: MediaMetadata?`, `imaUrl: String?`, `gptUrl: String?` |
| `MediaMetadata` (struct) | `title`, `artistOrShow`, `artworkUrl: URL?` |
| `StreamType` (enum) | `vod`, `live` |
| `MediaType` (enum) | `audio`, `video` |
| `StreamQualityOption` (struct) | `label` ("Auto", "1080p", "720p60", "128 kbps"), `detail: String?` (bitrate, for video), `peakBitRate`, `width: Int?`, `height: Int?`, `isAuto`; `static let auto` |

### Configuration (from the backend; `Codable`)

| Type | Members |
|---|---|
| `PlayerConfig` | `assetKey` (JSON `apiKey`), `streamURL` (`url`), `playerName` (`name`), `websiteName` (`website`), `logo`, `domain` (from `authorisedDomain.url`), `publisher: Publisher?`, `loadingAnimation`, `adUrl` (`imaUrl`), `gptUrl`, `streamChannels` |
| `Publisher` | `name`, `id`, `tier: PricingTier?`, `logo`, `newsSources` |
| `PublisherConfig` | `publisher: PublisherInfo`, `players: [PlayerConfig]` |
| `PublisherInfo` | `name`, `id`, `logo`, `tier`, `newsSources` |
| `StreamChannels` | `isAudio`, `isVideo` |
| `PlayerConfig.AuthorisedDomain` | `url` |

All have public initializers, so you can build a `PlayerConfig` yourself (for example from another
directory API) and pass it to `loadPlayer`/`activatePlayer`.

### Theming

| Type | Members |
|---|---|
| `SDKThemeConfig` | `primaryColorHex` (`#0057FF`), `secondaryColorHex`, `liveBadgeColorHex` (`#F44336`), `loadingColorHex` |
| `SDKTheme` | `tier`, `primaryColorHex`, `secondaryColorHex`, `stationLogoUrl: URL?`, `liveBadgeColorHex`, `loadingColorHex` |
| `PricingTier` (enum) | `standard`, `custom` |

### Ads

| Type | Members |
|---|---|
| `AdPlaybackState` (enum) | `idle`, `requesting`, `loaded`, `buffering`, `playing`, `paused`, `completed`, `skipped`, `error(description:)`. `buffering`, `paused` and `skipped` aren't emitted today |
| `AtunwaAdEvent` (enum) | `loaded`, `started`, `progress(countdownMs:)`, `completed`, `skipped`, `contentResumeRequested`, `error(description:)` |
| `IMAConfig` (struct) | See [Advertising → Tuning IMA](ad-integration.md#tuning-ima-imaconfig) |
| `APSBidProvider` (protocol) | `fetchBids(adUnitId:sizes:) async -> [String: String]` |
| `NoOpAPSBidProvider` | Returns no bids |

### News

| Type | Members |
|---|---|
| `NewsSource` | `id`, `name`, `logo`, `feedUrl` |
| `NewsArticle` | `id` (= `link`), `title`, `link`, `publishedAt: Date`, `thumbnailUrl`, `sourceName`, `sourceLogo` |

## UI

| Type | API |
|---|---|
| `SDKPlayerViewController` | Present it through `MediaSDK.shared.launchPlayer` / `loadPlayer` |
| `SDKLiveCardView` | `onCardClickListener`, `previewPlayer` |
| `SDKLiveCardViewRepresentable` | `init(previewPlayer:onCardClickListener:)` |
| `SDKMiniPlayerView` | `onExpandListener`, `onCloseClickListener` |
| `SDKAudioPlayerView` | *(none, fully reactive)* |
| `SDKVideoView` | `init(player:)`, `playerLayer`, `attach(player:)` |
| `SDKVideoViewRepresentable` | `init(player:)` |
| `SDKBannerAdView` | `onContentStateChanged`, `isSuppressed` |
| `SDKGptBannerView` | `init(adUnitId:)`, `adUnitId`, `sizes`, `customTargeting`, `onContentStateChanged` |
| `SDKGptBannerViewRepresentable` | `init(adUnitId:customTargeting:onContentStateChanged:)` |
| `SDKCompanionBannerView` | `activateIfNeeded()`; toggle `isHidden` |
| `SDKWaveformView` | `barColor`, `updateAmplitude(_:)` |
| `SDKWaveformRepresentable` | `init(amplitude:color:)` |
| `SDKLiveBadgeView` | `color`, `isAnimating` |
| `SDKLiveBadgeRepresentable` | `init(color:isAnimating:)` |
| `SDKShakeMeView` | `onShakeDetected`, `promptText`, `shakeThreshold`, `minimumShakeInterval`, `samplingInterval`, `startMonitoring()`, `stopMonitoring()` |
| `SDKThemeManager` (`shared`) | `themePublisher`, `currentTheme`, `updateTheme(_:)` |

## Player & ads

| Type | API |
|---|---|
| `SDKAdDisplayContainer` (`shared`) | `adContainerView`, `companionContainerView`, `attachAdSurface(to:viewController:)`, `registerCompanionSlots(ownerId:slots:)`, `unregisterCompanionSlots(ownerId:)`, `hasRealPresenter` |
| `SDKGptManager` | `isAdsEnabled` (`true` once Google Mobile Ads has started; needs a valid Ad Manager app ID in `GADApplicationIdentifier`) |

## Utilities

| API | |
|---|---|
| `UIColor(hex:)` | Parses `#RRGGBB` |

## REST endpoints used

| Endpoint | Called by |
|---|---|
| `GET https://amp.atunwadigital.com/sdk/v1/player?key={apiKey}` | `preload`, `initialize`, `refreshPlayerConfig` |
| `GET https://amp.atunwadigital.com/sdk/v1/config?key={sdkKey}` | `initializePublisher`, `refreshPublisherConfig` |
