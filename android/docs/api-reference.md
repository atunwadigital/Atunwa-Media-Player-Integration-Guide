# API reference

The public surface of `:mediasdk`, grouped by package. Everything marked `internal` in the source
is left out on purpose. Don't depend on it.

- [`MediaSDK`](#mediasdk)
- [Models](#models)
- [UI](#ui)
- [Player & ads](#player--ads)
- [Utilities](#utilities)

---

## `MediaSDK`

`com.atunwadigital.mediasdk.MediaSDK` is a Kotlin `object`, so there's one instance per process.
Call the suspend functions from a coroutine, for example `lifecycleScope.launch { … }`. All other
functions are main-thread safe.

### Setup

| Signature | Description |
|---|---|
| `suspend fun setup(context: Context): Result<Unit>` | Builds the shared `MediaController` and the ad managers. Idempotent. Call it first. |

### Single-player mode

| Signature | Description |
|---|---|
| `suspend fun preload(apiKey: String): Result<Unit>` | Fetches the config into the in-memory cache. It doesn't activate the station. |
| `suspend fun initialize(context, apiKey, themeConfig = SDKThemeConfig(), autoplay = true): Result<Unit>` | Fetches the config (or reuses the cache, or the disk copy when offline), sets `playerConfig`, and applies the theme. State: `INITIALIZING` → `IDLE`, or `ERROR`. |
| `fun launchPlayer(context, apiKey, themeConfig = SDKThemeConfig(), autoplay = true)` | Opens `SDKPlayerActivity` immediately, then runs `initialize` + `load` in the background. If `apiKey` is already active, it only brings the UI to the front. |
| `suspend fun refreshPlayerConfig(context, apiKey): Result<PlayerConfig>` | Fetches from the network with no cache fallback, and updates the cache, the disk copy and `playerConfig`. |

### Publisher mode

| Signature | Description |
|---|---|
| `suspend fun initializePublisher(context, sdkKey, themeConfig = SDKThemeConfig()): Result<PublisherConfig>` | Fetches the publisher config and station list (with cache and disk fallback), sets `publisherConfig`, and applies publisher branding. |
| `fun launchPublisher(context, sdkKey, themeConfig = SDKThemeConfig())` | Runs `initializePublisher`, then `loadPlayer` if the publisher has exactly one station. |
| `fun loadPlayer(context, playerConfig: PlayerConfig)` | Activates the station and opens `SDKPlayerActivity`. |
| `fun activatePlayer(context, playerConfig: PlayerConfig)` | Activates the station without navigating. If it's already active, calls `play()`. |
| `suspend fun refreshPublisherConfig(context, sdkKey): Result<PublisherConfig>` | Fetches from the network with no cache fallback, and updates `publisherConfig`. |

### Playback

| Signature | Description |
|---|---|
| `fun load()` | Loads the active `playerConfig`'s stream as a live item. Sets `ERROR` if there's no active config. |
| `fun load(mediaItem: MediaItem, autoplay: Boolean? = null)` | Loads any item. `autoplay = null` uses the default set by `initialize`. Requires `setup()`. |
| `fun play()` | Starts or resumes playback. If called while a live load is pending, it's applied once the load finishes. |
| `fun pause()` | Pauses playback. |
| `fun reset()` | Stops playback and clears the item, `playerConfig`, `publisherConfig`, ad state and `isInitialized`. Emits `dismissPlayerSignal`. |
| `fun launchPlayer(context)` | Opens `SDKPlayerActivity` for whatever is active. |
| `fun attachVideoView(videoView: SDKVideoView)` | Binds the view to the shared controller. |
| `fun createVideoView(context): SDKVideoView` | Creates a bound `SDKVideoView`. |

### News

| Signature | Description |
|---|---|
| `suspend fun fetchNews(sources: List<NewsSource>): List<NewsArticle>` | Fetches every feed in parallel, merges them, and sorts newest first. Failed sources are dropped. |

### Observable state

| Property | Type |
|---|---|
| `state` | `StateFlow<SDKState>` |
| `playWhenReady` | `StateFlow<Boolean>` |
| `currentMediaItemFlow` | `StateFlow<MediaItem?>` |
| `adPlaybackState` | `StateFlow<AdPlaybackState>` |
| `adCountdown` | `StateFlow<Int>`, in seconds |
| `hasCompanionAd` | `StateFlow<Boolean>` |
| `companionAdUrl` | `StateFlow<String?>`, reserved and currently always `null` |
| `isDedicatedPlayerActive` | `StateFlow<Boolean>` |
| `dismissPlayerSignal` | `SharedFlow<Unit>` |
| `amplitude` | `SharedFlow<Float>`, from 0 to 1 |

### Snapshot properties

| Property | Type | Notes |
|---|---|---|
| `isInitialized` | `Boolean` | Set by `setup()` and cleared by `reset()` |
| `playerConfig` | `PlayerConfig?` | Active station |
| `publisherConfig` | `PublisherConfig?` | Loaded publisher |
| `currentMediaItem` | `MediaItem?` | |
| `mediaController` | `MediaController?` | Advanced use only, such as seeking |
| `imaConfig` | `IMAConfig` (`var`) | Set it before the first ad-enabled load |
| `videoPipOnBack` | `Boolean` (`var`, default `true`) | Whether Back on a playing video station minimizes `SDKPlayerActivity` into Picture-in-Picture instead of closing it |
| `videoFullscreenByDefault` | `Boolean` (`var`, default `true`) | Whether `SDKPlayerActivity` opens video stations full screen in landscape. Set it before launching the player |
| `imaAdProvider` | `ImaAdProvider?` | Companion slot registration |
| `gptManager` | `SDKGptManager?` | `isAdsEnabled` |
| `adsWizzSDKManager` | `AdsWizzSDKManager?` | |

---

## Models

Package `com.atunwadigital.mediasdk.models`.

### Playback

```kotlin
data class MediaItem(
    val id: String,
    val streamUrl: String,
    val gptUrl: String? = null,            // Ad Manager ad unit for banners
    val imaUrl: String? = null,            // IMA VAST/VMAP tag for prerolls
    val streamChannels: StreamChannels? = null,   // null → audio-only
    val streamType: StreamType,
    val metadata: MediaMetadata? = null,
)
data class MediaMetadata(val title: String, val artistOrShow: String, val artworkUrl: String? = null)
data class StreamChannels(val isAudio: Boolean, val isVideo: Boolean)
enum class StreamType { VOD, LIVE }
enum class SDKState { IDLE, INITIALIZING, LOADING, READY, PLAYING, PAUSED, BUFFERING, ERROR, AD_PLAYING }
```

### Configuration (from the backend; `@Serializable`)

```kotlin
data class PlayerConfig(
    val assetKey: String,          // JSON "apiKey"
    val streamURL: String,         // JSON "url"
    val playerName: String,        // JSON "name"
    val websiteName: String = "",  // JSON "website"
    val logo: String = "",
    val authorisedDomain: AuthorisedDomain? = null,
    val publisher: Publisher? = null,
    val loadingAnimation: String = "",   // Lottie URL
    val adUrl: String = "",        // JSON "imaUrl"
    val gptUrl: String = "",
    val streamChannels: StreamChannels? = null,
    val colors: PlayerColors? = null,
) { val domain: String }           // authorisedDomain?.url ?: ""

data class PlayerColors(val primary: String?, val secondary: String?, val highlight: String?, val text: String?)
data class AuthorisedDomain(val id: String?, val url: String?, val publisherId: String?)
data class Publisher(val name: String?, val id: String?, val tier: PricingTier?, val logo: String?,
                     val website: String?, val newsSources: List<NewsSource> = emptyList())

data class PublisherConfig(
    val publisher: PublisherInfo,
    val players: List<PlayerConfig>,
    val configVersion: String,
    val hasMore: Boolean = false,
    val nextCursor: String? = null,
)
data class PublisherInfo(val id: String, val name: String, val logo: String?, val loadingAnimation: String?,
                         val website: String?, val newsSources: List<NewsSource> = emptyList())
```

Unknown JSON keys are ignored, and invalid values are replaced with defaults.

### Theming

```kotlin
data class SDKThemeConfig(val primaryColorHex: String = "#0057FF", val secondaryColorHex: String? = null,
                          val liveBadgeColorHex: String = "#F44336", val loadingColorHex: String? = null)
data class SDKTheme(val tier: PricingTier, val primaryColorHex: String, val secondaryColorHex: String?,
                    val stationLogoUrl: String?, val liveBadgeColorHex: String, val loadingColorHex: String?)
enum class PricingTier { STANDARD, CUSTOM }
```

### Ads

```kotlin
sealed class AdPlaybackState {
    object Idle; object Requesting; object Loaded; object Buffering; object Playing
    object Paused; object Completed; object Skipped
    data class Error(val throwable: Throwable)
}

data class IMAConfig(
    var language: String = "en",
    var vastLoadTimeoutMs: Int = 4000,
    var mediaLoadTimeoutMs: Int = 5000,
    var enableDebugMode: Boolean = false,
    var maxRedirects: Int = 4,
    var sessionId: String? = null,
    var playerType: String = "atunwa-media-sdk",
    var playerVersion: String = "1.0.0",
    var enableFocusSkipButton: Boolean = true,
    var adAttribution: Boolean = true,
    var adCountDown: Boolean = true,
) {
    fun addFriendlyObstruction(view: View, purpose: Int, detailedReason: String? = null): IMAConfig
}
```

`AtunwaAdEvent` (`Loaded`, `Started`, `Progress(countdownMs)`, `Completed`, `Skipped`,
`ContentResumeRequested`, `Error`) is the SDK's internal ad-event vocabulary. Observe
`adPlaybackState` instead.

### News

```kotlin
data class NewsSource(val id: String, val name: String, val logo: String = "", val feedUrl: String = "")
data class NewsArticle(val title: String, val link: String, val publishedAt: Long,
                       val thumbnailUrl: String?, val sourceName: String, val sourceLogo: String?)
```

---

## UI

Package `com.atunwadigital.mediasdk.ui`.

| Class | Public API |
|---|---|
| `SDKLiveCardView` | `onCardClickListener: (() -> Unit)?`, `previewPlayer: PlayerConfig?` |
| `SDKMiniPlayerView` | `onExpandListener: (() -> Unit)?`, `onCloseClickListener: (() -> Unit)?` |
| `SDKAudioPlayerView` | *(none, fully reactive)* |
| `SDKPlayerActivity` | Launch it through `MediaSDK.launchPlayer` / `loadPlayer` |
| `SDKVideoView` | `attachPlayer(Player)`, `setShowLoader(Boolean)`, `setLoaderTint(Int)`, `setAccentColor(Int)` |
| `SDKBannerAdView` | XML `adUnitId`, `autoLoad`; `loadAd(adUnitId, sizes)`, `refreshAd()`, `clearGptBannerContent()`, `destroy()`, `onContentStateChanged: ((Boolean) -> Unit)?` |
| `SDKCompanionBannerView` | *(none; toggle its visibility)* |
| `SDKWaveformView` | `setBarColor(primary, highlight = primary)`, `startAnimation()`, `stopAnimation()`, `setBuffering(Boolean)`, `updateAmplitude(Float)` |
| `SDKProgressBar` | `setProgress(pos, dur, buffered = 0)`, `setLive(Boolean)`, `setAccentColor(Int)`, `setTextColor(Int)`, `showTimeTexts(Boolean)`, `onSeekListener`, `onDraggingListener` |
| `SDKControlButton` | `setIcon(resId)`, `setIconTint(color)` |
| `SDKControlsView` | `addCenterControl(View)`, `addBottomControl(View)`, `showControls(Boolean)`, `onPlayPauseClick`, `onRewindClick`, `onForwardClick`, `onNextClick`, `onPrevClick` |
| `SDKShakeMeView` | *(placeholder)* |
| `LiveBadgeAnimator(badge: TextView)` | `startPulse()`, `stopPulse()`, `release()` |
| `SDKThemeManager` (object) | `theme: StateFlow<SDKTheme?>` |

---

## Player & ads

| Class | Public API |
|---|---|
| `player.ImaAdProvider` | `createCompanionAdSlot(container, width, height)`, `registerCompanionSlots(ownerId, slots)`, `unregisterCompanionSlots(ownerId)`, `getImaAdsLoader()` |
| `player.AdSequencer(initialWaitTimeoutMs = 12_000)` | `decide(item, adPlaybackState, state): Outcome(decision, itemChanged)`, with `Decision` = `ImaActive` \| `AwaitingPreroll` \| `ShowGpt(gptUrl)` \| `NoGpt` |
| `player.AdProvider` | Interface: `initialize()`, `attachPlayer(Player?)`, `destroy()` |
| `player.CoreMediaService` | The `MediaSessionService`. It's declared in the manifest, and you don't use it directly. |
| `SDKGptManager` | `isAdsEnabled: Boolean` |
| `adswizz.AdsWizzStreamCoordinator` | Used internally by `load()`/`reset()`. Don't call `startStream`/`stop` yourself. |
| `adswizz.AdsWizzPlayerBridge` | The AdsWizz `AdStreamPlayerAdapter`. `CoreMediaService` wires it up. |

## Utilities

| Class | Public API |
|---|---|
| `utils.PiPHelper` | `createPiPParams(width = 16, height = 9): PictureInPictureParams?` (null below API 26) |
| `utils.TimeUtils` | `formatTime(ms): String`, giving `mm:ss` or `h:mm:ss` |
| `telemetry.TelemetryManager` | `startObserving(stateFlow, scope)`, `stopObserving()`, `reportError(String)`. Logs only, in v1.0. |

## REST endpoints used

| Endpoint | Called by |
|---|---|
| `GET {ATUNWA_SDK_BASE_URL}/sdk/v1/player?key={apiKey}` | `preload`, `initialize`, `refreshPlayerConfig` |
| `GET {ATUNWA_SDK_BASE_URL}/sdk/v1/config?key={sdkKey}` | `initializePublisher`, `refreshPublisherConfig` |

Both send `Accept: application/json` and `Accept-Encoding: gzip`, use 15s timeouts, and retry 3
times with exponential backoff (1s, 2s, 4s). Any status other than 200 counts as a failure.
