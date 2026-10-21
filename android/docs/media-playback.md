# Media playback & state

`MediaSDK` is the only playback API you use. Under it, `CoreMediaService` (a Media3
`MediaSessionService`) owns an `ExoPlayer`, or a `CastPlayer` while casting. Every SDK view shares
one `MediaController` connected to that service.

- [Loading media](#loading-media)
- [Playback controls](#playback-controls)
- [The state machine](#the-state-machine)
- [Observable flows](#observable-flows)
- [Offline start & config caching](#offline-start--config-caching)
- [Engine behavior](#engine-behavior)
- [Building a custom player screen](#building-a-custom-player-screen)

---

## Loading media

### The active station's stream

```kotlin
MediaSDK.load()
```

This builds a `MediaItem` from the active `playerConfig` and loads it:

| `MediaItem` field | Comes from |
|---|---|
| `id` | `playerConfig.assetKey` |
| `streamUrl` | `playerConfig.streamURL` |
| `streamChannels` | `playerConfig.streamChannels` |
| `imaUrl` | `playerConfig.adUrl` (blank becomes `null`) |
| `gptUrl` | `playerConfig.gptUrl` (blank becomes `null`) |
| `streamType` | always `StreamType.LIVE` |
| `metadata.title` / `artistOrShow` / `artworkUrl` | `playerName` / `publisher.name`, falling back to `websiteName` / `logo` |

If there's no active `playerConfig`, it logs an error and moves `state` to `ERROR`. The `launch*`,
`loadPlayer` and `activatePlayer` calls all call `load()` for you.

### A custom `MediaItem`

To play your own content, such as a podcast episode or a VOD clip, pass a `MediaItem`:

```kotlin
val episode = MediaItem(
    id = "episode-142",
    streamUrl = "https://cdn.example.com/vod/142/master.m3u8",
    streamType = StreamType.VOD,
    streamChannels = StreamChannels(isAudio = true, isVideo = false),
    metadata = MediaMetadata(
        title = "Morning Show — Ep. 142",
        artistOrShow = "Metro FM",
        artworkUrl = "https://cdn.example.com/art/142.jpg",
    ),
    imaUrl = "https://pubads.g.doubleclick.net/gampad/ads?...",   // optional preroll
    gptUrl = "/1234567/metro_banner",                             // optional banner ad unit
)

MediaSDK.load(episode)                    // uses the autoplay default from initialize()
MediaSDK.load(episode, autoplay = false)  // per-call override
```

`load(mediaItem)` requires `setup()` to have completed, because it checks `isInitialized`. It
doesn't need a `playerConfig`. Before loading, it normalizes the item:

- `hls://…` URLs are rewritten to `https://…`, and the MIME type is forced to HLS. URLs ending in
  `.m3u8` are also marked as HLS.
- A missing `streamChannels` defaults to audio-only (`isAudio = true, isVideo = false`).
- `StreamType.LIVE` items get a 5-second live-edge target offset. They also go through AdsWizz URL
  decoration first, which normally finishes immediately and times out after 5 seconds, falling
  back to the plain URL. See [Advertising → AdsWizz](ad-integration.md#3-adswizz-server-side-insertion-companions).
- Any AdsWizz session from the previous item is torn down.

> `MediaItem` here is `com.atunwadigital.mediasdk.models.MediaItem`, not Media3's class of the
> same name.

## Playback controls

```kotlin
MediaSDK.play()    // resume or start; sets playWhenReady = true
MediaSDK.pause()   // sets playWhenReady = false
MediaSDK.reset()   // stop, clear item + configs + ad state, return to IDLE
```

- If you call `play()` while a live stream is still waiting on AdsWizz URL decoration, the call is
  remembered and applied once the stream is actually set on the player. A `load()` followed
  immediately by `play()` always plays.
- `reset()` also emits `dismissPlayerSignal`, which closes `SDKPlayerActivity` and PiP, and clears
  `isInitialized`. Call `setup()` and an init or launch call again before playing anything else.

The facade has no seek API. Seeking only matters for VOD, and the built-in `SDKVideoView` and
`SDKPlayerActivity` already provide it. For a custom scrubber, use the shared controller:

```kotlin
MediaSDK.mediaController?.seekTo(positionMs)
```

## The state machine

```kotlin
enum class SDKState { IDLE, INITIALIZING, LOADING, READY, PLAYING, PAUSED, BUFFERING, ERROR, AD_PLAYING }
```

| State | Meaning |
|---|---|
| `IDLE` | Nothing loaded. This is the state at startup, after `initialize()`, after `reset()`, and when VOD reaches its end. |
| `INITIALIZING` | `initialize()` is fetching config. |
| `LOADING` | A `MediaItem` is being prepared. |
| `READY` | The player is ready but not playing. |
| `PLAYING` | Content is playing. |
| `PAUSED` | The user or the system paused playback. |
| `BUFFERING` | The player is rebuffering. |
| `AD_PLAYING` | An IMA preroll or midroll is playing. Content is paused underneath it. |
| `ERROR` | Initialization or playback failed. |

The SDK enforces valid transitions. Invalid ones are ignored with a
`Invalid state transition: X → Y` warning in the log:

| From | Allowed next states |
|---|---|
| `IDLE` | `INITIALIZING`, `LOADING`, `ERROR` |
| `INITIALIZING` | `IDLE`, `ERROR` |
| `LOADING` | `READY`, `BUFFERING`, `PLAYING`, `AD_PLAYING`, `ERROR` |
| `READY` | `PLAYING`, `LOADING`, `IDLE`, `ERROR` |
| `PLAYING` | `PAUSED`, `BUFFERING`, `READY`, `AD_PLAYING`, `IDLE`, `ERROR` |
| `PAUSED` | `PLAYING`, `READY`, `BUFFERING`, `IDLE`, `ERROR` |
| `BUFFERING` | `PLAYING`, `READY`, `AD_PLAYING`, `IDLE`, `ERROR` |
| `AD_PLAYING` | `PLAYING`, `PAUSED`, `READY`, `BUFFERING`, `ERROR` |
| `ERROR` | `IDLE`, `INITIALIZING`, `LOADING` |

Typical sequences:

```
Cold start:   IDLE → INITIALIZING → IDLE → LOADING → PLAYING ⇄ BUFFERING
With preroll: … LOADING → AD_PLAYING → PLAYING
Pause:        PLAYING → PAUSED → PLAYING
Error:        … → ERROR → LOADING (retry with load())
```

### Observing state

```kotlin
MediaSDK.state
    .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
    .onEach { state ->
        when (state) {
            SDKState.IDLE -> showPlaceholder()
            SDKState.INITIALIZING, SDKState.LOADING -> showSpinner()
            SDKState.BUFFERING -> showBuffering()
            SDKState.READY, SDKState.PAUSED -> showPlayButton()
            SDKState.PLAYING -> showPauseButton()
            SDKState.AD_PLAYING -> showAdIndicator()
            SDKState.ERROR -> showRetry { MediaSDK.load() }
        }
    }
    .launchIn(lifecycleScope)
```

> **Play/pause icon tip.** To decide which icon to show, use the user's *intent* instead of
> `state == PLAYING` alone, so the icon doesn't flicker during buffering. The built-in views use
> `state == PLAYING || state == AD_PLAYING || MediaSDK.playWhenReady.value`.

## Observable flows

All of these are hot `StateFlow`s or `SharedFlow`s, safe to collect from any lifecycle-aware scope.

| Property | Type | Meaning |
|---|---|---|
| `state` | `StateFlow<SDKState>` | Playback state machine (see above). |
| `playWhenReady` | `StateFlow<Boolean>` | Play/pause intent, independent of buffering. |
| `currentMediaItemFlow` | `StateFlow<MediaItem?>` | The loaded item. `currentMediaItem` gives a snapshot. |
| `adPlaybackState` | `StateFlow<AdPlaybackState>` | IMA ad sub-state. The current version emits `Idle`, `Requesting`, `Loaded`, `Playing`, `Completed` and `Error(throwable)`. `Buffering`, `Paused` and `Skipped` are declared but not emitted yet, and a skipped ad reports `Completed`. |
| `adCountdown` | `StateFlow<Int>` | Seconds remaining in the current IMA ad. |
| `hasCompanionAd` | `StateFlow<Boolean>` | A companion creative should be shown. Both an IMA preroll's VAST companion and an AdsWizz SSI break can set this. |
| `companionAdUrl` | `StateFlow<String?>` | Reserved. Nothing in the current version sets it, so it's always `null`. |
| `isDedicatedPlayerActive` | `StateFlow<Boolean>` | `SDKPlayerActivity` exists, in full screen or PiP. |
| `dismissPlayerSignal` | `SharedFlow<Unit>` | Fires when another station takes over, or on `reset()`. Close any floating UI you built yourself. |
| `amplitude` | `SharedFlow<Float>` | Real-time RMS amplitude of the output, from 0 to 1. Needs `RECORD_AUDIO` (see [Getting started](getting-started.md#about-record_audio)). |

```kotlin
// Example: show "Now playing" metadata
MediaSDK.currentMediaItemFlow
    .onEach { item ->
        title.text = item?.metadata?.title.orEmpty()
        subtitle.text = item?.metadata?.artistOrShow.orEmpty()
    }
    .launchIn(lifecycleScope)

// Example: an ad countdown chip
combine(MediaSDK.adPlaybackState, MediaSDK.adCountdown) { ad, secs -> ad to secs }
    .onEach { (ad, secs) ->
        adChip.isVisible = ad is AdPlaybackState.Playing
        adChip.text = "Ad · ${secs}s"
    }
    .launchIn(lifecycleScope)
```

Snapshot (non-flow) properties: `isInitialized`, `playerConfig`, `publisherConfig`,
`currentMediaItem`, `mediaController`.

## Offline start & config caching

The SDK caches config in two places:

1. **In memory**, per API key or SDK key, for the life of the process. `initialize`,
   `initializePublisher` and `preload` reuse it without a network call.
2. **On disk** (SharedPreferences `atunwa_mediasdk_station_cache`). `initialize` and
   `initializePublisher` write the config there after every successful fetch.

If a fetch fails after its retries (3 retries with 1s, 2s and 4s backoff, and 15s connect and read
timeouts), `initialize` and `initializePublisher` fall back to the persisted copy and log
`…fetch failed, using persisted config…`. The app can then show the last-known stations while it's
offline. The stream itself still needs a network connection.

`refreshPlayerConfig` and `refreshPublisherConfig` never fall back. See
[Integration modes → Pull-to-refresh](integration-modes.md#pull-to-refresh).

## Engine behavior

You don't need to configure any of this. It's documented so you know what to expect.

| Behavior | Detail |
|---|---|
| **Background playback** | `CoreMediaService` runs as a `mediaPlayback` foreground service with a system media notification. Lock-screen, notification and Bluetooth media controls work through the `MediaSession`. |
| **Audio focus** | Handled by ExoPlayer (`handleAudioFocus = true`). Playback ducks or pauses for calls and other apps, and resumes afterwards. AdsWizz's own focus handling is disabled so the two don't conflict. |
| **Buffering** | 30–90s buffer, 3.5s to start, 8s after a rebuffer, 10s back buffer. |
| **Network resilience** | 15s connect and read timeouts, at least 6 load retries with the delay capped at 8s, and cross-protocol redirects allowed. User agent: `AtunwaMediaSDK/1.0`. |
| **Live edge** | Targets 5s behind live, with a playback speed of 0.96–1.04× to hold it without visible jumps. |
| **Google Cast** | When a Cast session starts, playback moves to a `CastPlayer`, keeping the current item, position and play state. It moves back when the session ends. `SDKPlayerActivity` shows a media route button. |
| **Consent (TCF)** | If `IABTCF_TCString` is set in the default SharedPreferences (`<package>_preferences`, as IAB requires), `&tcf=<string>` is appended to every stream request. |
| **Telemetry** | `TelemetryManager` can log time-to-first-frame and play duration under the `MediaSDK_Telemetry` tag. In v1.0 it only logs locally. The SDK doesn't start it itself; to turn it on, call `TelemetryManager.startObserving(MediaSDK.state, scope)`. |

## Building a custom player screen

If the built-in `SDKPlayerActivity` doesn't fit your design, build your own screen on the same
public surface:

```kotlin
class MyPlayerActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_player)

        // Video surface with transport controls, bound to the shared controller
        val videoView = findViewById<SDKVideoView>(R.id.videoView)
        MediaSDK.attachVideoView(videoView)
        // …or: container.addView(MediaSDK.createVideoView(this))

        playPause.setOnClickListener {
            if (MediaSDK.playWhenReady.value) MediaSDK.pause() else MediaSDK.play()
        }

        MediaSDK.state.onEach(::render).launchIn(lifecycleScope)
        MediaSDK.dismissPlayerSignal.onEach { finish() }.launchIn(lifecycleScope)
    }
}
```

Rules to follow:

- **Never create your own ExoPlayer** for SDK content. Always attach views to the shared
  controller.
- **Only one surface shows video at a time.** If your screen hosts video while `SDKLiveCardView`
  is also on screen, the card hands over automatically only to `SDKPlayerActivity`, which is the
  screen tracked by `isDedicatedPlayerActive`. Avoid showing two video surfaces at once.
- For PiP, `PiPHelper.createPiPParams(16, 9)` returns ready-made `PictureInPictureParams` with
  auto-enter and seamless resize on API 31+.
